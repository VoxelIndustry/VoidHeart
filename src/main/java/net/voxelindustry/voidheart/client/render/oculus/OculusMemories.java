package net.voxelindustry.voidheart.client.render.oculus;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.voxelindustry.voidheart.common.content.crystal.CradleBalance;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusMemoryPayloads;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Places recorded by Deep memoriam that tables on this client show, asked of the server once and kept while shown. Their land never changes.
 */
final class OculusMemories
{
    private static final int ANSWER_TIMEOUT = 200;
    private static final int FORGET_AFTER   = 400;

    /**
     * @param sections per recorded chunk, its sections from {@link #minSection} up, missing when empty
     * @param covers   per recorded chunk, each column's cover height
     */
    record Memory(UUID id, boolean found, BlockPos center, int minSection, int sectionCount, int surface, List<CrystalRecordings.Creature> creatures,
                  Long2ObjectOpenHashMap<@Nullable LevelChunkSection[]> sections, Long2ObjectOpenHashMap<int[]> covers)
    {
        /**
         * Whether the recording holds this chunk, whose land may still be on its way.
         */
        boolean records(int chunkX, int chunkZ)
        {
            int radius = CradleBalance.RECORD_RADIUS;
            return chunkX >= SectionPos.blockToSectionCoord(center.getX() - radius) && chunkX <= SectionPos.blockToSectionCoord(center.getX() + radius)
                   && chunkZ >= SectionPos.blockToSectionCoord(center.getZ() - radius) && chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + radius);
        }

        /**
         * Whether a column lies within the radius recorded around the cradle.
         */
        boolean recordsColumn(int x, int z)
        {
            long dx = x - center.getX(), dz = z - center.getZ();
            return dx * dx + dz * dz <= (long) CradleBalance.RECORD_RADIUS * CradleBalance.RECORD_RADIUS;
        }

        int bottom()
        {
            return SectionPos.sectionToBlockCoord(minSection);
        }

        int top()
        {
            return SectionPos.sectionToBlockCoord(minSection + sectionCount);
        }
    }

    private static final Map<UUID, Memory> MEMORIES     = new HashMap<>();
    private static final Map<UUID, Long>   REQUESTED_AT = new HashMap<>();
    private static final Map<UUID, Long>   WANTED_AT    = new HashMap<>();

    private OculusMemories()
    {
    }

    /**
     * The recording, asking the server for it the first time.
     *
     * @return nothing until its header arrives
     */
    static @Nullable Memory get(UUID id, BlockPos table, long tick)
    {
        WANTED_AT.put(id, tick);
        var memory = MEMORIES.get(id);
        if (memory != null)
            return memory;
        var asked = REQUESTED_AT.get(id);
        if (asked == null || tick - asked > ANSWER_TIMEOUT)
        {
            REQUESTED_AT.put(id, tick);
            ClientPlayNetworking.send(new OculusMemoryPayloads.Request(id, table));
        }
        return null;
    }

    static void receiveHeader(OculusMemoryPayloads.Header payload, ClientPlayNetworking.Context context)
    {
        // A recording never spans more sections than its radius reaches, so anything else is malformed and allocates nothing
        if (payload.sectionCount() < 0 || payload.sectionCount() > OculusMemoryPayloads.MAX_SECTIONS)
            return;
        REQUESTED_AT.remove(payload.recording());
        MEMORIES.put(payload.recording(), new Memory(payload.recording(), payload.found(), payload.center(), payload.minSection(), payload.sectionCount(),
                payload.surface(), payload.creatures(), new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>()));
        OculusRenderer.onMemory(payload.recording());
    }

    static void receiveLand(OculusMemoryPayloads.Land payload, ClientPlayNetworking.Context context)
    {
        var level = context.client().level;
        var memory = MEMORIES.get(payload.recording());
        if (level == null || memory == null)
            return;

        var sections = new LevelChunkSection[memory.sectionCount()];
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
            // A malformed piece of land stays missing, and shows as nothing
            return;
        } finally
        {
            buffer.release();
        }
        long chunk = ChunkPos.pack(payload.chunkX(), payload.chunkZ());
        memory.sections().put(chunk, sections);
        if (payload.cover().size() == OculusChunkPayloads.COLUMNS)
            memory.covers().put(chunk, payload.cover().stream().mapToInt(Integer::intValue).toArray());
        OculusRenderer.onRemoteChunk(payload.chunkX(), payload.chunkZ());
    }

    /**
     * Forgets recordings no table has shown for a while.
     */
    static void tick(long tick)
    {
        WANTED_AT.entrySet().removeIf(entry ->
        {
            if (tick - entry.getValue() <= FORGET_AFTER)
                return false;
            MEMORIES.remove(entry.getKey());
            REQUESTED_AT.remove(entry.getKey());
            return true;
        });
    }

    static void clear()
    {
        MEMORIES.clear();
        REQUESTED_AT.clear();
        WANTED_AT.clear();
    }
}
