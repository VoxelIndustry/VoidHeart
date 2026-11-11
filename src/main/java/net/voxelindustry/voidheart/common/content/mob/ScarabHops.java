package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a Wraith Scarab hops next. It phases through what lies between, so any face in range will do, but salt wards both the spots and the way between:
 * no hop starts, passes or lands within a block of it. It would rather land on a different kind of face than the one it holds, so it goes floor, wall,
 * ceiling in turn where the room allows.
 */
public final class ScarabHops
{
    public static final double MAX_HOP = 3.5;
    public static final double MIN_HOP = 1;

    private static final int RANGE = (int) Math.ceil(MAX_HOP);
    // The scarab's collision box, which sets where its middle ends up against each face
    public static final float WIDTH = 0.55F;
    public static final float HEIGHT = 0.4F;
    // Crossings nearer together than this along the segment are taken as one, through an edge or corner
    private static final double EDGE_EPSILON = 1e-9;
    private static final double ALTERNATE_BONUS = 2;
    private static final double CLOSE_WEIGHT = 1.5;
    private static final double JITTER = 1;
    private static final double IDLE_JITTER = 2;
    private static final int PATHS_TRIED = 24;
    // How much further from its target a hop may take it when no hop brings it closer
    private static final double SIDESTEP = 0.5;

    private ScarabHops()
    {
    }

    /**
     * What the world answers about spots, so the choice can be tested without one.
     */
    public interface Ground
    {
        /**
         * @return whether the scarab fits in this cell against that face, and may go there
         */
        boolean fits(BlockPos cell, Direction surface);

        /**
         * @return whether the block offers a face to hold on that side
         */
        boolean holds(BlockPos block, Direction face);

        /**
         * @return whether the scarab can phase through this block, which unbreakable blocks and pocket walls forbid
         */
        boolean passable(BlockPos block);

        /**
         * @return whether salt lies within a block of it, or under it within the few blocks it could fall, so a line of salt bars the ceiling above it too
         */
        boolean warded(BlockPos block);
    }

    public enum Kind
    {
        FLOOR,
        WALL,
        CEILING;

        public static Kind of(Direction surface)
        {
            return switch (surface)
            {
                case UP -> FLOOR;
                case DOWN -> CEILING;
                default -> WALL;
            };
        }
    }

    /**
     * A cell and the face it holds there, named as {@link CrawlerMonster#surface()} is: the way out of the face.
     */
    public record Spot(BlockPos cell, Direction surface)
    {
        /**
         * @return the middle of its body once settled here, where {@link CrawlerMonster#settledPosition} puts it
         */
        public Vec3 center()
        {
            double gap = WIDTH / 2 + 0.01;
            double x = cell.getX() + 0.5;
            double y = cell.getY() + HEIGHT / 2;
            double z = cell.getZ() + 0.5;
            switch (surface)
            {
                case DOWN -> y = cell.getY() + 1 - HEIGHT / 2 - 0.01;
                case EAST -> x = cell.getX() + gap;
                case WEST -> x = cell.getX() + 1 - gap;
                case SOUTH -> z = cell.getZ() + gap;
                case NORTH -> z = cell.getZ() + 1 - gap;
                case UP ->
                {
                }
            }
            return new Vec3(x, y, z);
        }

        public Kind kind()
        {
            return Kind.of(surface);
        }
    }

    private record Scored(Spot spot, double score)
    {
    }

    /**
     * @param target what to get within reach of, or null to wander
     * @param reach  how near its middle has to come to the target's box to bite
     * @return the spot to hop to, or null where none can be reached
     */
    public static @Nullable Spot plan(Spot from, @Nullable AABB target, double reach, Ground ground, RandomSource random)
    {
        if (ground.warded(from.cell()))
            return null;

        var origin = from.center();
        double distanceNow = target == null ? 0 : distance(origin, target);
        boolean inReach = target != null && distanceNow <= reach;

        var closing = new ArrayList<Scored>();
        var aside = new ArrayList<Scored>();
        for (var cell : BlockPos.betweenClosed(from.cell().offset(-RANGE, -RANGE, -RANGE), from.cell().offset(RANGE, RANGE, RANGE)))
        {
            for (var surface : Direction.values())
            {
                var holder = cell.relative(surface.getOpposite());
                if (!ground.holds(holder, surface))
                    continue;
                var spot = new Spot(cell.immutable(), surface);
                var center = spot.center();
                double hop = center.distanceTo(origin);
                if (hop < MIN_HOP || hop > MAX_HOP)
                    continue;

                double score = spot.kind() != from.kind() ? ALTERNATE_BONUS : 0;
                if (target == null)
                {
                    closing.add(new Scored(spot, score + random.nextDouble() * IDLE_JITTER));
                    continue;
                }
                double distance = distance(center, target);
                score += random.nextDouble() * JITTER - Math.max(0, distance - reach) * CLOSE_WEIGHT;
                // Out of reach it closes in, and in reach it keeps there. Held back, as by salt, it skitters along where it is rather than wait
                if (inReach ? distance <= reach : distance < distanceNow)
                    closing.add(new Scored(spot, score));
                else if (!inReach && distance <= distanceNow + SIDESTEP)
                    aside.add(new Scored(spot, score));
            }
        }

        var spot = best(closing, origin, ground);
        return spot != null ? spot : best(aside, origin, ground);
    }

    private static @Nullable Spot best(List<Scored> scored, Vec3 origin, Ground ground)
    {
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int tried = 0;
        for (var candidate : scored)
        {
            if (tried >= PATHS_TRIED)
                break;
            if (!ground.fits(candidate.spot().cell(), candidate.spot().surface()))
                continue;
            tried++;
            if (clear(origin, candidate.spot().center(), ground))
                return candidate.spot();
        }
        return null;
    }

    public static double distance(Vec3 point, AABB box)
    {
        return Math.sqrt(box.distanceToSqr(point));
    }

    /**
     * @return whether every block between the two points, both ends included, can be phased through and lies outside any ward
     */
    public static boolean clear(Vec3 from, Vec3 to, Ground ground)
    {
        for (var block : blocksBetween(from, to))
        {
            if (!ground.passable(block) || ground.warded(block))
                return false;
        }
        return true;
    }

    /**
     * Every block the segment passes through, in order, walked one block boundary at a time so none is skipped. Where it crosses exactly at an edge or
     * corner, the blocks meeting there are included too, so it can never slip between two diagonal ones.
     */
    static List<BlockPos> blocksBetween(Vec3 from, Vec3 to)
    {
        int[] cell = { Mth.floor(from.x), Mth.floor(from.y), Mth.floor(from.z) };
        int[] last = { Mth.floor(to.x), Mth.floor(to.y), Mth.floor(to.z) };
        double[] origin = { from.x, from.y, from.z };
        double[] delta = { to.x - from.x, to.y - from.y, to.z - from.z };
        int[] step = new int[3];
        // How far along the segment, from 0 to 1, the next boundary on each axis lies, and how far apart those boundaries are
        double[] next = new double[3];
        double[] across = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            step[axis] = (int) Math.signum(delta[axis]);
            if (step[axis] == 0)
            {
                next[axis] = Double.POSITIVE_INFINITY;
                continue;
            }
            double boundary = step[axis] > 0 ? cell[axis] + 1 : cell[axis];
            next[axis] = (boundary - origin[axis]) / delta[axis];
            across[axis] = 1 / Math.abs(delta[axis]);
        }

        var blocks = new ArrayList<BlockPos>();
        var seen = new HashSet<BlockPos>();
        add(blocks, seen, cell);
        while (!Arrays.equals(cell, last))
        {
            double t = Math.min(next[0], Math.min(next[1], next[2]));
            if (t > 1)
                break;
            boolean[] crossing = new boolean[3];
            for (int axis = 0; axis < 3; axis++)
                crossing[axis] = next[axis] - t < EDGE_EPSILON;
            for (int axis = 0; axis < 3; axis++)
            {
                if (crossing[axis])
                {
                    var beside = cell.clone();
                    beside[axis] += step[axis];
                    add(blocks, seen, beside);
                }
            }
            for (int axis = 0; axis < 3; axis++)
            {
                if (crossing[axis])
                {
                    cell[axis] += step[axis];
                    next[axis] += across[axis];
                }
            }
            add(blocks, seen, cell);
        }
        return blocks;
    }

    private static void add(List<BlockPos> blocks, Set<BlockPos> seen, int[] cell)
    {
        var block = new BlockPos(cell[0], cell[1], cell[2]);
        if (seen.add(block))
            blocks.add(block);
    }
}
