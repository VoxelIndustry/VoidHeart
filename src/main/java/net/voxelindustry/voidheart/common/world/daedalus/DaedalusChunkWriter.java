package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.UnderwayChunkWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Writes a chunk straight into its sections at the terrain step, like noise generation does. Nothing generated needs its neighbours, so a chunk costs no feature step, shape update or light beyond its few lamps.
 * The few altars and pillars get their block entities here too, as a feature would have given them, and barnacle nests are grown here since the spawn step may not set blocks.
 * The Underway takes the sections under the districts' floor, written by its own writer first. Its hatch lies in a district's floor and goes through the layer under it, down to its shaft.
 * Rails are joined to what stands beside them here, as placing them would, reading across the chunk's edge from a strip painted only when a rail reaches it.
 */
final class DaedalusChunkWriter
{
    private static final int SECTION = 16;
    private static final byte AIR = (byte) DaedalusMaterial.AIR.ordinal();
    private static final byte BOUND = (byte) DaedalusMaterial.BOUND.ordinal();
    private static final int HATCH = DaedalusMaterial.HATCH.ordinal();
    private static final int  CACHE = DaedalusMaterial.CACHE.ordinal();
    private static final ResourceKey<LootTable> CACHE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, "chests/daedalus_cache"));
    private static final ThreadLocal<DaedalusCanvas> CANVAS = ThreadLocal.withInitial(() -> new DaedalusCanvas(SECTION, SECTION));
    private static final ThreadLocal<DaedalusCanvas[]> BORDERS = ThreadLocal.withInitial(() -> new DaedalusCanvas[] {
            new DaedalusCanvas(SECTION, 1), new DaedalusCanvas(1, SECTION), new DaedalusCanvas(SECTION, 1), new DaedalusCanvas(1, SECTION) });
    private static final Direction[] FACING = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    /** The parts of a wall vanilla tests against the block above it, to decide its post and its tall sides. */
    private static final VoxelShape RAIL_POST_TEST = Block.column(2.0, 0.0, 16.0);
    private static final Map<Direction, VoxelShape> RAIL_SIDE_TESTS = Shapes.rotateHorizontal(Block.boxZ(2.0, 16.0, 0.0, 9.0));

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
        var neighbours = new Neighbours(worldSeed, canvas);
        byte[] cells = canvas.cells();
        UnderwayChunkWriter.write(chunk, worldSeed);
        var sections = chunk.getSections();
        int index = 0;
        for (int sectionIndex = UnderwayChunkWriter.SECTIONS; sectionIndex < sections.length; sectionIndex++)
        {
            var section = sections[sectionIndex];
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
                            int below = index - SECTION * SECTION;
                            int material = cells[index++] & 0xFF;
                            if (baseY + y == 0 && (cells[index - 1 + SECTION * SECTION] & 0xFF) == HATCH)
                                material = HATCH;
                            if (material == AIR)
                                continue;
                            var state = DaedalusPalette.isRail(material)
                                        ? rail(palette, neighbours.railSides(minX + x, baseY + y, minZ + z, palette), canvas, material, minX + x, baseY + y, minZ + z)
                                        : palette.state(material, below >= 0 ? cells[below] & 0xFF : AIR, minX + x, baseY + y, minZ + z);
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

    /**
     * A rail joined to the given sides, then shaped by what rests on it as a placed wall is: sides under a solid block stand tall, and the post rises as vanilla raises it.
     */
    private static BlockState rail(DaedalusPalette palette, int sides, DaedalusCanvas canvas, int material, int x, int y, int z)
    {
        var joined = palette.rail(sides);
        var above = palette.state(canvas.raw(x, y + 1, z), material, x, y + 1, z);
        if (above.isAir())
            return joined;
        var aboveShape = above.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).getFaceShape(Direction.DOWN);
        var state = joined;
        for (var direction : FACING)
        {
            var side = WallBlock.PROPERTY_BY_DIRECTION.get(direction);
            if (state.getValue(side) != WallSide.NONE && isCovered(aboveShape, RAIL_SIDE_TESTS.get(direction)))
                state = state.setValue(side, WallSide.TALL);
        }
        return state.setValue(WallBlock.UP, raisesPost(state, above, aboveShape));
    }

    private static boolean raisesPost(BlockState state, BlockState above, VoxelShape aboveShape)
    {
        if (above.getBlock() instanceof WallBlock && above.getValue(WallBlock.UP))
            return true;
        var north = state.getValue(WallBlock.NORTH);
        var east = state.getValue(WallBlock.EAST);
        var south = state.getValue(WallBlock.SOUTH);
        var west = state.getValue(WallBlock.WEST);
        boolean northNone = north == WallSide.NONE;
        boolean eastNone = east == WallSide.NONE;
        boolean southNone = south == WallSide.NONE;
        boolean westNone = west == WallSide.NONE;
        if (northNone && eastNone && southNone && westNone || northNone != southNone || eastNone != westNone)
            return true;
        if (north == WallSide.TALL && south == WallSide.TALL || east == WallSide.TALL && west == WallSide.TALL)
            return false;
        return above.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(aboveShape, RAIL_POST_TEST);
    }

    private static boolean isCovered(VoxelShape aboveShape, VoxelShape test)
    {
        return !Shapes.joinIsNotEmpty(test, aboveShape, BooleanOp.ONLY_FIRST);
    }

    /**
     * What stands round a chunk's rails, from its own canvas or, past its edge, from a strip of the neighbouring chunk painted the first time it is read.
     */
    private static final class Neighbours
    {
        private final long worldSeed;
        private final DaedalusCanvas canvas;
        private final DaedalusCanvas[] borders = BORDERS.get();
        private int painted;

        Neighbours(long worldSeed, DaedalusCanvas canvas)
        {
            this.worldSeed = worldSeed;
            this.canvas = canvas;
        }

        int railSides(int x, int y, int z, DaedalusPalette palette)
        {
            int sides = 0;
            for (int direction = 0; direction < Compass.COUNT; direction++)
            {
                int nx = x + Compass.dx(direction);
                int nz = z + Compass.dz(direction);
                boolean inside = nx >= canvas.minX() && nx <= canvas.maxX() && nz >= canvas.minZ() && nz <= canvas.maxZ();
                int material = inside ? canvas.raw(nx, y, nz) : border(direction).raw(nx, y, nz);
                if (DaedalusPalette.isRail(material) || joins((inside ? palette : paletteAt(nx, nz)).state(material, nx, y, nz), FACING[direction].getOpposite()))
                    sides |= 1 << direction;
            }
            return sides;
        }

        private DaedalusCanvas border(int direction)
        {
            var border = borders[direction];
            if ((painted & 1 << direction) == 0)
            {
                int x = direction == Compass.EAST ? canvas.maxX() + 1 : direction == Compass.WEST ? canvas.minX() - 1 : canvas.minX();
                int z = direction == Compass.SOUTH ? canvas.maxZ() + 1 : direction == Compass.NORTH ? canvas.minZ() - 1 : canvas.minZ();
                border.reset(x, z);
                DistrictPlans.paint(worldSeed, border);
                painted |= 1 << direction;
            }
            return border;
        }

        private static DaedalusPalette paletteAt(int x, int z)
        {
            return DistrictPos.isInsideDistrict(x, z) ? DaedalusPalette.of(DaedalusTheme.at(DistrictPos.at(x, z))) : DaedalusPalette.chasm();
        }

        /**
         * The rule walls place by: other walls, bars, and any sturdy face that isn't one of the few blocks walls shun.
         */
        private static boolean joins(BlockState state, Direction face)
        {
            return state.is(BlockTags.WALLS) || state.getBlock() instanceof IronBarsBlock
                   || !Block.isExceptionForConnection(state) && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
        }
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
