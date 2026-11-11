package net.voxelindustry.voidheart.common.content.wraithveil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a Wraith Veil can open a tunnel through a wall, floor or ceiling. The world is only read through {@link Cells}, so this is unit tested
 * without a level. A tunnel goes straight in from the face that was used, at most {@link #MAX_DEPTH} blocks, and opens at the first spot past it
 * where a standing body fits. Anything forbidden on the way, such as fluid, bedrock, a pocket wall or a block entity, rules the whole tunnel out.
 * Everything else in it, whole blocks, slabs, glass or air, is erased from its bearer's sight while it is open, so whatever was in the way is
 * simply gone.
 * <p>
 * Through a wall it is always two blocks tall. It starts where a body can stand at the mouth, so a step in front is stood on, and it comes out
 * at the first spot past the face where a body stands on the same floor or one up, such as on a step. Anything a body would stand on too high
 * for the mouth, a slab underfoot or the step at the far side, is taken into the tunnel and erased with the rest, so the body walks in on the
 * tunnel's floor and steps or jumps up where the tunnel ends. The realm itself only spans the wall, between the two marks.
 * <p>
 * Once the mark has spread the tunnel bores in from both mouths over {@link #BORE_TICKS} and stays open for {@link #OPEN_TICKS}, and its bearer
 * walks or climbs in it as they like. Then it collapses over {@link #COLLAPSE_TICKS}, pinching shut from its middle out to the mouths, which
 * pushes them out of whichever end is nearer.
 */
public final class WallPassagePlan
{
    public static final int    MAX_DEPTH      = 10;
    public static final int    MARK_TICKS     = 16;
    public static final int    OPEN_TICKS     = 160;
    public static final int    COLLAPSE_TICKS = 20;
    public static final int    REST_TICKS     = 40;
    public static final double BODY_HEIGHT    = 1.8;
    // Climbing up into a ceiling starts this many blocks below it at most
    public static final int    CLIMB_REACH    = 4;
    // The mouth's share of its mark, so the ink spreads past the mouth onto the surface around it. The ink masks are drawn for this
    public static final double MARK_COVER     = 0.6;
    // How long the tunnel takes to bore in from its mouths and meet in the middle once the marks have spread
    public static final int    BORE_TICKS     = 22;
    // How far the narrowing ahead of a pinch reaches, as a share of half the tunnel
    public static final float  PINCH          = 0.4F;

    // How far above or below the feet a wall's mouth may stand, about a jump
    public static final double ENTRY_REACH    = 1.3;
    // How high feet may rest above a wall's tunnel floor with the head still under its top, carpet but not a slab
    public static final double HEADROOM       = 2 - BODY_HEIGHT;

    private static final double FEET_EPSILON = 1e-4;

    /**
     * OPEN has nothing to bump into, SOLID is a whole opaque block a tunnel can cross, PARTIAL is anything else in the way, such as a slab,
     * carpet, fence or glass, and FORBIDDEN rules a tunnel out.
     */
    public enum Cell
    {
        OPEN, PARTIAL, SOLID, FORBIDDEN
    }

    public interface Cells
    {
        Cell at(BlockPos pos);

        /**
         * @return how high above the bottom of this cell feet would rest, for carpet and the like
         */
        double standHeight(BlockPos pos);

        boolean fits(Vec3 feet);
    }

    /**
     * @param face  the way the marked surface faces, out of the wall
     * @param halfU half the mark's size along the first axis of its surface: x, or z on a wall facing east or west
     * @param halfV half its size along the second: y on a wall, z on a floor or ceiling
     */
    public record Mark(Vec3 center, Direction face, double halfU, double halfV)
    {
    }

    public enum Phase
    {
        MARKING, OPEN, COLLAPSING, CLOSED;

        public boolean passable()
        {
            return this == OPEN || this == COLLAPSING;
        }
    }

    /**
     * @param tubeMin   the corner of the blocks crossed, inclusive
     * @param tubeMax   the opposite corner, inclusive
     * @param entrySpot where feet stand just outside the mouth that was marked
     * @param exitSpot  where they stand just outside the far mouth
     * @param climbFrom for a tunnel up into a ceiling, the lowest block of open air under it from which one can climb in
     */
    public record Plan(BlockPos tubeMin, BlockPos tubeMax, Direction into, int depth, Mark entry, Mark exit, Vec3 entrySpot, Vec3 exitSpot, BlockPos climbFrom)
    {
        public static Phase phaseAt(float ticks)
        {
            if (ticks < MARK_TICKS)
                return Phase.MARKING;
            if (ticks < MARK_TICKS + OPEN_TICKS)
                return Phase.OPEN;
            if (ticks < totalTicks())
                return Phase.COLLAPSING;
            return Phase.CLOSED;
        }

        /**
         * The tunnel opens like the reverse of its collapse: shut everywhere while the marks spread, it opens at the mouths first and bores in to
         * meet in the middle, then pinches shut from the middle out. A narrowing runs ahead of the pinch, so it closes as a funnel.
         *
         * @param fromMiddle 0 at the tunnel's middle, 1 at either mouth
         * @return how shut the tunnel is there, 0 open to 1 shut
         */
        public static float closure(float ticks, float fromMiddle)
        {
            float bored = Mth.clamp((ticks - MARK_TICKS) / BORE_TICKS, 0, 1);
            float front = Math.max(1 - bored, collapse(ticks)) * (1 + PINCH);
            float t = Mth.clamp((front - fromMiddle) / PINCH, 0, 1);
            return t * t * (3 - 2 * t);
        }

        /**
         * @return how far the hole in each mark has opened: shut while the ink spreads, open first as the tunnel bores in, shut last as it collapses
         */
        public static float holeOpen(float ticks)
        {
            return 1 - closure(ticks, 1);
        }

        public static int totalTicks()
        {
            return MARK_TICKS + OPEN_TICKS + COLLAPSE_TICKS;
        }

        public static int cooldownTicks()
        {
            return totalTicks() + REST_TICKS;
        }

        /**
         * @return 0 until the collapse begins, 1 once it is over
         */
        public static float collapse(float ticks)
        {
            return Mth.clamp((ticks - MARK_TICKS - OPEN_TICKS) / COLLAPSE_TICKS, 0, 1);
        }

        public boolean contains(BlockPos pos)
        {
            return pos.getX() >= tubeMin.getX() && pos.getX() <= tubeMax.getX()
                   && pos.getY() >= tubeMin.getY() && pos.getY() <= tubeMax.getY()
                   && pos.getZ() >= tubeMin.getZ() && pos.getZ() <= tubeMax.getZ();
        }

        public AABB box()
        {
            return new AABB(tubeMin.getX(), tubeMin.getY(), tubeMin.getZ(), tubeMax.getX() + 1, tubeMax.getY() + 1, tubeMax.getZ() + 1);
        }

        /**
         * @return the part of the tunnel the realm fills, between its two marks: past them it may cross what a body stands on, which is only erased
         */
        public AABB realm()
        {
            var box = box();
            var axis = into.getAxis();
            double a = axis.choose(entry.center().x, entry.center().y, entry.center().z), b = axis.choose(exit.center().x, exit.center().y, exit.center().z);
            double from = Math.min(a, b), to = Math.max(a, b);
            return switch (axis)
            {
                case X -> box.setMinX(from).setMaxX(to);
                case Y -> box.setMinY(from).setMaxY(to);
                case Z -> box.setMinZ(from).setMaxZ(to);
            };
        }

        public boolean vertical()
        {
            return into.getAxis() == Direction.Axis.Y;
        }

        /**
         * @return the column one can climb in: the tunnel, and for one going up the open air below it
         */
        public AABB climbBox()
        {
            var box = box();
            return into == Direction.UP ? box.setMinY(climbFrom.getY()) : box;
        }

        /**
         * @return the spot outside whichever mouth is nearer along the tunnel
         */
        public Vec3 nearestEnd(Vec3 feet)
        {
            var axis = into.getAxis();
            double at = axis.choose(feet.x, feet.y, feet.z);
            double toEntry = Math.abs(at - axis.choose(entrySpot.x, entrySpot.y, entrySpot.z));
            double toExit = Math.abs(at - axis.choose(exitSpot.x, exitSpot.y, exitSpot.z));
            return toEntry <= toExit ? entrySpot : exitSpot;
        }
    }

    private WallPassagePlan()
    {
    }

    /**
     * @param clicked the block whose face was used
     * @param face    the face that was used, pointing out of the wall toward whoever used it
     * @param feet    where they stand
     * @return the tunnel, or null when the wall cannot be crossed from there
     */
    public static @Nullable Plan plan(Cells cells, BlockPos clicked, Direction face, Vec3 feet)
    {
        var used = cells.at(clicked);
        if (used == Cell.OPEN || used == Cell.FORBIDDEN)
            return null;
        if (face.getAxis().isHorizontal())
            return throughWall(cells, clicked, face, feet);
        return face == Direction.UP ? throughFloor(cells, clicked) : throughCeiling(cells, clicked);
    }

    /**
     * Starts from the spot nearest the feet where a body stands at the mouth with the used face in front of it, and comes out on the far side
     * at that floor or one up.
     */
    private static @Nullable Plan throughWall(Cells cells, BlockPos clicked, Direction face, Vec3 feet)
    {
        var into = face.getOpposite();
        Vec3 entrySpot = null;
        for (int y = clicked.getY() - 1; y <= clicked.getY(); y++)
        {
            var outside = new BlockPos(clicked.getX(), y, clicked.getZ()).relative(face);
            var spot = standingSpot(cells, outside);
            if (spot == null || Math.abs(spot.y - feet.y) > ENTRY_REACH || clicked.getY() < bottomRow(spot) || clicked.getY() > bottomRow(spot) + 1)
                continue;
            if (cells.standHeight(outside) == 0 && cells.at(outside.below()) == Cell.OPEN)
                continue;
            if (entrySpot == null || Math.abs(spot.y - feet.y) < Math.abs(entrySpot.y - feet.y))
                entrySpot = spot;
        }
        if (entrySpot == null)
            return null;

        int floor = bottomRow(entrySpot);
        var base = new BlockPos(clicked.getX(), floor, clicked.getZ());
        for (int k = 0; k <= MAX_DEPTH; k++)
        {
            var column = base.relative(into, k);
            if (k > 0)
            {
                var exit = exitAt(cells, column, floor);
                if (exit != null)
                    return wallTunnel(cells, face, into, base, k, entrySpot, exit);
            }
            if (k == MAX_DEPTH || column(cells, column, floor, floor + 1) == Cell.FORBIDDEN)
                return null;
        }
        return null;
    }

    /**
     * @return where a body stands in this column on the tunnel's floor or one up, out of any fluid, or null
     */
    private static @Nullable Vec3 exitAt(Cells cells, BlockPos column, int floor)
    {
        for (int rise = 0; rise <= 1; rise++)
        {
            var spot = standingSpot(cells, column.atY(floor + rise));
            if (spot != null && column(cells, column, bottomRow(spot), topRow(spot)) != Cell.FORBIDDEN)
                return spot;
        }
        return null;
    }

    /**
     * Ends at column k. A slab underfoot at the mouth, or what the body stands on at column k when it is above the tunnel's floor, is taken into
     * the tunnel, so the body walks in level and steps up where it ends.
     */
    private static @Nullable Plan wallTunnel(Cells cells, Direction face, Direction into, BlockPos base, int k, Vec3 entrySpot, Vec3 exit)
    {
        int floor = base.getY();
        int first = entrySpot.y - floor > HEADROOM ? -1 : 0;
        int last = exit.y - floor > HEADROOM ? k : k - 1;
        for (int i = first; i <= last; i++)
            if (column(cells, base.relative(into, i), floor, floor + 1) == Cell.FORBIDDEN)
                return null;

        double halfU = 0.5 / MARK_COVER, halfV = 1 / MARK_COVER;
        var nearFace = Vec3.atBottomCenterOf(base).add(-into.getStepX() * 0.5, 1, -into.getStepZ() * 0.5);
        var farFace = Vec3.atBottomCenterOf(base.relative(into, k)).add(-into.getStepX() * 0.5, 1, -into.getStepZ() * 0.5);
        var from = base.relative(into, first);
        var to = base.relative(into, last).above();
        return new Plan(min(from, to), max(from, to), into, k, new Mark(nearFace, face, halfU, halfV), new Mark(farFace, into, halfU, halfV),
                entrySpot, exit, base);
    }

    /**
     * @return FORBIDDEN if any of these rows is, SOLID if all are, OPEN otherwise
     */
    private static Cell column(Cells cells, BlockPos at, int fromY, int toY)
    {
        var result = Cell.SOLID;
        for (int y = fromY; y <= toY; y++)
        {
            var cell = cells.at(at.atY(y));
            if (cell == Cell.FORBIDDEN)
                return Cell.FORBIDDEN;
            if (cell != Cell.SOLID)
                result = Cell.OPEN;
        }
        return result;
    }

    private static int bottomRow(Vec3 feet)
    {
        return Mth.floor(feet.y + FEET_EPSILON);
    }

    private static int topRow(Vec3 feet)
    {
        return Mth.floor(feet.y + BODY_HEIGHT - FEET_EPSILON);
    }

    /**
     * Comes out hanging just under the floor, then falls.
     */
    private static @Nullable Plan throughFloor(Cells cells, BlockPos clicked)
    {
        for (int k = 1; k <= MAX_DEPTH; k++)
        {
            var upper = clicked.below(k);
            var lower = upper.below();
            var upperCell = cells.at(upper);
            if (upperCell == Cell.FORBIDDEN)
                return null;
            // Anything in the way, or a pocket too small to hang in, is crossed
            var exit = Vec3.atBottomCenterOf(lower).add(0, cells.standHeight(lower), 0);
            if (upperCell != Cell.OPEN || cells.at(lower) == Cell.FORBIDDEN || !cells.fits(exit))
                continue;

            double half = 0.5 / MARK_COVER;
            var top = Vec3.atBottomCenterOf(clicked).add(0, 1, 0);
            var last = clicked.below(k - 1);
            return new Plan(last, clicked, Direction.DOWN, k, new Mark(top, Direction.UP, half, half), new Mark(Vec3.atBottomCenterOf(last), Direction.DOWN, half, half),
                    top, exit, last);
        }
        return null;
    }

    /**
     * Climbed into from below, and left standing on top.
     */
    private static @Nullable Plan throughCeiling(Cells cells, BlockPos clicked)
    {
        for (int k = 1; k <= MAX_DEPTH; k++)
        {
            var cell = clicked.above(k);
            var above = cells.at(cell);
            if (above == Cell.FORBIDDEN)
                return null;
            // Climbing out needs the block on top clear; anything there is crossed and erased instead
            var exit = Vec3.atBottomCenterOf(cell);
            if (above != Cell.OPEN || !cells.fits(exit))
                continue;

            var climbFrom = clicked;
            for (int down = 1; down <= CLIMB_REACH && cells.at(clicked.below(down)) == Cell.OPEN; down++)
                climbFrom = clicked.below(down);

            double half = 0.5 / MARK_COVER;
            var bottom = Vec3.atBottomCenterOf(clicked);
            var last = clicked.above(k - 1);
            return new Plan(clicked, last, Direction.UP, k, new Mark(bottom, Direction.DOWN, half, half), new Mark(Vec3.atBottomCenterOf(cell), Direction.UP, half, half),
                    bottom.subtract(0, BODY_HEIGHT, 0), exit, climbFrom);
        }
        return null;
    }

    /**
     * @return where feet rest in this block, on top of anything low in it such as a slab, when a body fits there, or null
     */
    private static @Nullable Vec3 standingSpot(Cells cells, BlockPos pos)
    {
        var feet = Vec3.atBottomCenterOf(pos).add(0, cells.standHeight(pos), 0);
        return cells.fits(feet) ? feet : null;
    }

    private static BlockPos min(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b)
    {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }
}
