package net.voxelindustry.voidheart.common.content.armillary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridge;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusBridges;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Every way an armillary holds open onto a bridge of the Daedalus. They have no end of their own: one only closes once it is severed, a short while after.
 * Everyone remembers the way they came in by, since the Daedalus has no edges to tell connections apart by.
 */
public final class DaedalusConnections extends SavedData
{
    public static final long STABLE = Long.MAX_VALUE;

    /** Long enough for the last two warnings, and for anyone near the rift to make it back on their own. */
    public static final int SEVER_TICKS = 20 * 60 * 2;

    private static final int RIFT_SPAN    = DistrictPos.CHASM / 2 + 2;
    private static final int ARRIVAL_SPAN = DistrictPos.CHASM / 2 - 1;
    private static final int LANE_CENTRE  = 3;
    private static final int RIFT_RISE    = 1;
    private static final int[] RIFT_RISES = { 0, 1, -1 };

    public record Connection(int id, ResourceKey<Level> dimension, BlockPos armillary, DistrictPos district, boolean south, int bridge, long decayAt)
    {
        private static final Codec<DistrictPos> DISTRICT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(DistrictPos::x),
                Codec.INT.fieldOf("z").forGetter(DistrictPos::z)
        ).apply(instance, DistrictPos::new));

        private static final Codec<Connection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("id").forGetter(Connection::id),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Connection::dimension),
                BlockPos.CODEC.fieldOf("armillary").forGetter(Connection::armillary),
                DISTRICT_CODEC.fieldOf("district").forGetter(Connection::district),
                Codec.BOOL.fieldOf("south").forGetter(Connection::south),
                Codec.INT.fieldOf("bridge").forGetter(Connection::bridge),
                Codec.LONG.fieldOf("decay_at").forGetter(Connection::decayAt)
        ).apply(instance, Connection::new));

        public boolean isSevered()
        {
            return decayAt != STABLE;
        }

        Connection severed(long decayAt)
        {
            return new Connection(id, dimension, armillary, district, south, bridge, decayAt);
        }

        /**
         * One open to the chasm where the district side has one, since a tunnel would hide where the player has arrived.
         */
        public DaedalusBridge bridgeIn(long worldSeed)
        {
            var bridges = DaedalusBridges.between(worldSeed, district, south ? Compass.SOUTH : Compass.EAST);
            for (int offset = 0; offset < bridges.size(); offset++)
            {
                var candidate = bridges.get(Math.floorMod(bridge + offset, bridges.size()));
                if (candidate.style() != DaedalusBridge.BridgeStyle.TUNNEL)
                    return candidate;
            }
            return bridges.get(Math.floorMod(bridge, bridges.size()));
        }
    }

    private static final Codec<DaedalusConnections> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("next_id").forGetter(state -> state.nextId),
            Connection.CODEC.listOf().fieldOf("open").forGetter(state -> List.copyOf(state.open.values())),
            Connection.CODEC.listOf().fieldOf("closed").forGetter(state -> List.copyOf(state.closed.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("entries").forGetter(state -> state.entries)
    ).apply(instance, DaedalusConnections::new));

    public static final SavedDataType<DaedalusConnections> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "daedalus_connections"),
            DaedalusConnections::new,
            CODEC,
            null);

    private int nextId;

    private final Map<Integer, Connection> open    = new HashMap<>();
    private final Map<Integer, Connection> closed  = new HashMap<>();
    /** Which way each player last came into the Daedalus by. */
    private final Map<UUID, Integer>       entries = new HashMap<>();

    public DaedalusConnections()
    {
    }

    private DaedalusConnections(int nextId, List<Connection> open, List<Connection> closed, Map<UUID, Integer> entries)
    {
        this.nextId = nextId;
        open.forEach(connection -> this.open.put(connection.id(), connection));
        closed.forEach(connection -> this.closed.put(connection.id(), connection));
        this.entries.putAll(entries);
    }

    public static DaedalusConnections get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Connection open(ResourceKey<Level> dimension, BlockPos armillary, ArmillaryChart.StrataPoint point)
    {
        var connection = new Connection(nextId++, dimension, armillary, new DistrictPos(point.districtX(), point.districtZ()), point.south(), point.bridge(), STABLE);
        open.put(connection.id(), connection);
        setDirty();
        return connection;
    }

    public @Nullable Connection get(int id)
    {
        return open.get(id);
    }

    public @Nullable Integer entryOf(UUID player)
    {
        return entries.get(player);
    }

    /**
     * Every connection still open, severed or not, so something in the Daedalus can find the way onto its bridge without a player who came in by it.
     */
    public Collection<Connection> openConnections()
    {
        return Collections.unmodifiableCollection(open.values());
    }

    /**
     * Starts the end of a connection. Nothing more happens to one already on its way out.
     */
    public boolean sever(int id, long now)
    {
        return decayAt(id, now + SEVER_TICKS, true);
    }

    /**
     * Moves the end of a connection closer, severed or not, for commands.
     */
    public boolean decayAt(int id, long decayAt)
    {
        return decayAt(id, decayAt, false);
    }

    private boolean decayAt(int id, long decayAt, boolean onlyStable)
    {
        var connection = open.get(id);
        if (connection == null || onlyStable && connection.isSevered())
            return false;

        open.put(id, connection.severed(decayAt));
        setDirty();
        return true;
    }

    /**
     * Onto the bridge the connection opened on, facing into the district it named, with the way back just behind.
     */
    public static void enter(ServerPlayer player, int id)
    {
        var server = player.level().getServer();
        var connections = get(server);
        var connection = connections.get(id);
        var level = VoidDaedalus.level(server);
        if (connection == null || level == null)
            return;

        var bridge = connection.bridgeIn(level.getSeed());
        VoidDaedalus.arriveOn(player, bridge, ARRIVAL_SPAN, true);
        connections.entries.put(player.getUUID(), id);
        connections.setDirty();

        var wanted = new BlockPos(bridge.worldX(RIFT_SPAN, LANE_CENTRE), bridge.walkY(RIFT_SPAN) + RIFT_RISE, bridge.worldZ(RIFT_SPAN, LANE_CENTRE));
        for (int rise : RIFT_RISES)
        {
            var rift = wanted.above(rise);
            if (level.getBlockEntity(rift) instanceof ArmillaryRiftTile tile && tile.getConnection() == id)
                return;
        }

        // Something built on the bridge must not leave the player without a way back
        var rift = wanted;
        for (int rise : RIFT_RISES)
        {
            if (level.getBlockState(wanted.above(rise)).isAir())
            {
                rift = wanted.above(rise);
                break;
            }
        }
        level.setBlockAndUpdate(rift, VoidHeartBlocks.ARMILLARY_RIFT.defaultBlockState());
        if (level.getBlockEntity(rift) instanceof ArmillaryRiftTile tile)
            tile.setConnection(id);
    }

    /**
     * Back through a rift on a bridge, to the armillary it was opened by. False once nothing answers from there.
     */
    public static boolean leave(ServerPlayer player, int id)
    {
        var connections = get(player.level().getServer());
        var connection = connections.get(id);
        if (connection == null)
            return false;

        connections.eject(player, connection, null);
        return true;
    }

    /**
     * A way in some other way than through an armillary leads back through none.
     */
    public static void forget(ServerPlayer player)
    {
        var connections = get(player.level().getServer());
        if (connections.entries.remove(player.getUUID()) != null)
            connections.setDirty();
    }

    public static void tickServer(MinecraftServer server)
    {
        var level = VoidDaedalus.level(server);
        if (level == null)
            return;

        var connections = get(server);
        long now = ArmillaryConnections.now(server);
        for (var connection : List.copyOf(connections.open.values()))
        {
            if (!connection.isSevered())
                continue;

            long remaining = connection.decayAt() - now;
            var inside = new ArrayList<ServerPlayer>();
            for (var player : level.players())
            {
                if (Integer.valueOf(connection.id()).equals(connections.entries.get(player.getUUID())))
                    inside.add(player);
            }
            ArmillaryConnections.warn(inside, remaining);

            if (remaining <= 0)
            {
                connections.open.remove(connection.id());
                connections.closed.put(connection.id(), connection);
                connections.setDirty();
                inside.stream().filter(ServerPlayer::isAlive).forEach(player -> connections.eject(player, connection, "voidheart.connection.collapsed"));
                connections.forgetUnused(connection.id());
            }
        }
    }

    /**
     * Someone who logged out in the Daedalus while their way back closed is sent back like everyone else was.
     */
    public static void onJoin(ServerPlayer player)
    {
        if (!player.level().dimension().equals(VoidDaedalus.WORLD_KEY))
            return;

        var connections = get(player.level().getServer());
        var id = connections.entries.get(player.getUUID());
        var connection = id == null ? null : connections.closed.get(id);
        if (connection != null)
            connections.eject(player, connection, "voidheart.connection.collapsed");
    }

    private void eject(ServerPlayer player, Connection connection, @Nullable String message)
    {
        entries.remove(player.getUUID());
        setDirty();
        ArmillaryConnections.eject(player, connection.dimension(), connection.armillary(), message);
        forgetUnused(connection.id());
    }

    /**
     * A closed connection is only kept for those still to be sent back by it.
     */
    private void forgetUnused(int id)
    {
        if (closed.containsKey(id) && !entries.containsValue(id))
        {
            closed.remove(id);
            setDirty();
        }
    }
}
