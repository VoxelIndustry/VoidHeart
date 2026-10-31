package net.voxelindustry.voidheart.client.render.oculus;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads.RELIEF_SIDE;
import static net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads.RELIEF_STEP;

/**
 * Elevation of the land as first generated, for the parts of a hologram no beholder sees. It never changes, so it is
 * kept until the level changes or it has not been shown for a long while.
 */
final class OculusRelief
{
    private static final int REQUEST_INTERVAL = 5;
    private static final int REQUESTS_PER_FLUSH = 4;
    private static final int ANSWER_TIMEOUT = 100;
    private static final int FORGET_AFTER = 6000;

    private record Grid(int[] heights, long lastWantedTick)
    {
    }

    private static final Long2ObjectOpenHashMap<Grid> GRIDS = new Long2ObjectOpenHashMap<>();
    private static final Long2LongOpenHashMap REQUESTED_AT = new Long2LongOpenHashMap();
    private static final Map<BlockPos, List<Long>> WANTED = new HashMap<>();

    private OculusRelief()
    {
    }

    /**
     * The elevation samples of a chunk, if the server sent them. The grid is shared with mesh builders, never written.
     */
    static int @Nullable [] get(long chunk, long tick)
    {
        var grid = GRIDS.get(chunk);
        if (grid == null)
            return null;
        if (grid.lastWantedTick() != tick)
            GRIDS.put(chunk, new Grid(grid.heights(), tick));
        return grid.heights();
    }

    static void want(BlockPos table, long chunk, long tick)
    {
        if (GRIDS.containsKey(chunk) || REQUESTED_AT.containsKey(chunk) && tick - REQUESTED_AT.get(chunk) < ANSWER_TIMEOUT)
            return;
        REQUESTED_AT.put(chunk, tick);
        WANTED.computeIfAbsent(table, key -> new ArrayList<>()).add(chunk);
    }

    /**
     * The first air block above the generated surface of a column, eased between the samples around it.
     *
     * @param x column within the chunk, from 0 to 15
     */
    static int height(int[] heights, int x, int z)
    {
        int cellX = Math.min(x / RELIEF_STEP, RELIEF_SIDE - 2);
        int cellZ = Math.min(z / RELIEF_STEP, RELIEF_SIDE - 2);
        float fx = (x - cellX * RELIEF_STEP) / (float) RELIEF_STEP;
        float fz = (z - cellZ * RELIEF_STEP) / (float) RELIEF_STEP;
        float north = Mth.lerp(fx, heights[cellX + cellZ * RELIEF_SIDE], heights[cellX + 1 + cellZ * RELIEF_SIDE]);
        float south = Mth.lerp(fx, heights[cellX + (cellZ + 1) * RELIEF_SIDE], heights[cellX + 1 + (cellZ + 1) * RELIEF_SIDE]);
        return Math.round(Mth.lerp(fz, north, south));
    }

    static void tick(long tick)
    {
        if (tick % REQUEST_INTERVAL == 0 && !WANTED.isEmpty())
            flush();
        GRIDS.long2ObjectEntrySet().removeIf(entry -> tick - entry.getValue().lastWantedTick() > FORGET_AFTER);
    }

    private static void flush()
    {
        int sent = 0;
        var iterator = WANTED.entrySet().iterator();
        while (iterator.hasNext() && sent < REQUESTS_PER_FLUSH)
        {
            var entry = iterator.next();
            var chunks = entry.getValue();
            for (int from = 0; from < chunks.size() && sent < REQUESTS_PER_FLUSH; from += OculusSightPayloads.MAX_CHUNKS_PER_REQUEST)
            {
                var batch = List.copyOf(chunks.subList(from, Math.min(chunks.size(), from + OculusSightPayloads.MAX_CHUNKS_PER_REQUEST)));
                ClientPlayNetworking.send(new OculusSightPayloads.ReliefRequest(entry.getKey(), batch));
                sent++;
            }
            iterator.remove();
        }
    }

    static void receive(OculusSightPayloads.Relief payload, ClientPlayNetworking.Context context)
    {
        long tick = OculusRenderer.ticks();
        for (var relief : payload.chunks())
        {
            if (relief.heights().size() != RELIEF_SIDE * RELIEF_SIDE)
                continue;
            var heights = relief.heights().stream().mapToInt(Integer::intValue).toArray();
            REQUESTED_AT.remove(relief.chunk());
            GRIDS.put(relief.chunk(), new Grid(heights, tick));
            OculusRenderer.onRemoteChunk(ChunkPos.getX(relief.chunk()), ChunkPos.getZ(relief.chunk()));
        }
    }

    static int size()
    {
        return GRIDS.size();
    }

    static void clear()
    {
        GRIDS.clear();
        REQUESTED_AT.clear();
        WANTED.clear();
    }
}
