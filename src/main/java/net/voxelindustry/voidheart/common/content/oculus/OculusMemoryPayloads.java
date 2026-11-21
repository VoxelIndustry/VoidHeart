package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.common.content.crystal.CradleBalance;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Sends a recorded place to a client whose table shows it: a header with where it is and the creatures in it, then its land a chunk at a time, spread
 * over ticks as the Oculus spreads land a client lacks.
 */
public final class OculusMemoryPayloads
{
    private static final int CHUNKS_PER_TICK = 6;
    private static final int MAX_QUEUED      = 512;
    /** How far a player may stand from the table whose recording they ask for. */
    private static final double VIEW_REACH   = 64;

    /** The most sections a recording keeps per chunk: its radius every way, plus the sections it starts and ends partway into. */
    public static final int MAX_SECTIONS = CradleBalance.RECORD_RADIUS * 2 / 16 + 2;

    /**
     * @param table the table showing the recording, which the asker must stand near
     */
    public record Request(UUID recording, BlockPos table) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_memory_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Request::recording,
                BlockPos.STREAM_CODEC, Request::table, Request::new);

        @Override
        public Type<Request> type()
        {
            return TYPE;
        }
    }

    /**
     * Where a recording is and what lived there. Sent first, with no land: {@link Land} follows.
     */
    public record Header(UUID recording, boolean found, BlockPos center, int minSection, int sectionCount, int surface,
                         List<CrystalRecordings.Creature> creatures) implements CustomPacketPayload
    {
        public static final Type<Header> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_memory_header"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Header> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Header::recording,
                ByteBufCodecs.BOOL, Header::found,
                BlockPos.STREAM_CODEC, Header::center,
                ByteBufCodecs.VAR_INT, Header::minSection,
                ByteBufCodecs.VAR_INT, Header::sectionCount,
                ByteBufCodecs.VAR_INT, Header::surface,
                CrystalRecordings.Creature.STREAM_CODEC.apply(ByteBufCodecs.list(CrystalRecordings.MAX_CREATURES)), Header::creatures,
                Header::new);

        @Override
        public Type<Header> type()
        {
            return TYPE;
        }
    }

    public record Land(UUID recording, int chunkX, int chunkZ, byte[] data, List<Integer> cover) implements CustomPacketPayload
    {
        public static final Type<Land> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_memory_land"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Land> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Land::recording,
                ByteBufCodecs.VAR_INT, Land::chunkX,
                ByteBufCodecs.VAR_INT, Land::chunkZ,
                ByteBufCodecs.BYTE_ARRAY, Land::data,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(OculusChunkPayloads.COLUMNS)), Land::cover,
                Land::new);

        @Override
        public Type<Land> type()
        {
            return TYPE;
        }
    }

    private record Pending(UUID recording, CrystalRecordings.Chunk chunk)
    {
    }

    private static final Map<UUID, ArrayDeque<Pending>> QUEUES = new HashMap<>();

    private OculusMemoryPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Header.TYPE, Header.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Land.TYPE, Land.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, OculusMemoryPayloads::handle);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(OculusMemoryPayloads::tick);
    }

    private static void handle(Request request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        // Only a table this player stands near, and that shows this recording, gets it sent
        var tile = OculusTableStructure.getTile(player.level(), request.table());
        boolean shown = tile != null && tile.getView().memory().map(memory -> memory.recording().id().equals(request.recording())).orElse(false);
        if (!shown || player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(request.table())) > VIEW_REACH * VIEW_REACH)
            return;
        var recording = CrystalRecordings.find(player.level().getServer(), request.recording());
        if (recording == null)
        {
            ServerPlayNetworking.send(player, new Header(request.recording(), false, BlockPos.ZERO, 0, 0, 0, List.of()));
            return;
        }
        ServerPlayNetworking.send(player, new Header(request.recording(), true, recording.center(), recording.minSection(), recording.sectionCount(),
                recording.surface(), recording.creatures()));
        var queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        // Asked again, it starts over rather than sending the same land twice
        queue.removeIf(pending -> pending.recording().equals(request.recording()));
        // Older recordings give way to this one whole, rather than it arriving cut short
        while (!queue.isEmpty() && queue.size() + recording.chunks().size() > MAX_QUEUED)
            queue.poll();
        for (var chunk : recording.chunks())
            queue.add(new Pending(request.recording(), chunk));
    }

    private static void tick(MinecraftServer server)
    {
        var iterator = QUEUES.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            var queue = entry.getValue();
            if (player == null || queue.isEmpty())
            {
                iterator.remove();
                continue;
            }
            for (int sent = 0; sent < CHUNKS_PER_TICK && !queue.isEmpty(); sent++)
            {
                var pending = queue.poll();
                var chunk = pending.chunk();
                ServerPlayNetworking.send(player, new Land(pending.recording(), chunk.x(), chunk.z(), chunk.data(), chunk.cover()));
            }
        }
    }

    public static void clear()
    {
        QUEUES.clear();
    }
}
