package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Writes a chunk straight into its sections at the terrain step, like noise generation does. Nothing generated needs its neighbours, so a chunk costs no feature step, shape update or light beyond its few lamps.
 * The few altars and pillars get their block entities here too, as a feature would have given them, and barnacle nests are grown here since the spawn step may not set blocks.
 */
final class DaedalusChunkWriter
{
    private static final int SECTION = 16;
    private static final byte AIR = (byte) DaedalusMaterial.AIR.ordinal();
    private static final byte BOUND = (byte) DaedalusMaterial.BOUND.ordinal();
    private static final int  CACHE = DaedalusMaterial.CACHE.ordinal();
    private static final ResourceKey<LootTable> CACHE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, "chests/daedalus_cache"));
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
                            int material = cells[index++] & 0xFF;
                            if (material == AIR)
                                continue;
                            var state = palette.state(material, minX + x, baseY + y, minZ + z);
                            section.setBlockState(x, y, z, state, false);
                            if (state.hasBlockEntity())
                            {
                                var pos = new BlockPos(minX + x, baseY + y, minZ + z);
                                addBlockEntity(chunk, pos, state);
                                if (material == CACHE && chunk.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity cache)
                                    cache.setLootTable(CACHE_LOOT, worldSeed ^ pos.asLong());
                            }
                        }
                    }
                }
            } finally
            {
                section.release();
            }
        }
        primeHeightmaps(chunk, palette);
        if (DistrictPos.isInsideDistrict(minX, minZ))
            growNests(chunk, canvas, DistrictPlans.get(worldSeed, DistrictPos.at(minX, minZ)));
    }

    private static void growNests(ChunkAccess chunk, DaedalusCanvas canvas, DistrictPlan plan)
    {
        var taken = new HashMap<Integer, Set<Long>>();
        for (var placement : plan.mobs().inChunk(chunk.getPos().x(), chunk.getPos().z()))
        {
            if (placement.mob() != DaedalusMob.BARNACLE_NEST)
                continue;
            var spot = DaedalusMobSpots.find(canvas, plan.pos, placement, taken.computeIfAbsent(DistrictPlan.index(placement.i(), placement.k(), placement.j()), key -> new HashSet<>()));
            if (spot == null)
                continue;
            var pos = new BlockPos(spot.x(), spot.y(), spot.z());
            var state = VoidHeartBlocks.BARNACLE_NEST.defaultBlockState().setValue(BarnacleNestBlock.FACING, Direction.from3DDataValue(spot.surface()));
            chunk.setBlockState(pos, state, 0);
            addBlockEntity(chunk, pos, state);
        }
    }

    private static void addBlockEntity(ChunkAccess chunk, BlockPos pos, BlockState state)
    {
        if (state.getBlock() instanceof EntityBlock block)
        {
            var entity = block.newBlockEntity(pos, state);
            if (entity != null)
                chunk.setBlockEntity(entity);
        }
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
