package net.voxelindustry.voidheart.common.content.oculus;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.voxelindustry.voidheart.VoidHeart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.logging.Level;

/**
 * Answers viewers asking for land their client has not loaded, within what the table sees. Chunks the server holds
 * are read as they are, others from the save without loading them, and land never generated stays unknown.
 * Answers are spread over ticks so a wide table does not flood a connection.
 */
public final class OculusChunkServer
{
    private static final int ANSWERS_PER_TICK = 6;
    private static final int SAVE_READS_PER_PLAYER = 4;
    private static final int MAX_QUEUED = 256;

    private static final Map<UUID, ArrayDeque<Pending>> QUEUES = new HashMap<>();
    private static final Map<UUID, Integer> SAVE_READS = new HashMap<>();
    private static final AtomicInteger SAVED_ANSWERS = new AtomicInteger();

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

        var viewed = OculusViewed.of(player, request.table());
        if (viewed == null)
            return;
        var sight = OculusSight.of(level, request.table());

        var queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        for (long chunk : request.chunks())
        {
            if (!viewed.shows(chunk) || !sight.touchesChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)))
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
                var pending = entry.getValue().peek();
                if (pending.level() != player.level())
                {
                    entry.getValue().poll();
                    continue;
                }

                var chunk = pending.level().getChunkSource().getChunkNow(ChunkPos.getX(pending.chunk()), ChunkPos.getZ(pending.chunk()));
                if (chunk != null)
                {
                    entry.getValue().poll();
                    ServerPlayNetworking.send(player, encode(pending, sectionY -> sectionOf(pending.level(), chunk.getSections(), sectionY)));
                    continue;
                }

                if (SAVE_READS.getOrDefault(player.getUUID(), 0) >= SAVE_READS_PER_PLAYER)
                    break;
                entry.getValue().poll();
                readSaved(server, player.getUUID(), pending);
            }
        }
    }

    private static @Nullable LevelChunkSection sectionOf(ServerLevel level, LevelChunkSection[] sections, int sectionY)
    {
        int index = level.getSectionIndexFromSectionY(sectionY);
        return index < 0 || index >= sections.length ? null : sections[index];
    }

    /**
     * Reads a chunk the server has not loaded from the save, off the main thread. It answers once read.
     */
    private static void readSaved(MinecraftServer server, UUID playerId, Pending pending)
    {
        var level = pending.level();
        var chunkMap = level.getChunkSource().chunkMap;
        var pos = ChunkPos.unpack(pending.chunk());
        SAVE_READS.merge(playerId, 1, Integer::sum);

        chunkMap.read(pos)
                .thenApplyAsync(tag -> tag.map(saved -> encodeSaved(pending, chunkMap, saved))
                        .orElseGet(() -> unavailable(pending)), Util.backgroundExecutor())
                .whenCompleteAsync((answer, error) ->
                {
                    SAVE_READS.computeIfPresent(playerId, (id, reads) -> reads > 1 ? reads - 1 : null);
                    if (error != null)
                        VoidHeart.LOGGER.log(Level.WARNING, "Failed to read saved land for an Oculus Table", error);
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == level)
                        ServerPlayNetworking.send(player, error == null ? answer : unavailable(pending));
                }, server);
    }

    private static OculusChunkPayloads.Sections encodeSaved(Pending pending, ChunkMap chunkMap, CompoundTag saved)
    {
        var level = pending.level();
        var upgraded = chunkMap.upgradeChunkTag(saved, -1,
                ChunkMap.getChunkDataFixContextTag(level.dimension(), level.getChunkSource().getGenerator().getTypeNameForDataFixer()),
                SharedConstants.getCurrentVersion().dataVersion().version());
        var data = SerializableChunkData.parse(level, level.palettedContainerFactory(), upgraded);
        // Land only partly generated has no surface worth showing yet
        if (data == null || !data.chunkStatus().isOrAfter(ChunkStatus.FULL))
            return unavailable(pending);

        var sections = new HashMap<Integer, LevelChunkSection>();
        for (var section : data.sectionData())
        {
            if (section.chunkSection() != null)
                sections.put(section.y(), section.chunkSection());
        }
        SAVED_ANSWERS.incrementAndGet();
        return encode(pending, sections::get);
    }

    /**
     * How many answers came from the save rather than loaded chunks, for tests.
     */
    public static int savedAnswers()
    {
        return SAVED_ANSWERS.get();
    }

    private static OculusChunkPayloads.Sections unavailable(Pending pending)
    {
        return new OculusChunkPayloads.Sections(ChunkPos.getX(pending.chunk()), ChunkPos.getZ(pending.chunk()), pending.windowBottom(), pending.windowHeight(), false, new byte[0]);
    }

    private static OculusChunkPayloads.Sections encode(Pending pending, IntFunction<@Nullable LevelChunkSection> sectionAt)
    {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            int minSection = SectionPos.blockToSectionCoord(pending.windowBottom());
            int maxSection = SectionPos.blockToSectionCoord(pending.windowBottom() + pending.windowHeight() - 1);
            for (int sectionY = minSection; sectionY <= maxSection; sectionY++)
            {
                var section = sectionAt.apply(sectionY);
                buffer.writeBoolean(section != null && !section.hasOnlyAir());
                if (section != null && !section.hasOnlyAir())
                    section.write(buffer);
            }
            var data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new OculusChunkPayloads.Sections(ChunkPos.getX(pending.chunk()), ChunkPos.getZ(pending.chunk()), pending.windowBottom(), pending.windowHeight(), true, data);
        } finally
        {
            buffer.release();
        }
    }

    public static void clear()
    {
        QUEUES.clear();
        SAVE_READS.clear();
    }
}
