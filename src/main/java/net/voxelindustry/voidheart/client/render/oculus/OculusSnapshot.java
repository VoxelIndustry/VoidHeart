package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Copy of the sections a chunk slice needs: its own and the facing sides of its four neighbors, which only decide
 * whether border faces are hidden. Taken on the main thread, read by a mesh builder.
 * Also stands in for the level when tint sources look up the biome of a block.
 */
final class OculusSnapshot implements BlockAndTintGetter
{
    private static final int CENTER = 0;
    private static final int[][] CHUNK_OFFSETS = { { 0, 0 }, { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };

    final int chunkX;
    final int chunkZ;
    final int windowBottom;
    final int windowHeight;

    private final int minSection;
    private final @Nullable LevelChunkSection[][] sections;
    private final Holder<Biome> fallbackBiome;
    private final int levelMinY;
    private final int levelHeight;

    private OculusSnapshot(int chunkX, int chunkZ, int windowBottom, int windowHeight, int minSection,
                           @Nullable LevelChunkSection[][] sections, Holder<Biome> fallbackBiome, int levelMinY, int levelHeight)
    {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.windowBottom = windowBottom;
        this.windowHeight = windowHeight;
        this.minSection = minSection;
        this.sections = sections;
        this.fallbackBiome = fallbackBiome;
        this.levelMinY = levelMinY;
        this.levelHeight = levelHeight;
    }

    /**
     * @return nothing while the chunk itself is not loaded on this client
     */
    static @Nullable OculusSnapshot capture(ClientLevel level, int chunkX, int chunkZ, int windowBottom, int windowHeight)
    {
        var source = level.getChunkSource();
        if (source.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null)
            return null;

        int minSection = SectionPos.blockToSectionCoord(windowBottom);
        int maxSection = SectionPos.blockToSectionCoord(windowBottom + windowHeight - 1);
        var sections = new LevelChunkSection[CHUNK_OFFSETS.length][maxSection - minSection + 1];

        for (int chunk = 0; chunk < CHUNK_OFFSETS.length; chunk++)
        {
            var levelChunk = source.getChunk(chunkX + CHUNK_OFFSETS[chunk][0], chunkZ + CHUNK_OFFSETS[chunk][1], ChunkStatus.FULL, false);
            if (levelChunk == null)
                continue;

            for (int sectionY = minSection; sectionY <= maxSection; sectionY++)
            {
                int index = level.getSectionIndexFromSectionY(sectionY);
                if (index < 0 || index >= levelChunk.getSectionsCount())
                    continue;

                var section = levelChunk.getSection(index);
                if (section.hasOnlyAir())
                    continue;

                var copy = section.copy();
                OculusPalette.ensure(copy.getStates());
                sections[chunk][sectionY - minSection] = copy;
            }
        }

        var center = new BlockPos((chunkX << 4) + 8, windowBottom + windowHeight / 2, (chunkZ << 4) + 8);
        return new OculusSnapshot(chunkX, chunkZ, windowBottom, windowHeight, minSection, sections, level.getBiome(center), level.getMinY(), level.getHeight());
    }

    /**
     * Turns the copied blocks into colored voxels. Neighbor voxels only matter for hiding faces, so they are not colored.
     */
    OculusMesher.Volume voxelize()
    {
        var volume = OculusMesher.Volume.empty(windowHeight);
        var pos = new BlockPos.MutableBlockPos();
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;

        for (int y = 0; y < windowHeight; y++)
        {
            for (int z = -1; z <= OculusMesher.CHUNK; z++)
            {
                boolean borderZ = z < 0 || z >= OculusMesher.CHUNK;
                for (int x = -1; x <= OculusMesher.CHUNK; x++)
                {
                    boolean borderX = x < 0 || x >= OculusMesher.CHUNK;
                    if (borderX && borderZ)
                        continue;

                    var state = stateAt(x, windowBottom + y, z);
                    var entry = OculusPalette.get(state);
                    if (!entry.filled())
                        continue;

                    volume.set(x, y, z, borderX || borderZ ? 1 : entry.colorAt(state, this, pos.set(originX + x, windowBottom + y, originZ + z)));
                }
            }
        }
        return volume;
    }

    private @Nullable LevelChunkSection sectionAt(int x, int y, int z)
    {
        int chunk = x < 0 ? 3 : x >= OculusMesher.CHUNK ? 4 : z < 0 ? 1 : z >= OculusMesher.CHUNK ? 2 : CENTER;
        int section = SectionPos.blockToSectionCoord(y) - minSection;
        if (section < 0 || section >= sections[chunk].length)
            return null;
        return sections[chunk][section];
    }

    private BlockState stateAt(int x, int y, int z)
    {
        var section = sectionAt(x, y, z);
        return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15);
    }

    /**
     * Whether a position falls in the chunk or a side neighbor. Diagonal neighbors are not copied.
     */
    private boolean holds(int x, int z)
    {
        if (x < -1 || x > OculusMesher.CHUNK || z < -1 || z > OculusMesher.CHUNK)
            return false;
        boolean borderX = x < 0 || x >= OculusMesher.CHUNK;
        boolean borderZ = z < 0 || z >= OculusMesher.CHUNK;
        return !(borderX && borderZ);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver)
    {
        int x = pos.getX() - (chunkX << 4);
        int z = pos.getZ() - (chunkZ << 4);
        var section = holds(x, z) ? sectionAt(x, pos.getY(), z) : null;
        var biome = section == null
                ? fallbackBiome
                : section.getNoiseBiome(QuartPos.fromBlock(x & 15), QuartPos.fromBlock(pos.getY() & 15), QuartPos.fromBlock(z & 15));
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        int x = pos.getX() - (chunkX << 4);
        int z = pos.getZ() - (chunkZ << 4);
        return holds(x, z) ? stateAt(x, pos.getY(), z) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public CardinalLighting cardinalLighting()
    {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public LevelLightEngine getLightEngine()
    {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getHeight()
    {
        return levelHeight;
    }

    @Override
    public int getMinY()
    {
        return levelMinY;
    }
}
