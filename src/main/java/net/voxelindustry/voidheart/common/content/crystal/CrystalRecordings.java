package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The places Deep memoriam recorded when it finished growing: the land within {@link CradleBalance#RECORD_RADIUS} blocks of its cradle, and the creatures
 * there at that moment. Kept on the server, in the overworld's saved data, and sent to an Oculus Table's viewers when it shows one. The bundle only
 * carries a reference.
 * <p>
 * Land is kept per chunk as the sections the Oculus sends for land a client lacks: a presence flag per section from the lowest up, then each section as
 * vanilla writes it, with each column's cover height.
 */
public final class CrystalRecordings extends SavedData
{
    /** The most creatures a recording keeps, nearest first. */
    public static final int MAX_CREATURES = 256;

    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer ->
    {
        var bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }, ByteBuffer::wrap);

    /**
     * What a Deep memoriam bundle carries: which recording, and where it was made.
     */
    public record Ref(UUID id, ResourceKey<Level> dimension)
    {
        public static final Codec<Ref> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Ref::id),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Ref::dimension)
        ).apply(instance, Ref::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Ref> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Ref::id,
                ResourceKey.streamCodec(Registries.DIMENSION), Ref::dimension,
                Ref::new);
    }

    public record Creature(EntityType<?> type, double x, double y, double z)
    {
        public static final Codec<Creature> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(Creature::type),
                Codec.DOUBLE.fieldOf("x").forGetter(Creature::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Creature::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Creature::z)
        ).apply(instance, Creature::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Creature> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ENTITY_TYPE), Creature::type,
                ByteBufCodecs.DOUBLE, Creature::x,
                ByteBufCodecs.DOUBLE, Creature::y,
                ByteBufCodecs.DOUBLE, Creature::z,
                Creature::new);
    }

    /**
     * @param data  per section from the recording's lowest up, a presence flag and the section as vanilla writes it
     * @param cover per column, the first open block above what covers it
     */
    public record Chunk(int x, int z, byte[] data, List<Integer> cover)
    {
        public static final Codec<Chunk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Chunk::x),
                Codec.INT.fieldOf("z").forGetter(Chunk::z),
                BYTES.fieldOf("data").forGetter(Chunk::data),
                Codec.INT.listOf().fieldOf("cover").forGetter(Chunk::cover)
        ).apply(instance, Chunk::new));
    }

    /**
     * @param minSection   the lowest section kept
     * @param sectionCount how many sections each chunk keeps, from the lowest up
     * @param surface      the median ground height around the cradle, where a table rests its window
     */
    public record Recording(ResourceKey<Level> dimension, BlockPos center, int minSection, int sectionCount, int surface, List<Chunk> chunks,
                            List<Creature> creatures)
    {
        public static final Codec<Recording> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Recording::dimension),
                BlockPos.CODEC.fieldOf("center").forGetter(Recording::center),
                Codec.INT.fieldOf("min_section").forGetter(Recording::minSection),
                Codec.INT.fieldOf("section_count").forGetter(Recording::sectionCount),
                Codec.INT.fieldOf("surface").forGetter(Recording::surface),
                Chunk.CODEC.listOf().fieldOf("chunks").forGetter(Recording::chunks),
                Creature.CODEC.listOf().fieldOf("creatures").forGetter(Recording::creatures)
        ).apply(instance, Recording::new));
    }

    private record Entry(UUID id, Recording recording)
    {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Recording.CODEC.fieldOf("recording").forGetter(Entry::recording)
        ).apply(instance, Entry::new));
    }

    public static final Codec<CrystalRecordings> CODEC = Entry.CODEC.listOf().fieldOf("recordings").codec()
            .xmap(CrystalRecordings::new, CrystalRecordings::entries);

    public static final SavedDataType<CrystalRecordings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "crystal_recordings"),
            CrystalRecordings::new,
            CODEC,
            null);

    private final Map<UUID, Recording> recordings = new HashMap<>();

    public CrystalRecordings()
    {
    }

    private CrystalRecordings(List<Entry> entries)
    {
        entries.forEach(entry -> recordings.put(entry.id(), entry.recording()));
    }

    private List<Entry> entries()
    {
        return recordings.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    public static CrystalRecordings get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static @Nullable Recording find(MinecraftServer server, UUID id)
    {
        return get(server).recordings.get(id);
    }

    public static void forget(MinecraftServer server, Ref ref)
    {
        var all = get(server);
        if (all.recordings.remove(ref.id()) != null)
            all.setDirty();
    }

    /**
     * Records the land within the radius of a position, every way, and the creatures there now. Chunks not loaded are loaded for it, once.
     */
    public static Ref record(ServerLevel level, BlockPos center)
    {
        int radius = CradleBalance.RECORD_RADIUS;
        int minSection = Math.max(level.getMinSectionY(), SectionPos.blockToSectionCoord(center.getY() - radius));
        int maxSection = Math.min(level.getMaxSectionY(), SectionPos.blockToSectionCoord(center.getY() + radius));
        int sectionCount = maxSection - minSection + 1;

        var chunks = new ArrayList<Chunk>();
        var surfaces = new IntArrayList();
        int fromX = SectionPos.blockToSectionCoord(center.getX() - radius), toX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int fromZ = SectionPos.blockToSectionCoord(center.getZ() - radius), toZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int chunkX = fromX; chunkX <= toX; chunkX++)
        {
            for (int chunkZ = fromZ; chunkZ <= toZ; chunkZ++)
            {
                var chunk = level.getChunk(chunkX, chunkZ);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try
                {
                    for (int sectionY = minSection; sectionY <= maxSection; sectionY++)
                    {
                        var section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
                        boolean present = !section.hasOnlyAir();
                        buffer.writeBoolean(present);
                        if (present)
                            section.write(buffer);
                    }
                    var data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);

                    var cover = new ArrayList<Integer>(256);
                    for (int z = 0; z < 16; z++)
                    {
                        for (int x = 0; x < 16; x++)
                        {
                            int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                            cover.add(height);
                            int worldX = (chunkX << 4) + x, worldZ = (chunkZ << 4) + z;
                            long dx = worldX - center.getX(), dz = worldZ - center.getZ();
                            if (dx * dx + dz * dz <= (long) radius * radius && height > center.getY() - radius && height <= center.getY() + radius)
                                surfaces.add(height);
                        }
                    }
                    chunks.add(new Chunk(chunkX, chunkZ, data, cover));
                } finally
                {
                    buffer.release();
                }
            }
        }
        surfaces.sort(null);
        int surface = surfaces.isEmpty() ? center.getY() : surfaces.getInt(surfaces.size() / 2);

        var box = new AABB(center).inflate(radius);
        var creatures = level.getEntitiesOfClass(LivingEntity.class, box, entity -> !(entity instanceof Player) && entity.isAlive()
                        && entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center)) <= (double) radius * radius).stream()
                .sorted(java.util.Comparator.comparingDouble(entity -> entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center))))
                .limit(MAX_CREATURES)
                .map(entity -> new Creature(entity.getType(), entity.getX(), entity.getY(), entity.getZ()))
                .toList();

        var id = UUID.randomUUID();
        var all = get(level.getServer());
        all.recordings.put(id, new Recording(level.dimension(), center.immutable(), minSection, sectionCount, surface, chunks, creatures));
        all.setDirty();
        return new Ref(id, level.dimension());
    }
}
