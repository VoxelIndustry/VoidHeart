package net.voxelindustry.voidheart.common.energy.fissure;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.VoidWounds;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Every loaded fissure and wound, by level, and what they do beyond their own block: the real {@link VoidSeep}, the corruption modifier and slow rise
 * inside a seep, and {@link VoidWounds#open}. A seep only acts while its crack is loaded.
 */
public final class VoidFissures
{
    private static final int RISE_TICKS = 20;
    // A wound opened by command looks this far below the spot asked for the ground to lie on
    private static final int WOUND_DROP = 8;

    private static final Map<ServerLevel, Set<VoidFissureTile>> LOADED = new WeakHashMap<>();
    /**
     * Fractions of a point of corruption each player has gathered from the seep's own rise, before it adds up to one.
     */
    private static final Map<UUID, Float> RISE_CARRY = new HashMap<>();

    private VoidFissures()
    {
    }

    static void install()
    {
        VoidSeep.install(VoidFissures::strengthAt);
        VoidWounds.install(VoidFissures::openWound);
        Corruption.addModifier(player -> player.level() instanceof ServerLevel level
                ? SeepMath.corruptionMultiplier(strengthAt(level, player.getX(), player.getY(), player.getZ()))
                : 1);
        ServerTickEvents.END_SERVER_TICK.register(VoidFissures::riseTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            LOADED.clear();
            RISE_CARRY.clear();
        });
    }

    static void add(ServerLevel level, VoidFissureTile tile)
    {
        LOADED.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(tile);
    }

    static void remove(ServerLevel level, VoidFissureTile tile)
    {
        var tiles = LOADED.get(level);
        if (tiles != null)
            tiles.remove(tile);
    }

    public static Collection<VoidFissureTile> loaded(ServerLevel level)
    {
        var tiles = LOADED.get(level);
        return tiles == null ? List.of() : List.copyOf(tiles);
    }

    public static float strengthAt(ServerLevel level, BlockPos pos)
    {
        return strengthAt(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static float strengthAt(ServerLevel level, double x, double y, double z)
    {
        var tiles = LOADED.get(level);
        if (tiles == null || tiles.isEmpty())
            return 0;
        long now = level.getGameTime();
        float strength = 0;
        for (var tile : tiles)
        {
            if (!tile.isRemoved())
                strength = SeepMath.combine(strength, tile.seepStrength(x, y, z, now));
        }
        return strength;
    }

    /**
     * Opens a wound where something failed, exactly at {@code pos} and replacing whatever stands there, so a failed reactor's wound takes the
     * core's place inside its shell.
     *
     * @param size the width it seeps at, 1 to 5; anything else is clamped, and a reactor's wound is {@link VoidFissureBalance#WOUND_SIZE}
     */
    public static void openWound(ServerLevel level, BlockPos pos, float size)
    {
        var axis = Math.floorMod(pos.hashCode(), 2) == 0 ? Direction.Axis.X : Direction.Axis.Z;
        level.setBlock(pos, VoidFissureContent.VOID_WOUND.defaultBlockState().setValue(VoidFissureBlock.AXIS, axis), 3);
        if (level.getBlockEntity(pos) instanceof VoidFissureTile wound)
            wound.open(level, size, level.getGameTime());
    }

    /**
     * Where a wound asked for at {@code pos} by command lies: there, or on the ground below it if it hangs over air.
     */
    static BlockPos woundSpot(ServerLevel level, BlockPos pos)
    {
        var at = pos;
        for (int i = 0; i < WOUND_DROP; i++)
        {
            var below = at.below();
            BlockState ground = level.getBlockState(below);
            if (ground.isFaceSturdy(level, below, Direction.UP) || !ground.canBeReplaced())
                break;
            at = below;
        }
        return at;
    }

    /**
     * Once a second, each player inside a seep gathers its slow rise of corruption, and every whole point goes through {@link Corruption#raise}, so
     * wards and the seep's own boost apply to it.
     */
    private static void riseTick(MinecraftServer server)
    {
        if (server.getTickCount() % RISE_TICKS != 0)
            return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            gatherRise(player, 1);
    }

    /**
     * What standing where the player is for this many seconds adds to their corruption. Called every second, and by tests to skip the slow wait.
     */
    public static void gatherRise(ServerPlayer player, float seconds)
    {
        if (player.isSpectator() || !(player.level() instanceof ServerLevel level))
            return;
        float strength = strengthAt(level, player.getX(), player.getY(), player.getZ());
        if (strength <= 0)
            return;

        float carry = RISE_CARRY.getOrDefault(player.getUUID(), 0F) + SeepMath.risePerSecond(strength) * seconds;
        int points = (int) carry;
        if (points > 0)
        {
            Corruption.raise(player, points);
            carry -= points;
        }
        RISE_CARRY.put(player.getUUID(), carry);
    }
}
