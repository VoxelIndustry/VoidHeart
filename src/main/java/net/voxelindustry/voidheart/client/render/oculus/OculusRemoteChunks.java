package net.voxelindustry.voidheart.client.render.oculus;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Sections of land projected by a table but beyond what this client has loaded, sent by the server on request.
 * They are refreshed while shown, as this client hears of no block change there.
 */
final class OculusRemoteChunks
{
    private static final int REQUEST_INTERVAL = 5;
    private static final int REQUESTS_PER_FLUSH = 4;
    private static final int ANSWER_TIMEOUT = 100;
    private static final int UNAVAILABLE_RETRY = 200;
    private static final int REFRESH_AGE = 1200;
    private static final int FORGET_AFTER = 200;

    /**
     * @param sections the window's sections from its bottom one up, missing when empty
     */
    record Remote(int windowBottom, int windowHeight, @Nullable LevelChunkSection @Nullable [] sections, long receivedTick, long lastWantedTick)
    {
        boolean covers(int bottom, int height)
        {
            return windowBottom == bottom && windowHeight == height;
        }

        Remote wanted(long tick)
        {
            return new Remote(windowBottom, windowHeight, sections, receivedTick, tick);
        }
    }

    private record WindowKey(BlockPos table, int windowBottom, int windowHeight)
    {
    }

    private static final Long2ObjectOpenHashMap<Remote> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static final Long2LongOpenHashMap REQUESTED_AT = new Long2LongOpenHashMap();
    private static final Map<WindowKey, List<Long>> WANTED = new HashMap<>();

    private OculusRemoteChunks()
    {
    }

    /**
     * The sections of a chunk within this window, if the server sent them.
     *
     * @return nothing when they never came or came for another window
     */
    static @Nullable LevelChunkSection @Nullable [] sections(long chunk, int windowBottom, int windowHeight, long tick)
    {
        var remote = CHUNKS.get(chunk);
        if (remote == null || remote.sections() == null || !remote.covers(windowBottom, windowHeight))
            return null;
        CHUNKS.put(chunk, remote.wanted(tick));
        return remote.sections();
    }

    /**
     * Whether the server answered it has not loaded this chunk either, so there is nothing to wait for.
     */
    static boolean isUnavailable(long chunk, int windowBottom, int windowHeight)
    {
        var remote = CHUNKS.get(chunk);
        return remote != null && remote.sections() == null && remote.covers(windowBottom, windowHeight);
    }

    /**
     * Asks the server for a chunk this client lacks, unless it was asked recently or is known to be unloaded there too.
     */
    static void want(BlockPos table, long chunk, int windowBottom, int windowHeight, long tick)
    {
        var remote = CHUNKS.get(chunk);
        if (remote != null && remote.covers(windowBottom, windowHeight))
        {
            CHUNKS.put(chunk, remote.wanted(tick));
            if (remote.sections() != null || tick - remote.receivedTick() < UNAVAILABLE_RETRY)
                return;
        }
        if (REQUESTED_AT.containsKey(chunk) && tick - REQUESTED_AT.get(chunk) < ANSWER_TIMEOUT)
            return;

        REQUESTED_AT.put(chunk, tick);
        WANTED.computeIfAbsent(new WindowKey(table, windowBottom, windowHeight), key -> new ArrayList<>()).add(chunk);
    }

    /**
     * Sends batched requests, and drops answers that went stale or are no longer shown.
     *
     * @param onStale told of chunks whose remote land was dropped while still shown, so they are fetched again
     */
    static void tick(long tick, LongConsumer onStale)
    {
        if (tick % REQUEST_INTERVAL == 0 && !WANTED.isEmpty())
            flush();

        var iterator = CHUNKS.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var remote = entry.getValue();
            if (tick - remote.lastWantedTick() > FORGET_AFTER)
                iterator.remove();
            else if (remote.sections() != null && tick - remote.receivedTick() > REFRESH_AGE)
            {
                iterator.remove();
                onStale.accept(entry.getLongKey());
            }
        }
    }

    private static void flush()
    {
        int sent = 0;
        var iterator = WANTED.entrySet().iterator();
        while (iterator.hasNext() && sent < REQUESTS_PER_FLUSH)
        {
            var entry = iterator.next();
            var chunks = entry.getValue();
            for (int from = 0; from < chunks.size() && sent < REQUESTS_PER_FLUSH; from += OculusChunkPayloads.MAX_CHUNKS_PER_REQUEST)
            {
                var batch = List.copyOf(chunks.subList(from, Math.min(chunks.size(), from + OculusChunkPayloads.MAX_CHUNKS_PER_REQUEST)));
                var key = entry.getKey();
                ClientPlayNetworking.send(new OculusChunkPayloads.Request(key.table(), key.windowBottom(), key.windowHeight(), batch));
                sent++;
            }
            iterator.remove();
        }
    }

    static void receive(OculusChunkPayloads.Sections payload, ClientPlayNetworking.Context context)
    {
        var level = context.client().level;
        if (level == null)
            return;

        long chunk = ChunkPos.pack(payload.chunkX(), payload.chunkZ());
        long tick = OculusRenderer.ticks();
        REQUESTED_AT.remove(chunk);
        if (!payload.available())
        {
            CHUNKS.put(chunk, new Remote(payload.windowBottom(), payload.windowHeight(), null, tick, tick));
            return;
        }

        int minSection = SectionPos.blockToSectionCoord(payload.windowBottom());
        int maxSection = SectionPos.blockToSectionCoord(payload.windowBottom() + payload.windowHeight() - 1);
        var sections = new LevelChunkSection[maxSection - minSection + 1];
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        try
        {
            for (int i = 0; i < sections.length; i++)
            {
                if (!buffer.readBoolean())
                    continue;
                sections[i] = new LevelChunkSection(level.palettedContainerFactory());
                sections[i].read(buffer);
            }
        } catch (RuntimeException e)
        {
            // A malformed answer is dropped, the chunk is asked again once the request times out
            return;
        } finally
        {
            buffer.release();
        }

        CHUNKS.put(chunk, new Remote(payload.windowBottom(), payload.windowHeight(), sections, tick, tick));
        OculusRenderer.onRemoteChunk(payload.chunkX(), payload.chunkZ());
    }

    static int size()
    {
        return CHUNKS.size();
    }

    static void clear()
    {
        CHUNKS.clear();
        REQUESTED_AT.clear();
        WANTED.clear();
    }
}
