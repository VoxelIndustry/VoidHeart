package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

/**
 * Where the Underway's trunk lines, stations, gates and galleries fall under the districts. Everything comes from the seed and a district's position alone.
 */
public final class UnderwayNetwork
{
    /** One line each way and one ruin state per square of this many districts. A seeded shift leaves 5 to 7 districts between two lines. */
    public static final int REGION = 6;
    public static final int NO_GALLERY = -1;

    private static final int LINE_SHIFTS = 2;
    private static final int PERCENT = 100;
    private static final int SEALED_PERCENT = 6;
    private static final int FLOODED_PERCENT = 19;
    private static final int COLLAPSED_PERCENT = 19;
    private static final int BREACHED_PERCENT = 6;
    /** Every run of this many tunnels along a line holds one gate, so at most four stand open in a row: too few to join two hubs. */
    private static final int GATE_EVERY = 3;
    private static final int CAVE_IN_PERCENT = 12;
    private static final int CAVE_IN_PERCENT_IN_COLLAPSED = 45;

    private static final long SALT_ROW = 0x554E44455257L;
    private static final long SALT_COLUMN = 0x554E444552434FL;
    private static final long SALT_RUIN = 0x554E44455252L;
    private static final long SALT_BREACH = 0x554E44455242L;
    private static final long SALT_LINK_X = 0x554E4445524C58L;
    private static final long SALT_LINK_Z = 0x554E4445524C5AL;
    private static final long SALT_GALLERY = 0x554E44455247L;

    private UnderwayNetwork()
    {
    }

    /** @return whether an east-west trunk line runs under this row of districts */
    public static boolean isLineRow(long seed, int districtZ)
    {
        return isLine(seed, districtZ, SALT_ROW);
    }

    /** @return whether a north-south trunk line runs under this column of districts */
    public static boolean isLineColumn(long seed, int districtX)
    {
        return isLine(seed, districtX, SALT_COLUMN);
    }

    public static UnderwayNode nodeAt(long seed, DistrictPos pos)
    {
        boolean row = isLineRow(seed, pos.z());
        boolean column = isLineColumn(seed, pos.x());
        if (row && column)
            return UnderwayNode.HUB;
        return row || column ? UnderwayNode.STATION : UnderwayNode.POST;
    }

    /**
     * @return the direction a post's gallery runs to reach the nearest line, or {@link #NO_GALLERY} on a line
     */
    public static int galleryDirection(long seed, DistrictPos pos)
    {
        if (nodeAt(seed, pos).isOnLine())
            return NO_GALLERY;

        int shortest = Integer.MAX_VALUE;
        int ties = 0;
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            int steps = stepsToLine(seed, pos, direction);
            if (steps < shortest)
            {
                shortest = steps;
                ties = 0;
            }
            if (steps == shortest)
                ties++;
        }

        int chosen = DaedalusHash.pick(DaedalusHash.mix(seed, pos.x(), pos.z(), SALT_GALLERY), ties);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (stepsToLine(seed, pos, direction) == shortest && chosen-- == 0)
                return direction;
        }
        throw new IllegalStateException("No line near " + pos);
    }

    /** @return the station a district's hatch leads to: its own on a line, the end of its gallery otherwise */
    public static DistrictPos stationOf(long seed, DistrictPos pos)
    {
        int direction = galleryDirection(seed, pos);
        if (direction == NO_GALLERY)
            return pos;
        int steps = stepsToLine(seed, pos, direction);
        return new DistrictPos(pos.x() + Compass.dx(direction) * steps, pos.z() + Compass.dz(direction) * steps);
    }

    /** @return whether a post on that side of a district sends its gallery to it or through it */
    public static boolean galleryArrives(long seed, DistrictPos station, int direction)
    {
        for (int steps = 1; ; steps++)
        {
            var from = new DistrictPos(station.x() + Compass.dx(direction) * steps, station.z() + Compass.dz(direction) * steps);
            if (nodeAt(seed, from).isOnLine())
                return false;
            if (galleryDirection(seed, from) == Compass.opposite(direction))
                return true;
        }
    }

    /** @return whether a gallery crosses the chasm on that side of a district, whichever way it is headed */
    public static boolean galleryBetween(long seed, DistrictPos pos, int direction)
    {
        var next = new DistrictPos(pos.x() + Compass.dx(direction), pos.z() + Compass.dz(direction));
        return galleryArrives(seed, pos, direction) || galleryArrives(seed, next, Compass.opposite(direction));
    }

    /** @return the trunk tunnel leaving a district towards its neighbour, the same seen from either end */
    public static UnderwayLink linkFrom(long seed, DistrictPos pos, int direction)
    {
        var next = new DistrictPos(pos.x() + Compass.dx(direction), pos.z() + Compass.dz(direction));
        if (direction == Compass.WEST || direction == Compass.NORTH)
            return linkFrom(seed, next, Compass.opposite(direction));

        boolean alongX = direction == Compass.EAST;
        if (!(alongX ? isLineRow(seed, pos.z()) : isLineColumn(seed, pos.x())))
            return UnderwayLink.NONE;

        var here = regionRuin(seed, pos);
        var there = regionRuin(seed, next);
        boolean sameRegion = Math.floorDiv(pos.x(), REGION) == Math.floorDiv(next.x(), REGION) && Math.floorDiv(pos.z(), REGION) == Math.floorDiv(next.z(), REGION);
        if (sameRegion && here == UnderwayRuin.SEALED)
            return UnderwayLink.OPEN;
        boolean sharedWater = here == UnderwayRuin.FLOODED && there == UnderwayRuin.FLOODED;
        if (!sameRegion && !sharedWater && (here.isShutIn() || there.isShutIn()))
            return UnderwayLink.GATED;

        long salt = alongX ? SALT_LINK_X : SALT_LINK_Z;
        int index = alongX ? pos.x() : pos.z();
        int line = alongX ? pos.z() : pos.x();
        int group = Math.floorDiv(index, GATE_EVERY);
        if (group * GATE_EVERY + DaedalusHash.pick(DaedalusHash.mix(seed, group, line, salt), GATE_EVERY) == index)
            return UnderwayLink.GATED;
        boolean rubble = here == UnderwayRuin.COLLAPSED || there == UnderwayRuin.COLLAPSED;
        int caveIn = rubble ? CAVE_IN_PERCENT_IN_COLLAPSED : CAVE_IN_PERCENT;
        return DaedalusHash.pick(DaedalusHash.mix(seed, pos.x(), pos.z(), ~salt), PERCENT) < caveIn ? UnderwayLink.COLLAPSED : UnderwayLink.OPEN;
    }

    /** @return the state of everything under a district, where a breach is one station's alone */
    public static UnderwayRuin ruinAt(long seed, DistrictPos pos)
    {
        var region = regionRuin(seed, pos);
        if (region.isShutIn() || nodeAt(seed, pos) != UnderwayNode.STATION)
            return region;
        boolean breached = DaedalusHash.pick(DaedalusHash.mix(seed, pos.x(), pos.z(), SALT_BREACH), PERCENT) < BREACHED_PERCENT;
        return breached ? UnderwayRuin.BREACHED : region;
    }

    /** @return the state shared by the square of districts this one belongs to, never {@link UnderwayRuin#BREACHED} */
    public static UnderwayRuin regionRuin(long seed, DistrictPos pos)
    {
        int roll = DaedalusHash.pick(DaedalusHash.mix(seed, Math.floorDiv(pos.x(), REGION), Math.floorDiv(pos.z(), REGION), SALT_RUIN), PERCENT);
        if (roll < SEALED_PERCENT)
            return UnderwayRuin.SEALED;
        if (roll < SEALED_PERCENT + FLOODED_PERCENT)
            return UnderwayRuin.FLOODED;
        return roll < SEALED_PERCENT + FLOODED_PERCENT + COLLAPSED_PERCENT ? UnderwayRuin.COLLAPSED : UnderwayRuin.DRY;
    }

    private static boolean isLine(long seed, int coordinate, long salt)
    {
        int index = Math.floorDiv(coordinate, REGION);
        return index * REGION + DaedalusHash.pick(DaedalusHash.mix(seed, index, 0, salt), LINE_SHIFTS) == coordinate;
    }

    private static int stepsToLine(long seed, DistrictPos pos, int direction)
    {
        boolean alongX = direction == Compass.EAST || direction == Compass.WEST;
        int from = alongX ? pos.x() : pos.z();
        int sign = alongX ? Compass.dx(direction) : Compass.dz(direction);
        long salt = alongX ? SALT_COLUMN : SALT_ROW;
        int steps = 1;
        while (!isLine(seed, from + steps * sign, salt))
            steps++;
        return steps;
    }
}
