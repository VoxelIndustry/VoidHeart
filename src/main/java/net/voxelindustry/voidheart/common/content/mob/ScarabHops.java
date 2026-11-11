package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

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
    private static final double SAMPLE_STEP = 0.25;
    // A face's middle sits this far in from the cell's middle, which is close enough to where the scarab's body ends up
    private static final double FACE_INSET = 0.35;
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
        public Vec3 center()
        {
            return Vec3.atCenterOf(cell).subtract(surface.getStepX() * FACE_INSET, surface.getStepY() * FACE_INSET, surface.getStepZ() * FACE_INSET);
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
            double cellDistance = Math.sqrt(cell.distSqr(from.cell()));
            if (cellDistance > MAX_HOP)
                continue;

            for (var surface : Direction.values())
            {
                var holder = cell.relative(surface.getOpposite());
                if (!ground.holds(holder, surface))
                    continue;
                var spot = new Spot(cell.immutable(), surface);
                var center = spot.center();
                if (center.distanceTo(origin) < MIN_HOP)
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

    static List<BlockPos> blocksBetween(Vec3 from, Vec3 to)
    {
        var seen = new HashSet<BlockPos>();
        var blocks = new ArrayList<BlockPos>();
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
        for (int i = 0; i <= steps; i++)
        {
            var block = BlockPos.containing(from.lerp(to, i / (double) steps));
            if (seen.add(block))
                blocks.add(block);
        }
        return blocks;
    }
}
