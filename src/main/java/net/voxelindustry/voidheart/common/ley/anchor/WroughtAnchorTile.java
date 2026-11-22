package net.voxelindustry.voidheart.common.ley.anchor;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Holds which wrought anchor this block is, and gathers the void energy of a trip leaving from it. A gathering isn't saved: unloaded, it is lost.
 */
public class WroughtAnchorTile extends TileBase
{
    private static final int CHECK_TICKS = 20;
    private static final int PROGRESS_MARKS = 24;

    @Getter
    private long   anchorId;
    @Getter
    private String name = "";

    private @Nullable Gathering gathering;

    public WroughtAnchorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.WROUGHT_ANCHOR, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WroughtAnchorTile tile)
    {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
            return;

        var anchors = OldAnchors.get(serverLevel);
        if (anchors.isEmpty())
            return;
        if (tile.anchorId == 0 || level.getGameTime() % CHECK_TICKS == Math.floorMod(pos.hashCode(), CHECK_TICKS))
            tile.checkRecord(anchors.get().wrought());
        if (tile.gathering != null)
            tile.gather(anchors.get());
    }

    /**
     * Makes sure the level knows this anchor where it stands: one just built, copied or missing from the record gets a new entry.
     */
    private void checkRecord(WroughtAnchors wrought)
    {
        var known = anchorId == 0 ? null : wrought.get(anchorId).orElse(null);
        if (known != null && known.pos().equals(getBlockPos()))
            return;

        var anchor = wrought.register(name, getBlockPos());
        anchorId = anchor.id();
        name = anchor.name();
        setChanged();
        sync();
    }

    public void setName(String name)
    {
        this.name = name;
        setChanged();
        sync();
    }

    public boolean isGathering()
    {
        return gathering != null;
    }

    public void startGathering(ServerPlayer player, long destination, String destinationName, long cost)
    {
        gathering = new Gathering(player.getUUID(), destination, destinationName, cost);
        getLevel().playSound(null, getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.7F);
        player.sendOverlayMessage(progress(gathering));
    }

    private void gather(OldAnchors anchors)
    {
        var level = (ServerLevel) getLevel();
        var current = gathering;
        var player = level.getServer().getPlayerList().getPlayer(current.traveller);
        var wrought = anchors.wrought().get(anchorId);
        if (player == null || !player.isAlive() || wrought.isEmpty() || !anchors.wrought().inReach(player, wrought.get()))
        {
            if (player != null)
                player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.left"));
            stop(level);
            return;
        }

        long got = VoidEnergy.draw(level, getBlockPos(), Math.min(WroughtAnchorBalance.INTAKE, current.cost - current.gathered));
        current.gathered += got;
        current.starved = got > 0 ? 0 : current.starved + 1;
        current.ticks++;

        if (current.starved >= WroughtAnchorBalance.STARVED_TICKS)
        {
            player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.fizzled"));
            stop(level);
            return;
        }

        float progress = current.progress();
        effects(level, progress, current.ticks);
        if (current.gathered >= current.cost)
        {
            gathering = null;
            AnchorTravel.arrive(player, anchors, getBlockPos(), current.destination);
            return;
        }
        if (current.ticks % WroughtAnchorBalance.PROGRESS_TICKS == 0)
            player.sendOverlayMessage(progress(current));
    }

    private void stop(ServerLevel level)
    {
        gathering = null;
        level.playSound(null, getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /**
     * Void drawn in from around the anchor toward its crystal, thicker as the trip nears, under a hum rising in pitch.
     */
    private void effects(ServerLevel level, float progress, int ticks)
    {
        var crystal = Vec3.atBottomCenterOf(getBlockPos()).add(0, 1.2, 0);
        if (ticks % 2 == 0)
        {
            int count = 1 + (int) (progress * 6);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, crystal.x, crystal.y, crystal.z, count, 0.9, 0.9, 0.9, 0.02);
        }
        if (ticks % 20 == 1)
            level.playSound(null, getBlockPos(), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.9F, 0.7F + progress);
    }

    private static Component progress(Gathering gathering)
    {
        int filled = Math.round(gathering.progress() * PROGRESS_MARKS);
        var bar = Component.literal("|".repeat(filled)).withColor(0xC9A6F2)
                .append(Component.literal("|".repeat(PROGRESS_MARKS - filled)).withColor(0x4A3D55));
        return Component.translatable("voidheart.wrought_anchor.gathering", gathering.destinationName, bar);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (anchorId != 0 && getLevel() instanceof ServerLevel serverLevel)
            OldAnchors.get(serverLevel).ifPresent(anchors -> anchors.wrought().unregisterAt(anchorId, pos));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        var customName = components.get(DataComponents.CUSTOM_NAME);
        if (customName != null)
            name = WroughtAnchorBalance.cleanName(customName.getString());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (!name.isEmpty())
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output)
    {
        output.discard("name");
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        anchorId = tag.getLongOr("anchor", 0);
        name = tag.getStringOr("name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        tag.putLong("anchor", anchorId);
        tag.putString("name", name);
    }

    private static final class Gathering
    {
        private final UUID   traveller;
        private final long   destination;
        private final String destinationName;
        private final long   cost;
        private long gathered;
        private int  starved;
        private int  ticks;

        private Gathering(UUID traveller, long destination, String destinationName, long cost)
        {
            this.traveller = traveller;
            this.destination = destination;
            this.destinationName = destinationName;
            this.cost = cost;
        }

        private float progress()
        {
            return Math.min(1, gathered / (float) cost);
        }
    }
}
