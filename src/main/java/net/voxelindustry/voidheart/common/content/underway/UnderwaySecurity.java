package net.voxelindustry.voidheart.common.content.underway;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.common.content.underway.security.UnderwayUnitSpawner;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayMaterial;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRoomKind;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRuin;
import net.voxelindustry.voidheart.common.world.underway.UnderwaySection;
import net.voxelindustry.voidheart.common.world.underway.UnderwayStroke;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The state of every section of the Underway that something happened to, and what that state does to the world: lamps, cabinets, klaxons, doors, and the word given to whoever stands there.
 * <p>
 * Lamps tell a section's state at a glance: steady red while it only watches, strobing red while its alarm sounds, warm once it has power, and dark once nothing is left to guard it. While an alarm sounds in a section with power, its armoured doors are held shut.
 * <p>
 * A sealed fort never lost its power, so its sections are live and wide awake with no cell.
 */
public class UnderwaySecurity extends SavedData
{
    /** A section of the fort under a district. */
    public record Key(int x, int z, UnderwaySection section)
    {
        /** @return the section holding a block of the Daedalus, or null above the Underway or between two districts */
        public static @Nullable Key at(int blockX, int blockY, int blockZ)
        {
            if (blockY > UnderwayLevels.TOP_Y || !DistrictPos.isInsideDistrict(blockX, blockZ))
                return null;
            var district = DistrictPos.at(blockX, blockZ);
            return new Key(district.x(), district.z(), UnderwaySection.at(blockY));
        }

        public static @Nullable Key at(BlockPos pos)
        {
            return at(pos.getX(), pos.getY(), pos.getZ());
        }

        public DistrictPos district()
        {
            return new DistrictPos(x, z);
        }
    }

    private record Entry(int x, int z, int section, boolean cell, long charge, int wake, int alarm, List<Integer> destroyed)
    {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Codec.INT.fieldOf("section").forGetter(Entry::section),
                Codec.BOOL.fieldOf("cell").forGetter(Entry::cell),
                Codec.LONG.fieldOf("charge").forGetter(Entry::charge),
                Codec.INT.fieldOf("wake").forGetter(Entry::wake),
                Codec.INT.fieldOf("alarm").forGetter(Entry::alarm),
                Codec.INT.listOf().fieldOf("destroyed").forGetter(Entry::destroyed)
        ).apply(instance, Entry::new));
    }

    /** Whoever a searchlight caught, known to the whole section until the game time given. Not saved: a restart loses sight of them. */
    private record Spotted(UUID player, long until)
    {
    }

    public static final Codec<UnderwaySecurity> CODEC = Entry.CODEC.listOf().fieldOf("sections").codec().xmap(UnderwaySecurity::new, UnderwaySecurity::entries);

    public static final SavedDataType<UnderwaySecurity> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(MODID, "underway_security"), UnderwaySecurity::new, CODEC, null);

    private static final SectionState UNTOUCHED = new SectionState();
    private static final int SECTIONS = UnderwaySection.values().length;
    /** How far the word of a section's state carries past its district. */
    private static final int HEARD = 8;
    private static final UnderwayProp[] CEILING_LAMPS = { UnderwayProp.EMERGENCY_LAMP, UnderwayProp.EMERGENCY_LAMP_ALARM, UnderwayProp.EMERGENCY_LAMP_DARK, UnderwayProp.CEILING_LAMP_LIT };
    private static final UnderwayProp[] WALL_LAMPS = { UnderwayProp.EMERGENCY_WALL_LAMP, UnderwayProp.EMERGENCY_WALL_LAMP_ALARM, UnderwayProp.WALL_LAMP, UnderwayProp.WALL_LAMP_LIT };
    private static final UnderwayProp[] CABINETS = { UnderwayProp.BREAKER_CABINET, UnderwayProp.BREAKER_CABINET_LIVE };

    private final Map<Key, SectionState> sections = new HashMap<>();
    private final Map<Key, Spotted> spotted = new HashMap<>();
    private final Queue<ChunkPos> loaded = new ArrayDeque<>();

    public UnderwaySecurity()
    {
    }

    private UnderwaySecurity(List<Entry> entries)
    {
        for (var entry : entries)
        {
            if (entry.section() < 0 || entry.section() >= SECTIONS)
                continue;
            sections.put(new Key(entry.x(), entry.z(), UnderwaySection.of(entry.section())),
                    new SectionState(entry.cell(), entry.charge(), SectionState.Wake.of(entry.wake()), entry.alarm(), entry.destroyed()));
        }
    }

    private List<Entry> entries()
    {
        return sections.entrySet().stream().filter(entry -> !entry.getValue().isBlank()).map(entry ->
        {
            var key = entry.getKey();
            var state = entry.getValue();
            return new Entry(key.x(), key.z(), key.section().ordinal(), state.hasCell(), state.charge(), state.wake().ordinal(), state.alarm(), state.destroyed());
        }).toList();
    }

    /** @return the security of the Daedalus, or null in any other dimension */
    public static @Nullable UnderwaySecurity of(ServerLevel level)
    {
        return level.dimension() == VoidDaedalus.WORLD_KEY ? level.getDataStorage().computeIfAbsent(TYPE) : null;
    }

    public static void tickLevel(ServerLevel level)
    {
        var security = of(level);
        if (security != null)
            security.tick(level);
    }

    public static void chunkLoaded(ServerLevel level, ChunkPos chunk)
    {
        var security = of(level);
        // Blocks are set a tick later, once the chunk is fully part of the level
        if (security != null)
            security.loaded.add(chunk);
    }

    private static UnderwayFort fort(ServerLevel level, Key key)
    {
        return UnderwayPlans.fort(level.getSeed(), key.district());
    }

    private SectionState peek(Key key)
    {
        return sections.getOrDefault(key, UNTOUCHED);
    }

    private SectionState touch(Key key)
    {
        setDirty();
        return sections.computeIfAbsent(key, missing -> new SectionState());
    }

    public boolean hasCell(Key key)
    {
        return peek(key).hasCell();
    }

    public long charge(Key key)
    {
        return peek(key).charge();
    }

    public boolean isLive(ServerLevel level, Key key)
    {
        return peek(key).isLive() || fort(level, key).ruin() == UnderwayRuin.SEALED;
    }

    public boolean isAlarmed(Key key)
    {
        return peek(key).isAlarmed();
    }

    /** @return whether the section's armoured doors are held shut: it has power and its alarm sounds */
    public boolean isLockedDown(ServerLevel level, Key key)
    {
        return peek(key).isAlarmed() && isLive(level, key);
    }

    /** @return whether a unit of that section is awake, given whether it runs on the emergency circuit */
    public boolean isAwake(ServerLevel level, Key key, boolean emergency)
    {
        return peek(key).isAwake(emergency) || fort(level, key).ruin() == UnderwayRuin.SEALED;
    }

    public boolean isSecured(ServerLevel level, Key key)
    {
        return peek(key).isSecured(fort(level, key).unitCount(key.section()));
    }

    public int destroyedCount(Key key)
    {
        return peek(key).destroyed().size();
    }

    public boolean isDestroyed(Key key, int unit)
    {
        return peek(key).destroyed().contains(unit);
    }

    /** @return the section's units that are wrecks, by their place in the fort's list */
    public List<Integer> wrecks(Key key)
    {
        return peek(key).destroyed();
    }

    /** Sets a cell in the section's cabinet, which wakes all of its security and locks it down if the cell holds any charge. */
    public void insertCell(ServerLevel level, Key key, long charge)
    {
        var state = touch(key);
        boolean wasLive = state.isLive();
        state.insert(charge);
        boolean woke = state.isLive() && !wasLive;
        boolean secured = woke && isSecured(level, key);
        if (secured)
            state.standDown();
        refresh(level, key, null);
        if (!woke)
            return;
        if (!secured)
            shutDoors(level, key, null, 0);
        tell(level, key, secured ? "live_secured" : "live", SoundEvents.BEACON_ACTIVATE, 0.7F);
    }

    /** @return the charge left in the cell taken out of the section's cabinet */
    public long removeCell(ServerLevel level, Key key)
    {
        var state = touch(key);
        boolean wasLive = state.isLive();
        long left = state.remove();
        refresh(level, key, null);
        if (wasLive)
            tell(level, key, "dark", SoundEvents.BEACON_DEACTIVATE, 0.7F);
        return left;
    }

    /** Sounds a section's alarm, or keeps it sounding. It wakes the emergency circuit, and locks down a section with power. Nothing answers in a section already secured. */
    public void raiseAlarm(ServerLevel level, Key key)
    {
        if (isSecured(level, key))
            return;
        var state = touch(key);
        boolean wasAlarmed = state.isAlarmed();
        state.raiseAlarm();
        if (wasAlarmed)
            return;
        refresh(level, key, null);
        boolean locked = isLive(level, key);
        if (locked)
            shutDoors(level, key, null, 0);
        tell(level, key, locked ? "lockdown" : "alarm", SoundEvents.BELL_RESONATE, 1.6F);
    }

    /** Answers a broken tripwire: the grating of a pit close by drops, the doors around shut, and the alarm sounds. */
    public void trip(ServerLevel level, Key key, BlockPos wire)
    {
        releasePit(level, key, wire);
        shutDoors(level, key, wire, SecurityBalance.TRIPWIRE_DOOR_REACH);
        raiseAlarm(level, key);
    }

    /** Tells the whole section where someone is, for a while. */
    public void spot(ServerLevel level, Key key, Player player)
    {
        spotted.put(key, new Spotted(player.getUUID(), level.getGameTime() + SecurityBalance.SPOTTED_TICKS));
        raiseAlarm(level, key);
    }

    /** @return whoever a searchlight of the section caught lately, if they can still be hunted */
    public @Nullable Player spotted(ServerLevel level, Key key)
    {
        var seen = spotted.get(key);
        // Nothing hunts in peaceful, whoever was caught
        if (seen == null || level.getDifficulty() == Difficulty.PEACEFUL)
            return null;
        if (level.getGameTime() > seen.until())
        {
            spotted.remove(key);
            return null;
        }
        var player = level.getPlayerByUUID(seen.player());
        return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator() ? player : null;
    }

    /** Records a unit as a wreck, for good unless a fitter rebuilds it. */
    public void destroyed(ServerLevel level, Key key, int unit)
    {
        var state = touch(key);
        if (!state.destroy(unit) || !state.isSecured(fort(level, key).unitCount(key.section())))
            return;
        state.standDown();
        refresh(level, key, null);
        tell(level, key, "secured", SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F);
    }

    /** Puts a wreck back to work, as a fitter does: the unit stands again where it was posted. */
    public boolean rebuild(ServerLevel level, Key key, int unit)
    {
        var state = sections.get(key);
        if (state == null || !state.restore(unit))
            return false;
        setDirty();
        UnderwayUnitSpawner.post(level, key.district(), unit);
        return true;
    }

    /** Forgets everything that happened to a section, for review. Its wrecks stay wrecks in the world. */
    public void reset(ServerLevel level, Key key)
    {
        // What is loaded turns back now, and the rest as its chunks load
        sections.remove(key);
        spotted.remove(key);
        setDirty();
        refresh(level, key, null);
    }

    private void tick(ServerLevel level)
    {
        // A chunk that loads is brought to what is saved for its sections, even when nothing is: a section whose state was
        // forgotten or lost while the chunk was away must not keep showing it
        for (var chunk = loaded.poll(); chunk != null; chunk = loaded.poll())
        {
            DistrictPos last = null;
            for (int corner = 0; corner < 4; corner++)
            {
                int x = (corner & 1) == 0 ? chunk.getMinBlockX() : chunk.getMaxBlockX();
                int z = (corner & 2) == 0 ? chunk.getMinBlockZ() : chunk.getMaxBlockZ();
                if (!DistrictPos.isInsideDistrict(x, z) || DistrictPos.at(x, z).equals(last))
                    continue;
                last = DistrictPos.at(x, z);
                for (var section : UnderwaySection.values())
                    refresh(level, new Key(last.x(), last.z(), section), chunk);
            }
        }
        long time = level.getGameTime();
        for (var entry : sections.entrySet())
        {
            var state = entry.getValue();
            if (state.isAlarmed())
            {
                state.tick();
                setDirty();
                if (!state.isAlarmed())
                {
                    refresh(level, entry.getKey(), null);
                    tell(level, entry.getKey(), "calm", SoundEvents.BELL_BLOCK, 0.5F);
                }
                else if (time % SecurityBalance.KLAXON_EVERY == 0)
                    klaxon(level, entry.getKey(), time / SecurityBalance.KLAXON_EVERY % 2 == 0);
            }
            if (!state.isLive())
                continue;
            setDirty();
            if (state.drain())
            {
                refresh(level, entry.getKey(), null);
                tell(level, entry.getKey(), "spent", SoundEvents.BEACON_DEACTIVATE, 0.5F);
            }
        }
    }

    /** Sounds one of the two notes of the alarm from every klaxon of the section, or from its cabinet when it has none. */
    private void klaxon(ServerLevel level, Key key, boolean high)
    {
        var fort = fort(level, key);
        var frame = fort.frame();
        float pitch = high ? 0.84F : 0.63F;
        boolean sounded = false;
        for (var spot : fort.props())
        {
            if (spot.prop() != UnderwayProp.KLAXON || UnderwaySection.at(spot.y()) != key.section())
                continue;
            var pos = new BlockPos(frame.x(spot.along(), spot.across()), spot.y(), frame.z(spot.along(), spot.across()));
            if (level.isLoaded(pos))
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.5F, pitch);
            sounded = true;
        }
        var cabinet = fort.cabinet(key.section());
        if (!sounded && cabinet != null)
            level.playSound(null, new BlockPos(frame.x(cabinet.along(), cabinet.across()), cabinet.y(), frame.z(cabinet.along(), cabinet.across())), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.5F, pitch);
    }

    /** Turns a section's lamps and its cabinet to what its state makes them, wherever they are loaded. */
    private void refresh(ServerLevel level, Key key, @Nullable ChunkPos only)
    {
        var fort = fort(level, key);
        if (fort.ruin() == UnderwayRuin.SEALED)
            return;
        var state = peek(key);
        boolean live = state.isLive();
        // A level that was built with nothing to guard it keeps its red lamps: dark means something was cleared
        int units = fort.unitCount(key.section());
        boolean secured = units > 0 && state.isSecured(units);
        var ceiling = state.isAlarmed() ? UnderwayProp.EMERGENCY_LAMP_ALARM : live ? UnderwayProp.CEILING_LAMP_LIT : secured ? UnderwayProp.EMERGENCY_LAMP_DARK : UnderwayProp.EMERGENCY_LAMP;
        var wall = state.isAlarmed() ? UnderwayProp.EMERGENCY_WALL_LAMP_ALARM : live ? UnderwayProp.WALL_LAMP_LIT : secured ? UnderwayProp.WALL_LAMP : UnderwayProp.EMERGENCY_WALL_LAMP;
        var frame = fort.frame();
        for (var spot : fort.props())
        {
            if (UnderwaySection.at(spot.y()) != key.section())
                continue;
            var pos = new BlockPos(frame.x(spot.along(), spot.across()), spot.y(), frame.z(spot.along(), spot.across()));
            if (spot.prop() == UnderwayProp.BREAKER_CABINET)
                turn(level, pos, CABINETS, live ? UnderwayProp.BREAKER_CABINET_LIVE : UnderwayProp.BREAKER_CABINET, only);
            else if (spot.prop() == UnderwayProp.EMERGENCY_WALL_LAMP)
                turn(level, pos, WALL_LAMPS, wall, only);
        }
        for (var stroke : fort.strokes())
        {
            if (stroke.material() != UnderwayMaterial.EMERGENCY_LAMP || UnderwaySection.at(stroke.y0()) != key.section())
                continue;
            for (int along = stroke.a0(); along <= stroke.a1(); along++)
            {
                for (int across = stroke.c0(); across <= stroke.c1(); across++)
                    turn(level, new BlockPos(frame.x(along, across), stroke.y0(), frame.z(along, across)), CEILING_LAMPS, ceiling, only);
            }
        }
    }

    /** Swaps a prop for another of its family, facing the same way, unless something else took its place. */
    private static void turn(ServerLevel level, BlockPos pos, UnderwayProp[] family, UnderwayProp wanted, @Nullable ChunkPos only)
    {
        if (only != null && (pos.getX() >> 4 != only.x() || pos.getZ() >> 4 != only.z()) || !level.isLoaded(pos))
            return;
        var state = level.getBlockState(pos);
        if (state.is(VoidHeartBlocks.underwayProp(wanted)))
            return;
        for (var member : family)
        {
            if (!state.is(VoidHeartBlocks.underwayProp(member)))
                continue;
            level.setBlock(pos, VoidHeartBlocks.underwayProp(wanted).defaultBlockState().setValue(UnderwayPropBlock.FACING, state.getValue(UnderwayPropBlock.FACING)), Block.UPDATE_CLIENTS);
            return;
        }
    }

    /**
     * Shuts the section's armoured doors that stand open.
     *
     * @param near  the only place around which doors shut, or null for all of them
     * @param reach how far from that place
     */
    private void shutDoors(ServerLevel level, Key key, @Nullable BlockPos near, int reach)
    {
        var fort = fort(level, key);
        var frame = fort.frame();
        for (var stroke : fort.strokes())
        {
            if (stroke.material() != UnderwayMaterial.DOOR || UnderwaySection.at(stroke.y0()) != key.section())
                continue;
            for (int along = stroke.a0(); along <= stroke.a1(); along++)
            {
                for (int across = stroke.c0(); across <= stroke.c1(); across++)
                {
                    var pos = new BlockPos(frame.x(along, across), stroke.y0(), frame.z(along, across));
                    if (near != null && !pos.closerThan(near, reach) || !level.isLoaded(pos))
                        continue;
                    var state = level.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock door && door.isOpen(state))
                        door.setOpen(null, level, state, pos, false);
                }
            }
        }
    }

    /** Drops the grating over the pit nearest to a place, if one lies close by. What stood on it falls. */
    private void releasePit(ServerLevel level, Key key, BlockPos near)
    {
        var fort = fort(level, key);
        var frame = fort.frame();
        UnderwayStroke nearest = null;
        double reach = SecurityBalance.TRIPWIRE_PIT_REACH * SecurityBalance.TRIPWIRE_PIT_REACH;
        for (var stroke : fort.strokes())
        {
            if (stroke.material() != UnderwayMaterial.GRATING || UnderwaySection.at(stroke.y0()) != key.section() || !isOverPit(fort, stroke))
                continue;
            double x = (frame.x(stroke.a0(), stroke.c0()) + frame.x(stroke.a1(), stroke.c1())) / 2.0 + 0.5;
            double z = (frame.z(stroke.a0(), stroke.c0()) + frame.z(stroke.a1(), stroke.c1())) / 2.0 + 0.5;
            double distance = near.distToCenterSqr(x, stroke.y0() + 0.5, z);
            if (distance < reach)
            {
                reach = distance;
                nearest = stroke;
            }
        }
        if (nearest == null)
            return;
        boolean dropped = false;
        for (int along = nearest.a0(); along <= nearest.a1(); along++)
        {
            for (int across = nearest.c0(); across <= nearest.c1(); across++)
            {
                var pos = new BlockPos(frame.x(along, across), nearest.y0(), frame.z(along, across));
                if (level.isLoaded(pos) && level.getBlockState(pos).is(VoidHeartBlocks.UNDERWAY_GRATING))
                    dropped |= level.destroyBlock(pos, false);
            }
        }
        if (dropped)
            level.playSound(null, near, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.5F);
    }

    private static boolean isOverPit(UnderwayFort fort, UnderwayStroke grating)
    {
        for (var room : fort.rooms())
        {
            if (room.kind() == UnderwayRoomKind.PIT && grating.a0() >= room.a0() && grating.a1() <= room.a1() && grating.c0() >= room.c0() && grating.c1() <= room.c1())
                return true;
        }
        return false;
    }

    /** Gives everyone standing in a section the word of what just happened to it, with a sound from its cabinet. */
    private void tell(ServerLevel level, Key key, String what, SoundEvent sound, float pitch)
    {
        var fort = fort(level, key);
        var cabinet = fort.cabinet(key.section());
        if (cabinet != null)
            level.playSound(null, new BlockPos(fort.frame().x(cabinet.along(), cabinet.across()), cabinet.y(), fort.frame().z(cabinet.along(), cabinet.across())), sound, SoundSource.BLOCKS, 3.0F, pitch);
        var origin = key.district();
        for (var player : level.players())
        {
            if (player.getX() >= origin.originX() - HEARD && player.getX() < origin.originX() + DistrictPos.SIZE + HEARD
                    && player.getZ() >= origin.originZ() - HEARD && player.getZ() < origin.originZ() + DistrictPos.SIZE + HEARD
                    && player.getBlockY() <= UnderwayLevels.TOP_Y && UnderwaySection.at(player.getBlockY()) == key.section())
                player.sendOverlayMessage(Component.translatable("voidheart.underway_section." + what));
        }
    }
}
