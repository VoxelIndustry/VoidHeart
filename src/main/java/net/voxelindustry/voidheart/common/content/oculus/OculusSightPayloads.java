package net.voxelindustry.voidheart.common.content.oculus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a table sees, and the lay of the land where it sees nothing: the surface the world generator gives each
 * column from the seed, ignoring everything built or dug since.
 */
public final class OculusSightPayloads
{
    public static final int MAX_CHUNKS_PER_REQUEST = 32;
    /**
     * Elevation is sampled every few blocks, on a grid whose last row and column fall on the next chunk.
     */
    public static final int RELIEF_STEP = 4;
    public static final int RELIEF_SIDE = 16 / RELIEF_STEP + 1;

    private static final int SIGHT_INTERVAL = 10;
    private static final int RELIEF_TASKS_PER_PLAYER = 2;
    private static final int MAX_QUEUED_RELIEF = 96;
    private static final int CACHED_CHUNKS_PER_LEVEL = 1 << 16;

    private static final Map<SightKey, Long> LAST_SIGHT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, int[]>> RELIEF = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RELIEF_TASKS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<List<Long>>> RELIEF_QUEUES = new HashMap<>();

    private OculusSightPayloads()
    {
    }

    private record SightKey(UUID player, BlockPos table)
    {
    }

    public record Request(BlockPos table) implements CustomPacketPayload
    {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_sight_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = BlockPos.STREAM_CODEC.map(Request::new, Request::table).cast();

        @Override
        public Type<Request> type()
        {
            return TYPE;
        }
    }

    public record Sight(BlockPos table, OculusSight sight) implements CustomPacketPayload
    {
        public static final Type<Sight> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_sight"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Sight> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Sight::table,
                OculusSight.STREAM_CODEC, Sight::sight,
                Sight::new);

        @Override
        public Type<Sight> type()
        {
            return TYPE;
        }
    }

    public record ReliefRequest(BlockPos table, List<Long> chunks) implements CustomPacketPayload
    {
        public static final Type<ReliefRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_relief_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ReliefRequest> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ReliefRequest::table,
                ByteBufCodecs.LONG.apply(ByteBufCodecs.list(MAX_CHUNKS_PER_REQUEST)), ReliefRequest::chunks,
                ReliefRequest::new);

        @Override
        public Type<ReliefRequest> type()
        {
            return TYPE;
        }
    }

    /**
     * @param heights the first air block above the generated surface, row by row along x then z, every
     *                {@link #RELIEF_STEP} blocks from the chunk corner
     */
    public record ChunkRelief(long chunk, List<Integer> heights)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ChunkRelief> CODEC = StreamCodec.composite(
                ByteBufCodecs.LONG, ChunkRelief::chunk,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(RELIEF_SIDE * RELIEF_SIDE)), ChunkRelief::heights,
                ChunkRelief::new);
    }

    public record Relief(List<ChunkRelief> chunks) implements CustomPacketPayload
    {
        public static final Type<Relief> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "oculus_relief"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Relief> CODEC = ChunkRelief.CODEC.apply(ByteBufCodecs.list(MAX_CHUNKS_PER_REQUEST))
                .map(Relief::new, Relief::chunks);

        @Override
        public Type<Relief> type()
        {
            return TYPE;
        }
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sight.TYPE, Sight.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReliefRequest.TYPE, ReliefRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Relief.TYPE, Relief.CODEC);
    }

    public static void handleSight(Request request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        var key = new SightKey(player.getUUID(), request.table());
        var last = LAST_SIGHT.get(key);
        if (last != null && level.getGameTime() - last < SIGHT_INTERVAL)
            return;
        if (OculusViewed.of(player, request.table()) == null)
            return;

        LAST_SIGHT.put(key, level.getGameTime());
        ServerPlayNetworking.send(player, new Sight(request.table(), OculusSight.of(level, request.table())));
    }

    public static void handleRelief(ReliefRequest request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        var viewed = OculusViewed.of(player, request.table());
        if (viewed == null)
            return;

        var chunks = request.chunks().stream().filter(viewed::shows).distinct().toList();
        if (chunks.isEmpty())
            return;

        var queue = RELIEF_QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() >= MAX_QUEUED_RELIEF)
            queue.poll();
        queue.add(chunks);
        sampleNext(player, level);
    }

    /**
     * Samples the next batches a player asked for, a few at a time so one viewer does not hold every worker.
     */
    private static void sampleNext(ServerPlayer player, ServerLevel level)
    {
        var queue = RELIEF_QUEUES.get(player.getUUID());
        while (queue != null && !queue.isEmpty() && RELIEF_TASKS.getOrDefault(player.getUUID(), 0) < RELIEF_TASKS_PER_PLAYER)
        {
            var chunks = queue.poll();
            RELIEF_TASKS.merge(player.getUUID(), 1, Integer::sum);
            var cache = RELIEF.computeIfAbsent(level.dimension(), dimension -> new ConcurrentHashMap<>());
            CompletableFuture.supplyAsync(() -> chunks.stream().map(chunk -> new ChunkRelief(chunk, sample(level, cache, chunk))).toList(), Util.backgroundExecutor())
                    .whenCompleteAsync((relief, error) ->
                    {
                        RELIEF_TASKS.computeIfPresent(player.getUUID(), (id, tasks) -> tasks > 1 ? tasks - 1 : null);
                        if (error != null)
                            VoidHeart.LOGGER.log(java.util.logging.Level.WARNING, "Failed to sample the Oculus relief", error);
                        if (player.hasDisconnected() || player.level() != level)
                        {
                            RELIEF_QUEUES.remove(player.getUUID());
                            return;
                        }
                        if (error == null)
                            ServerPlayNetworking.send(player, new Relief(relief));
                        sampleNext(player, level);
                    }, level.getServer());
        }
        if (queue != null && queue.isEmpty())
            RELIEF_QUEUES.remove(player.getUUID());
    }

    private static List<Integer> sample(ServerLevel level, Map<Long, int[]> cache, long chunk)
    {
        var heights = cache.get(chunk);
        if (heights == null)
        {
            heights = generatedHeights(level, ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (cache.size() >= CACHED_CHUNKS_PER_LEVEL)
                cache.clear();
            cache.put(chunk, heights);
        }

        var list = new ArrayList<Integer>(heights.length);
        for (int height : heights)
            list.add(height);
        return list;
    }

    /**
     * Asks the generator, without touching any chunk, where the surface of each sampled column was first laid.
     */
    private static int[] generatedHeights(ServerLevel level, int chunkX, int chunkZ)
    {
        var source = level.getChunkSource();
        var heights = new int[RELIEF_SIDE * RELIEF_SIDE];
        for (int z = 0; z < RELIEF_SIDE; z++)
        {
            for (int x = 0; x < RELIEF_SIDE; x++)
            {
                int blockX = (chunkX << 4) + x * RELIEF_STEP;
                int blockZ = (chunkZ << 4) + z * RELIEF_STEP;
                heights[x + z * RELIEF_SIDE] = source.getGenerator().getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
            }
        }
        return heights;
    }

    public static void clear()
    {
        LAST_SIGHT.clear();
        RELIEF.clear();
        RELIEF_TASKS.clear();
        RELIEF_QUEUES.clear();
    }
}
