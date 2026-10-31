package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight;
import org.jspecify.annotations.Nullable;

/**
 * Copy of the sections a chunk slice needs: its own and the facing sides of its four neighbors, which only decide
 * whether border faces are hidden. Taken on the main thread, read by a mesh builder.
 * Columns the table does not see hold the generated relief instead of their blocks. In the columns it sees, the open
 * space under cover, caves and rooms alike, is filled with hollow voxels drawn when the hologram shows the hollows.
 * Also stands in for the level when tint sources look up the biome of a block.
 */
final class OculusSnapshot implements BlockAndTintGetter
{
    private static final int CENTER = 0;
    private static final int[][] CHUNK_OFFSETS = { { 0, 0 }, { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
    private static final int SIDE = OculusMesher.Volume.SIDE;
    private static final int NO_RELIEF = Integer.MIN_VALUE;

    /**
     * Relief voxels are told apart by the shader through their alpha.
     */
    static final int RELIEF_ALPHA = 0x80;
    private static final int RELIEF_LOW = 0x24183D;
    private static final int RELIEF_HIGH = 0x8676B8;
    private static final int RELIEF_CONTOUR = 0xC2B0FF;
    private static final int HOLLOW_DEEP = 0x1C4F6E;
    private static final int HOLLOW_HIGH = 0x9EE6F2;

    final int chunkX;
    final int chunkZ;
    final int windowBottom;
    final int windowHeight;

    private final int minSection;
    private final @Nullable LevelChunkSection[][] sections;
    private final Holder<Biome> fallbackBiome;
    private final int levelMinY;
    private final int levelHeight;
    private final int seaLevel;
    /**
     * Per column of the slice and its border, whether the table sees its land.
     */
    private final boolean[] seen;
    /**
     * Per column, the first air block above the generated surface, for the columns not seen.
     */
    private final int[] relief;
    /**
     * Per column, the first open block above everything covering it: open space below is hollow.
     */
    private final int[] cover;

    private OculusSnapshot(int chunkX, int chunkZ, int windowBottom, int windowHeight, int minSection,
                           @Nullable LevelChunkSection[][] sections, Holder<Biome> fallbackBiome, int levelMinY, int levelHeight,
                           int seaLevel, boolean[] seen, int[] relief, int[] cover)
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
        this.seaLevel = seaLevel;
        this.seen = seen;
        this.relief = relief;
        this.cover = cover;
    }

    private static int column(int x, int z)
    {
        return x + 1 + (z + 1) * SIDE;
    }

    private static int chunkOf(int x, int z)
    {
        return x < 0 ? 3 : x >= OculusMesher.CHUNK ? 4 : z < 0 ? 1 : z >= OculusMesher.CHUNK ? 2 : CENTER;
    }

    private static boolean isCorner(int x, int z)
    {
        return (x < 0 || x >= OculusMesher.CHUNK) && (z < 0 || z >= OculusMesher.CHUNK);
    }

    /**
     * Copies loaded chunks, and falls back on sections the server sent for chunks this client has not loaded.
     * Missing land or relief is asked for.
     *
     * @return nothing while the land or relief this slice shows has not arrived yet
     */
    static @Nullable OculusSnapshot capture(ClientLevel level, int chunkX, int chunkZ, int windowBottom, int windowHeight, long tick,
                                            OculusSight sight, BlockPos table)
    {
        var source = level.getChunkSource();
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        long centerChunk = ChunkPos.pack(chunkX, chunkZ);

        // Land the server could not find, never generated, is only known by its relief
        boolean lost = source.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null
                && OculusRemoteChunks.isUnavailable(centerChunk, windowBottom, windowHeight);
        var seen = new boolean[SIDE * SIDE];
        var needed = new boolean[CHUNK_OFFSETS.length];
        var reliefWanted = new boolean[CHUNK_OFFSETS.length];
        for (int z = -1; z <= OculusMesher.CHUNK; z++)
        {
            for (int x = -1; x <= OculusMesher.CHUNK; x++)
            {
                if (isCorner(x, z))
                    continue;
                int chunk = chunkOf(x, z);
                boolean sees = !(lost && chunk == CENTER) && sight.seesColumn(originX + x, originZ + z);
                seen[column(x, z)] = sees;
                needed[chunk] |= sees;
                reliefWanted[chunk] |= !sees;
            }
        }

        var grids = new int[CHUNK_OFFSETS.length][];
        for (int chunk = 0; chunk < CHUNK_OFFSETS.length; chunk++)
        {
            if (!reliefWanted[chunk])
                continue;
            long pos = ChunkPos.pack(chunkX + CHUNK_OFFSETS[chunk][0], chunkZ + CHUNK_OFFSETS[chunk][1]);
            grids[chunk] = OculusRelief.get(pos, tick);
            if (grids[chunk] == null)
            {
                OculusRelief.want(table, pos, tick);
                // Without its neighbors' relief, a slice only shows a few more faces along its edges until it arrives
                if (chunk == CENTER)
                    return null;
            }
        }

        int minSection = SectionPos.blockToSectionCoord(windowBottom);
        int maxSection = SectionPos.blockToSectionCoord(windowBottom + windowHeight - 1);
        var sections = new LevelChunkSection[CHUNK_OFFSETS.length][maxSection - minSection + 1];
        var covers = new int[CHUNK_OFFSETS.length][];

        for (int chunk = 0; chunk < CHUNK_OFFSETS.length; chunk++)
        {
            if (!needed[chunk])
                continue;
            int x = chunkX + CHUNK_OFFSETS[chunk][0];
            int z = chunkZ + CHUNK_OFFSETS[chunk][1];
            var levelChunk = source.getChunk(x, z, ChunkStatus.FULL, false);
            if (levelChunk == null)
            {
                var remote = OculusRemoteChunks.sections(ChunkPos.pack(x, z), windowBottom, windowHeight, tick);
                if (remote == null)
                {
                    if (chunk == CENTER)
                    {
                        OculusRemoteChunks.want(table, centerChunk, windowBottom, windowHeight, tick);
                        return null;
                    }
                    continue;
                }
                // Remote sections are never written once decoded, so builders can share them
                for (int i = 0; i < remote.length; i++)
                {
                    if (remote[i] != null)
                        OculusPalette.ensure(remote[i].getStates());
                    sections[chunk][i] = remote[i];
                }
                covers[chunk] = OculusRemoteChunks.cover(ChunkPos.pack(x, z));
                continue;
            }

            covers[chunk] = new int[OculusChunkPayloads.COLUMNS];
            for (int columnZ = 0; columnZ < 16; columnZ++)
            {
                for (int columnX = 0; columnX < 16; columnX++)
                    covers[chunk][columnX + columnZ * 16] = levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnX, columnZ) + 1;
            }

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

        var relief = new int[SIDE * SIDE];
        var cover = new int[SIDE * SIDE];
        for (int z = -1; z <= OculusMesher.CHUNK; z++)
        {
            for (int x = -1; x <= OculusMesher.CHUNK; x++)
            {
                if (isCorner(x, z))
                    continue;
                var grid = grids[chunkOf(x, z)];
                relief[column(x, z)] = grid == null ? NO_RELIEF : OculusRelief.height(grid, x & 15, z & 15);
                var covered = covers[chunkOf(x, z)];
                cover[column(x, z)] = covered == null ? NO_RELIEF : covered[(x & 15) + (z & 15) * 16];
            }
        }

        var center = new BlockPos(originX + 8, windowBottom + windowHeight / 2, originZ + 8);
        return new OculusSnapshot(chunkX, chunkZ, windowBottom, windowHeight, minSection, sections, level.getBiome(center), level.getMinY(), level.getHeight(),
                level.getSeaLevel(), seen, relief, cover);
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

                    int column = column(x, z);
                    if (!seen[column])
                    {
                        if (windowBottom + y < relief[column])
                            volume.set(x, y, z, borderX || borderZ ? 1 : reliefColor(windowBottom + y, seaLevel));
                        continue;
                    }

                    var state = stateAt(x, windowBottom + y, z);
                    var entry = OculusPalette.get(state);
                    if (!entry.filled())
                    {
                        if (windowBottom + y < cover[column])
                            volume.set(x, y, z, borderX || borderZ ? OculusMesher.HOLLOW_BORDER : hollowColor(windowBottom + y, seaLevel));
                        continue;
                    }

                    volume.set(x, y, z, borderX || borderZ ? 1 : entry.colorAt(state, this, pos.set(originX + x, windowBottom + y, originZ + z)));
                }
            }
        }
        return volume;
    }

    /**
     * Strata of muted violet, paler with height, with a brighter contour every sixteen blocks.
     */
    static int reliefColor(int y, int seaLevel)
    {
        float height = Mth.clamp((y - seaLevel + 24) / 112F, 0, 1);
        int color = ARGB.srgbLerp(height, RELIEF_LOW, RELIEF_HIGH);
        if (Math.floorMod(y, 16) == 15)
            color = ARGB.srgbLerp(0.45F, color, RELIEF_CONTOUR);
        else if (Math.floorMod(y, 8) >= 4)
            color = ARGB.scaleRGB(color, 0.9F);
        return ARGB.color(RELIEF_ALPHA, color);
    }

    /**
     * Pale where the hollows are high, deepening toward the bottom of the world, banded every eight blocks.
     */
    static int hollowColor(int y, int seaLevel)
    {
        float height = Mth.clamp((y - seaLevel + 64) / 128F, 0, 1);
        int color = ARGB.srgbLerp(height, HOLLOW_DEEP, HOLLOW_HIGH);
        if (Math.floorMod(y, 8) >= 4)
            color = ARGB.scaleRGB(color, 0.88F);
        return ARGB.color(OculusMesher.HOLLOW_ALPHA, color);
    }

    private @Nullable LevelChunkSection sectionAt(int x, int y, int z)
    {
        int chunk = chunkOf(x, z);
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
