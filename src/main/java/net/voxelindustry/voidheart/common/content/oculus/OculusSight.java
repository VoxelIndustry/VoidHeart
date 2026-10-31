package net.voxelindustry.voidheart.common.content.oculus;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.beholder.BeholderRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * What a table can make out of the land it shows: around itself dimly, and around the beholders of its owners.
 * Elsewhere it only knows the lay of the land as the world was first shaped.
 */
public record OculusSight(List<Source> sources)
{
    public static final int MAX_SOURCES = 128;

    public static final StreamCodec<ByteBuf, OculusSight> STREAM_CODEC = Source.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SOURCES))
            .map(OculusSight::new, OculusSight::sources);

    public enum Resolution
    {
        /**
         * Only the elevation of the land is known.
         */
        UNSEEN,
        /**
         * The land and the presence of entities, but not who or what they are.
         */
        LOW,
        /**
         * Player names and mob types too.
         */
        HIGH
    }

    /**
     * A point seeing the land around it, measured horizontally from its block center.
     *
     * @param beholder whether it is a beholder rather than the table itself
     */
    public record Source(BlockPos pos, int lowRange, int highRange, boolean beholder)
    {
        public static final StreamCodec<ByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Source::pos,
                ByteBufCodecs.VAR_INT, Source::lowRange,
                ByteBufCodecs.VAR_INT, Source::highRange,
                ByteBufCodecs.BOOL, Source::beholder,
                Source::new);

        double distanceSq(double x, double z)
        {
            double dx = x - (pos.getX() + 0.5);
            double dz = z - (pos.getZ() + 0.5);
            return dx * dx + dz * dz;
        }

        /**
         * Whether any column of this chunk lies within the low range.
         */
        boolean touchesChunk(int chunkX, int chunkZ)
        {
            double minX = SectionPos.sectionToBlockCoord(chunkX);
            double minZ = SectionPos.sectionToBlockCoord(chunkZ);
            double nearestX = Math.clamp(pos.getX() + 0.5, minX + 0.5, minX + 15.5);
            double nearestZ = Math.clamp(pos.getZ() + 0.5, minZ + 0.5, minZ + 15.5);
            return distanceSq(nearestX, nearestZ) <= (double) lowRange * lowRange;
        }
    }

    public Resolution resolutionAt(double x, double z)
    {
        var best = Resolution.UNSEEN;
        for (var source : sources)
        {
            double distanceSq = source.distanceSq(x, z);
            if (source.highRange() > 0 && distanceSq <= (double) source.highRange() * source.highRange())
                return Resolution.HIGH;
            if (source.lowRange() > 0 && distanceSq <= (double) source.lowRange() * source.lowRange())
                best = Resolution.LOW;
        }
        return best;
    }

    /**
     * Whether the land of a block column is seen, by its block coordinates.
     */
    public boolean seesColumn(int x, int z)
    {
        return resolutionAt(x + 0.5, z + 0.5) != Resolution.UNSEEN;
    }

    public boolean touchesChunk(int chunkX, int chunkZ)
    {
        return sources.stream().anyMatch(source -> source.touchesChunk(chunkX, chunkZ));
    }

    /**
     * The table sees around its center, and its owners' beholders in the same dimension see for it.
     */
    public static OculusSight of(ServerLevel level, BlockPos table)
    {
        var footprint = OculusTableStructure.footprint(level, table);
        if (footprint.isEmpty())
            return new OculusSight(List.of());

        var config = VoidHeartConfig.get();
        var sources = new ArrayList<Source>();
        var center = BlockPos.containing(footprint.get().centerX(), footprint.get().y(), footprint.get().centerZ());
        if (config.oculusTableSightRange > 0)
            sources.add(new Source(center, config.oculusTableSightRange, 0, false));

        var owners = OculusTableStructure.owners(level, table);
        for (var beholder : BeholderRegistry.get(level).ownedBy(owners))
        {
            if (sources.size() >= MAX_SOURCES)
                break;
            sources.add(new Source(beholder, config.beholderLowRange, config.beholderHighRange, true));
        }
        return new OculusSight(sources);
    }
}
