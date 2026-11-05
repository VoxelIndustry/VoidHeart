package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Writes a chunk straight into its sections at the terrain step, like noise generation does. Nothing generated has a block entity or needs its neighbours, so a chunk costs no feature step, shape update or light beyond its few lamps.
 */
final class DaedalusChunkWriter
{
    private static final int SECTION = 16;
    private static final byte AIR = (byte) DaedalusMaterial.AIR.ordinal();
    private static final byte BOUND = (byte) DaedalusMaterial.BOUND.ordinal();
    private static final ThreadLocal<DaedalusCanvas> CANVAS = ThreadLocal.withInitial(() -> new DaedalusCanvas(SECTION, SECTION));

    private DaedalusChunkWriter()
    {
    }

    static void write(ChunkAccess chunk, long worldSeed)
    {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        var canvas = CANVAS.get();
        canvas.reset(minX, minZ);
        DistrictPlans.paint(worldSeed, canvas);

        var palette = DistrictPos.isInsideDistrict(minX, minZ)
                      ? DaedalusPalette.of(DaedalusTheme.at(DistrictPos.at(minX, minZ)))
                      : DaedalusPalette.chasm();
        byte[] cells = canvas.cells();
        int index = 0;
        for (var section : chunk.getSections())
        {
            int baseY = index / (SECTION * SECTION);
            section.acquire();
            try
            {
                for (int y = 0; y < SECTION; y++)
                {
                    for (int z = 0; z < SECTION; z++)
                    {
                        for (int x = 0; x < SECTION; x++)
                        {
                            byte material = cells[index++];
                            if (material != AIR)
                                section.setBlockState(x, y, z, palette.state(material, minX + x, baseY + y, minZ + z), false);
                        }
                    }
                }
            } finally
            {
                section.release();
            }
        }
        primeHeightmaps(chunk, palette);
    }

    /**
     * The ceiling is the top of every column, so these need no scan.
     */
    private static void primeHeightmaps(ChunkAccess chunk, DaedalusPalette palette)
    {
        var ceiling = palette.state(BOUND, 0, 0, 0);
        var oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int z = 0; z < SECTION; z++)
        {
            for (int x = 0; x < SECTION; x++)
            {
                oceanFloor.update(x, DistrictPos.TOP_Y, z, ceiling);
                worldSurface.update(x, DistrictPos.TOP_Y, z, ceiling);
            }
        }
    }
}
