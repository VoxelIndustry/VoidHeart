package net.voxelindustry.voidheart.common.content.armillary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every open way into a dungeon. Kept apart from the armillaries since the players inside have usually left theirs unloaded, or it may be gone.
 * Closed connections are remembered so a player who logged out inside can still be sent back when they return.
 */
public final class ArmillaryConnections extends SavedData
{
    public record Connection(DungeonAddress address, ResourceKey<Level> dimension, BlockPos armillary, long decayAt, long duration)
    {
        private static final Codec<Connection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DungeonAddress.CODEC.fieldOf("address").forGetter(Connection::address),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Connection::dimension),
                BlockPos.CODEC.fieldOf("armillary").forGetter(Connection::armillary),
                Codec.LONG.fieldOf("decay_at").forGetter(Connection::decayAt),
                Codec.LONG.fieldOf("duration").forGetter(Connection::duration)
        ).apply(instance, Connection::new));
    }

    private static final Codec<ArmillaryConnections> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Connection.CODEC.listOf().fieldOf("open").forGetter(state -> List.copyOf(state.open.values())),
            Connection.CODEC.listOf().fieldOf("closed").forGetter(state -> List.copyOf(state.closed.values()))
    ).apply(instance, ArmillaryConnections::new));

    public static final SavedDataType<ArmillaryConnections> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "armillary_connections"),
            ArmillaryConnections::new,
            CODEC,
            null);

    private static final int RUMBLE_FAR_VOLUME = 2;

    private final Map<DungeonAddress, Connection> open   = new HashMap<>();
    private final Map<DungeonAddress, Connection> closed = new HashMap<>();

    public ArmillaryConnections()
    {
    }

    private ArmillaryConnections(List<Connection> open, List<Connection> closed)
    {
        open.forEach(connection -> this.open.put(connection.address(), connection));
        closed.forEach(connection -> this.closed.put(connection.address(), connection));
    }

    public static ArmillaryConnections get(MinecraftServer server)
    {
        return VoidDungeons.level(server).getDataStorage().computeIfAbsent(TYPE);
    }

    public static long now(MinecraftServer server)
    {
        return server.overworld().getGameTime();
    }

    public Connection open(DungeonAddress address, ResourceKey<Level> dimension, BlockPos armillary, long now, long duration)
    {
        var connection = new Connection(address, dimension, armillary, now + duration, duration);
        open.put(address, connection);
        setDirty();
        return connection;
    }

    public @Nullable Connection get(DungeonAddress address)
    {
        return open.get(address);
    }

    /**
     * Moves a connection's end closer, for commands.
     */
    public boolean decayIn(DungeonAddress address, long decayAt)
    {
        var connection = open.get(address);
        if (connection == null)
            return false;

        open.put(address, new Connection(address, connection.dimension(), connection.armillary(), decayAt, connection.duration()));
        setDirty();
        return true;
    }

    public static void tickServer(MinecraftServer server)
    {
        var level = VoidDungeons.level(server);
        if (level == null)
            return;

        var connections = get(server);
        if (connections.open.isEmpty())
            return;

        long now = now(server);
        for (var connection : List.copyOf(connections.open.values()))
        {
            long remaining = connection.decayAt() - now;
            var inside = playersInside(server, connection.address());
            warn(inside, remaining);

            if (remaining <= 0)
            {
                connections.close(connection);
                // The dead are already on their way out, and respawn wherever they would have
                inside.stream().filter(ServerPlayer::isAlive).forEach(player -> eject(player, connection, "voidheart.connection.collapsed"));
            }
        }
    }

    private void close(Connection connection)
    {
        open.remove(connection.address());
        closed.put(connection.address(), connection);
        setDirty();
    }

    private static List<ServerPlayer> playersInside(MinecraftServer server, DungeonAddress address)
    {
        var inside = new ArrayList<ServerPlayer>();
        for (var player : VoidDungeons.level(server).players())
        {
            if (address.equals(DungeonAddress.at(player.getBlockX(), player.getBlockZ())))
                inside.add(player);
        }
        return inside;
    }

    static void warn(List<ServerPlayer> inside, long remaining)
    {
        var warning = ConnectionTimeline.warningAt(remaining);
        int countdown = ConnectionTimeline.countdownAt(remaining);
        float tremor = ConnectionTimeline.tremor(remaining);
        boolean nearRumble = tremor > 0 && remaining % ConnectionTimeline.NEAR_RUMBLE_INTERVAL == 0;

        for (var player : inside)
        {
            if (warning != null)
            {
                player.sendOverlayMessage(Component.translatable(warning.key()));
                rumble(player, warning.near() ? VoidHeartSounds.CONNECTION_RUMBLE_NEAR : VoidHeartSounds.CONNECTION_RUMBLE_FAR, RUMBLE_FAR_VOLUME);
            }
            if (countdown > 0)
                player.sendOverlayMessage(Component.translatable("voidheart.connection.countdown", countdown));
            if (nearRumble && warning == null)
                rumble(player, VoidHeartSounds.CONNECTION_RUMBLE_NEAR, 1 + tremor);
            if (tremor > 0 && remaining % ConnectionTimeline.TREMOR_INTERVAL == 0)
                ServerPlayNetworking.send(player, new ArmillaryPayloads.Tremor(tremor));
        }
    }

    private static void rumble(ServerPlayer player, SoundEvent sound, float volume)
    {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.AMBIENT, volume, 1);
    }

    /**
     * Walking out through the rift at the entry. False when no connection leads back from there.
     */
    public static boolean leave(ServerPlayer player)
    {
        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        var connection = address == null ? null : get(player.level().getServer()).get(address);
        if (connection == null)
            return false;

        eject(player, connection, null);
        return true;
    }

    /**
     * A player logging back in after their dungeon closed is returned like everyone else was.
     */
    public static void onJoin(ServerPlayer player)
    {
        if (!player.level().dimension().equals(VoidDungeons.WORLD_KEY))
            return;

        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        var connection = address == null ? null : get(player.level().getServer()).closed.get(address);
        if (connection != null)
            eject(player, connection, "voidheart.connection.collapsed");
    }

    private static void eject(ServerPlayer player, Connection connection, @Nullable String message)
    {
        eject(player, connection.dimension(), connection.armillary(), message);
    }

    static void eject(ServerPlayer player, ResourceKey<Level> dimension, BlockPos armillary, @Nullable String message)
    {
        var server = player.level().getServer();
        var level = server.getLevel(dimension);
        if (level == null)
            level = server.overworld();

        var landing = ArmillaryLanding.find(level, armillary);
        TeleportUtil.teleport(player, level, landing, player.getYHeadRot(), player.getViewXRot(0));
        level.playSound(null, landing.x, landing.y, landing.z, VoidHeartSounds.CONNECTION_TRAVEL, SoundSource.PLAYERS, 1, 1);
        if (message != null)
            player.sendOverlayMessage(Component.translatable(message));
    }
}
