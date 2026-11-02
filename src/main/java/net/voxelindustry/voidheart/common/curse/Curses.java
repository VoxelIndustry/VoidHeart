package net.voxelindustry.voidheart.common.curse;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides when a corrupted player is struck, and keeps track of the curse running on each player.
 * Only one runs on a player at a time, since most of them take over the same sky.
 */
public final class Curses
{
    private static final int   ROLL_INTERVAL_TICKS  = 30 * 20;
    private static final float STRIKE_CHANCE_AT_MAX = 0.15F;
    private static final int   COOLDOWN_TICKS       = 5 * 60 * 20;

    private static final Map<UUID, Running> RUNNING        = new HashMap<>();
    private static final Map<UUID, Long>    NEXT_STRIKE_AT = new HashMap<>();

    private Curses()
    {
    }

    public static void tickServer(MinecraftServer server)
    {
        if (!server.tickRateManager().runsNormally())
            return;

        long now = server.overworld().getGameTime();
        for (var player : server.getPlayerList().getPlayers())
        {
            var running = RUNNING.get(player.getUUID());
            if (running != null)
                advance(player, running);
            else if (now % ROLL_INTERVAL_TICKS == Math.floorMod(player.getUUID().hashCode(), ROLL_INTERVAL_TICKS))
                roll(player, now);
        }
    }

    private static void advance(ServerPlayer player, Running running)
    {
        if (!player.isAlive() || player.level() != running.level)
        {
            end(player);
            return;
        }

        if (running.kind == CurseKind.RAPTURE)
            RaptureLift.tick(player, running.age, running.seed);
        if (++running.age >= CurseTimings.duration(running.kind, running.seed))
            finish(player);
    }

    private static void roll(ServerPlayer player, long now)
    {
        int corruption = Corruption.get(player);
        if (corruption <= 0 || player.isSpectator() || now < NEXT_STRIKE_AT.getOrDefault(player.getUUID(), 0L))
            return;
        if (player.getRandom().nextFloat() >= STRIKE_CHANCE_AT_MAX * corruption / Corruption.MAX)
            return;

        var eligible = Arrays.stream(CurseKind.values())
                .filter(kind -> corruption >= kind.threshold() && kind.canStrike(player))
                .toList();
        if (!eligible.isEmpty())
            begin(player, eligible.get(player.getRandom().nextInt(eligible.size())));
    }

    /**
     * Strikes the player right away, whatever their corruption and surroundings, replacing a curse already running.
     */
    public static void begin(ServerPlayer player, CurseKind kind)
    {
        finish(player);

        long seed = player.getRandom().nextLong();
        RUNNING.put(player.getUUID(), new Running(kind, seed, player.level()));
        NEXT_STRIKE_AT.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + COOLDOWN_TICKS);
        ServerPlayNetworking.send(player, new CursePayloads.Begin(kind, seed));
    }

    /**
     * Cuts short the curse running on the player, for both sides.
     */
    public static boolean end(ServerPlayer player)
    {
        if (!finish(player))
            return false;
        ServerPlayNetworking.send(player, new CursePayloads.End());
        return true;
    }

    public static @Nullable CurseKind running(Player player)
    {
        var running = RUNNING.get(player.getUUID());
        return running == null ? null : running.kind;
    }

    private static boolean finish(ServerPlayer player)
    {
        var running = RUNNING.remove(player.getUUID());
        if (running == null)
            return false;
        if (running.kind == CurseKind.RAPTURE)
            RaptureLift.finish(player);
        return true;
    }

    public static void onLeave(ServerPlayer player)
    {
        finish(player);
        NEXT_STRIKE_AT.remove(player.getUUID());
    }

    public static void clear()
    {
        RUNNING.clear();
        NEXT_STRIKE_AT.clear();
    }

    private static final class Running
    {
        private final CurseKind   kind;
        private final long        seed;
        private final ServerLevel level;
        private int age;

        private Running(CurseKind kind, long seed, ServerLevel level)
        {
            this.kind = kind;
            this.seed = seed;
            this.level = level;
        }
    }
}
