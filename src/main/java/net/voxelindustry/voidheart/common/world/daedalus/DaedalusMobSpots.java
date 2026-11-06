package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.Set;
import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMaterial.*;

/**
 * Finds where in its cell a planned mob stands, on a painted canvas: clear floor under furniture-free headroom, open air for flyers, or a wall, floor or ceiling for a nest.
 * A spot never leaves its cell, so the chunk holding the cell is all that needs painting.
 */
public final class DaedalusMobSpots
{
    /**
     * As {@code Direction} orders them: down, up, north, south, west, east.
     */
    private static final int[][] SIDES = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
    private static final int AIR_ATTEMPTS = 40;
    private static final int SURFACE_ATTEMPTS = 60;
    private static final int COLUMNS = DistrictPos.CELL * DistrictPos.CELL;

    /**
     * @param surface the {@code Direction} ordinal a nest faces out of its surface, or 1 (up) for mobs
     */
    public record Spot(int x, int y, int z, int surface)
    {
    }

    private DaedalusMobSpots()
    {
    }

    /**
     * @param taken spots already given in the same cell, which this adds to
     * @return null if the cell has no room for it
     */
    public static Spot find(DaedalusCanvas canvas, DistrictPos pos, DaedalusMobPlan.Placement placement, Set<Long> taken)
    {
        int minX = pos.originX() + placement.i() * DistrictPos.CELL;
        int minZ = pos.originZ() + placement.j() * DistrictPos.CELL;
        int floorY = DistrictPos.floorY(placement.k());
        var random = new SplittableRandom(placement.salt());
        return switch (placement.mob().placing)
        {
            case FLOOR -> onFloor(canvas, minX, floorY, minZ, placement, random, taken);
            case AIR -> inAir(canvas, minX, floorY, minZ, random, taken);
            case SURFACE -> onSurface(canvas, minX, floorY, minZ, random, taken);
        };
    }

    private static Spot onFloor(DaedalusCanvas canvas, int minX, int floorY, int minZ, DaedalusMobPlan.Placement placement, SplittableRandom random, Set<Long> taken)
    {
        var mob = placement.mob();
        int[] order = columnOrder(placement, random);
        for (int halfWidth = mob.halfWidth; halfWidth >= mob.tightHalfWidth; halfWidth--)
        {
            for (int column : order)
            {
                int localX = column % DistrictPos.CELL;
                int localZ = column / DistrictPos.CELL;
                if (localX - halfWidth < 0 || localX + halfWidth >= DistrictPos.CELL || localZ - halfWidth < 0 || localZ + halfWidth >= DistrictPos.CELL)
                    continue;

                int x = minX + localX;
                int z = minZ + localZ;
                for (int y = floorY + 1; y < floorY + DistrictPos.CELL; y++)
                {
                    if (canStand(canvas, x, y, z, halfWidth, mob.height) && claim(taken, x, y, z, halfWidth))
                        return new Spot(x, y, z, 1);
                }
            }
        }
        return null;
    }

    /**
     * Nearest first to where it would rather stand, or in a random order.
     */
    private static int[] columnOrder(DaedalusMobPlan.Placement placement, SplittableRandom random)
    {
        int[] order = new int[COLUMNS];
        for (int index = 0; index < COLUMNS; index++)
            order[index] = index;
        if (placement.localX() == DaedalusMobPlan.ANYWHERE)
        {
            for (int index = COLUMNS - 1; index > 0; index--)
            {
                int other = random.nextInt(index + 1);
                int swap = order[index];
                order[index] = order[other];
                order[other] = swap;
            }
            return order;
        }

        int wantedX = placement.localX();
        int wantedZ = placement.localZ();
        return java.util.Arrays.stream(order).boxed()
                .sorted(java.util.Comparator.comparingInt(column -> square(column % DistrictPos.CELL - wantedX) + square(column / DistrictPos.CELL - wantedZ)))
                .mapToInt(Integer::intValue)
                .toArray();
    }

    static boolean canStand(DaedalusCanvas canvas, int x, int y, int z, int halfWidth, int height)
    {
        for (int dz = -halfWidth; dz <= halfWidth; dz++)
        {
            for (int dx = -halfWidth; dx <= halfWidth; dx++)
            {
                if (!isGround(canvas.get(x + dx, y - 1, z + dz)))
                    return false;
                for (int up = 0; up < height; up++)
                {
                    var body = canvas.get(x + dx, y + up, z + dz);
                    if (body != AIR && !(up == 0 && body == CARPET))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean isGround(DaedalusMaterial material)
    {
        return material == FLOOR || material == FLOOR_TRIM || material == FLOOR_INLAY || material == FILL;
    }

    private static Spot inAir(DaedalusCanvas canvas, int minX, int floorY, int minZ, SplittableRandom random, Set<Long> taken)
    {
        for (int attempt = 0; attempt < AIR_ATTEMPTS; attempt++)
        {
            int x = minX + 1 + random.nextInt(DistrictPos.CELL - 2);
            int y = floorY + 2 + random.nextInt(DistrictPos.CELL - 3);
            int z = minZ + 1 + random.nextInt(DistrictPos.CELL - 2);
            if (isOpenAround(canvas, x, y, z) && taken.add(pack(x, y, z)))
                return new Spot(x, y, z, 1);
        }
        return null;
    }

    private static boolean isOpenAround(DaedalusCanvas canvas, int x, int y, int z)
    {
        for (int dy = -1; dy <= 1; dy++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                for (int dx = -1; dx <= 1; dx++)
                {
                    if (canvas.get(x + dx, y + dy, z + dz) != AIR)
                        return false;
                }
            }
        }
        return true;
    }

    private static Spot onSurface(DaedalusCanvas canvas, int minX, int floorY, int minZ, SplittableRandom random, Set<Long> taken)
    {
        for (int attempt = 0; attempt < SURFACE_ATTEMPTS; attempt++)
        {
            int x = minX + random.nextInt(DistrictPos.CELL);
            int y = floorY + 1 + random.nextInt(DistrictPos.CELL - 1);
            int z = minZ + random.nextInt(DistrictPos.CELL);
            int side = random.nextInt(SIDES.length);
            int[] step = SIDES[side];
            if (canvas.get(x, y, z) == AIR && isAnchor(canvas.get(x - step[0], y - step[1], z - step[2])) && !touchesWater(canvas, x, y, z) && taken.add(pack(x, y, z)))
                return new Spot(x, y, z, side);
        }
        return null;
    }

    private static boolean isAnchor(DaedalusMaterial material)
    {
        return material == WALL || material == PANEL || material == FLOOR || material == CEILING || material == FILL;
    }

    private static boolean touchesWater(DaedalusCanvas canvas, int x, int y, int z)
    {
        for (int[] step : SIDES)
        {
            if (canvas.get(x + step[0], y + step[1], z + step[2]) == WATER)
                return true;
        }
        return false;
    }

    private static boolean claim(Set<Long> taken, int x, int y, int z, int halfWidth)
    {
        for (int dz = -halfWidth; dz <= halfWidth; dz++)
        {
            for (int dx = -halfWidth; dx <= halfWidth; dx++)
            {
                if (taken.contains(pack(x + dx, y, z + dz)))
                    return false;
            }
        }
        for (int dz = -halfWidth; dz <= halfWidth; dz++)
        {
            for (int dx = -halfWidth; dx <= halfWidth; dx++)
                taken.add(pack(x + dx, y, z + dz));
        }
        return true;
    }

    private static long pack(int x, int y, int z)
    {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | ((long) z & 0x3FFFFFF);
    }

    private static int square(int value)
    {
        return value * value;
    }
}
