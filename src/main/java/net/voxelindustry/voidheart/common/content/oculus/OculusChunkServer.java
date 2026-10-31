package net.voxelindustry.voidheart.common.content.oculus;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Answers viewers asking for land their client has not loaded, from chunks the server already holds.
 * Never loads chunks, and spreads answers over ticks so a wide table does not flood a connection.
 */
public final class OculusChunkServer
{
    private static final int ANSWERS_PER_TICK = 6;
    private static final int MAX_QUEUED = 256;
    private static final double VIEWER_DISTANCE = 48;
    /**
     * World blocks allowed past the land shown, covering a view still gliding or built ahead of a followed player.
     */
    private static final double AREA_MARGIN = 64;

    private static final Map<UUID, ArrayDeque<Pending>> QUEUES = new HashMap<>();

    private record Pending(ServerLevel level, long chunk, int windowBottom, int windowHeight)
    {
    }

    private OculusChunkServer()
    {
    }

    public static void handle(OculusChunkPayloads.Request request, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        var level = player.level();
        if (request.windowHeight() <= 0 || request.windowHeight() > OculusView.WIDEST_SCALE
                || request.windowBottom() < level.getMinY() || request.windowBottom() + request.windowHeight() > level.getMaxY() + 1)
            return;
        if (player.position().distanceToSqr(Vec3.atCenterOf(request.table())) > VIEWER_DISTANCE * VIEWER_DISTANCE)
            return;

        var tile = OculusTableStructure.getTile(level, request.table());
        var footprint = OculusTableStructure.footprint(level, request.table());
        if (tile == null || footprint.isEmpty())
            return;

        var centers = new ArrayList<Vec3>();
        centers.add(new Vec3(footprint.get().centerX(), 0, footprint.get().centerZ()));
        tile.getView().follow()
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(target -> target.level() == level)
                .ifPresent(target -> centers.add(target.position()));

        double reach = Math.max(footprint.get().width(), footprint.get().depth()) * tile.getView().scale() * Math.sqrt(2) / 2 + AREA_MARGIN;
        var queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        for (long chunk : request.chunks())
        {
            double chunkX = SectionPos.sectionToBlockCoord(ChunkPos.getX(chunk), 8);
            double chunkZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(chunk), 8);
            boolean shown = centers.stream().anyMatch(center ->
                    (chunkX - center.x) * (chunkX - center.x) + (chunkZ - center.z) * (chunkZ - center.z) <= reach * reach);
            if (!shown)
                continue;

            if (queue.size() >= MAX_QUEUED)
                queue.poll();
            queue.add(new Pending(level, chunk, request.windowBottom(), request.windowHeight()));
        }
    }

    public static void tick(MinecraftServer server)
    {
        var iterator = QUEUES.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || entry.getValue().isEmpty())
            {
                iterator.remove();
                continue;
            }

            for (int answered = 0; answered < ANSWERS_PER_TICK && !entry.getValue().isEmpty(); answered++)
            {
                var pending = entry.getValue().poll();
                if (pending.level() == player.level())
                    ServerPlayNetworking.send(player, encode(pending));
            }
        }
    }

    private static OculusChunkPayloads.Sections encode(Pending pending)
    {
        var level = pending.level();
        int chunkX = ChunkPos.getX(pending.chunk());
        int chunkZ = ChunkPos.getZ(pending.chunk());
        var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null)
            return new OculusChunkPayloads.Sections(chunkX, chunkZ, pending.windowBottom(), pending.windowHeight(), false, new byte[0]);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            int minSection = SectionPos.blockToSectionCoord(pending.windowBottom());
            int maxSection = SectionPos.blockToSectionCoord(pending.windowBottom() + pending.windowHeight() - 1);
            for (int sectionY = minSection; sectionY <= maxSection; sectionY++)
            {
                int index = level.getSectionIndexFromSectionY(sectionY);
                var section = index < 0 || index >= chunk.getSectionsCount() ? null : chunk.getSection(index);
                buffer.writeBoolean(section != null && !section.hasOnlyAir());
                if (section != null && !section.hasOnlyAir())
                    section.write(buffer);
            }
            var data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new OculusChunkPayloads.Sections(chunkX, chunkZ, pending.windowBottom(), pending.windowHeight(), true, data);
        } finally
        {
            buffer.release();
        }
    }

    public static void clear()
    {
        QUEUES.clear();
    }
}
