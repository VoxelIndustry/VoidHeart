package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The roots a search grows, as a pure function of the bubble: a client draws them from the search's progress alone, without being sent a single point.
 * All timings are fractions of the search. One root reaches the bubble; every other one has withered away by the time it does.
 * Every root grows out of the rings' mount, or out of another root, and none ever passes under the mount.
 */
public final class ArmillaryRoots
{
    /** Width of the chart for a height of one, so that roots don't look stretched sideways. */
    public static final float ASPECT = 1.45F;

    private static final float STEP_LENGTH     = 0.022F;
    private static final float MAIN_WOBBLE     = 0.42F;
    private static final float DEAD_END_WOBBLE = 0.7F;
    private static final float STEER_START     = 0.3F;
    private static final float STEER_GROWTH    = 0.55F;
    // Below 1 / (2 * PI * STALLS) the main root never moves back, it only slows and rushes
    private static final float STALL_STRENGTH  = 0.07F;
    private static final int   STALLS          = 2;

    private static final int   DEAD_ENDS      = 12;
    private static final int   DEAD_ENDS_DEEP = 4;
    private static final int   MOUNT_ROOTS    = 5;
    private static final int   MAX_TWIGS      = 3;
    private static final float LONG_CHANCE    = 0.3F;

    // How far either side of the way to the bubble a root that won't reach it sets off
    private static final float DEAD_END_SPREAD = Mth.PI * 0.55F;
    private static final float MOUNT_SPREAD    = Mth.PI * 0.7F;
    // Mount roots leave from its lower half only, the upper half being behind the slab
    private static final float MOUNT_LOWEST    = Mth.PI * 0.08F;

    private static final float SHORT_MIN = 0.08F, SHORT_MAX = 0.2F;
    private static final float LONG_MIN  = 0.25F, LONG_MAX  = 0.42F;
    private static final float TWIG_MIN  = 0.05F, TWIG_MAX  = 0.11F;

    private static final float GROWTH_MIN  = 0.08F, GROWTH_RANGE = 0.14F;
    private static final float LINGER_MIN  = 0.03F, LINGER_RANGE = 0.08F;
    private static final float WITHER_MIN  = 0.07F, WITHER_RANGE = 0.12F;
    private static final float LAST_WITHER = 0.985F;
    private static final float EDGE        = 0.015F;
    private static final float KEEP_OUT    = 1.6F;
    // No root ever digs into the strata, though the one reaching for it ends on its lip
    private static final float FLOOR       = ArmillaryChart.STRATA_TOP;

    public record Branch(float[] xs, float[] ys, float growStart, float growEnd, float witherStart, float witherEnd, boolean main)
    {
        public int segments()
        {
            return xs.length - 1;
        }

        /**
         * How far along its points the tip has grown, from 0 to 1.
         */
        public float grown(float progress)
        {
            float time = Mth.clamp((progress - growStart) / (growEnd - growStart), 0, 1);
            if (main)
                return time + STALL_STRENGTH * Mth.sin(2 * Mth.PI * STALLS * time);
            return time;
        }

        /**
         * 0 while alive, 1 once it has entirely withered away.
         */
        public float withered(float progress)
        {
            if (main)
                return 0;
            return Mth.clamp((progress - witherStart) / (witherEnd - witherStart), 0, 1);
        }
    }

    /**
     * The mount the roots grow out of, an ellipse centred on the top of the chart, in root space.
     */
    private record Mount(float x, float radiusX, float radiusY)
    {
        boolean contains(float pointX, float pointY)
        {
            float dx = (pointX - x) / radiusX;
            float dy = pointY / radiusY;
            return dx * dx + dy * dy < 1;
        }

        /**
         * Where a line from the middle at this angle crosses the rim.
         */
        float[] rim(float angle)
        {
            float dx = Mth.cos(angle) / radiusX;
            float dy = Mth.sin(angle) / radiusY;
            float reach = 1 / Mth.sqrt(dx * dx + dy * dy);
            return new float[] { x + Mth.cos(angle) * reach, Mth.sin(angle) * reach };
        }

        float[] pushOut(float pointX, float pointY)
        {
            return rim((float) Math.atan2(pointY, pointX - x));
        }
    }

    private ArmillaryRoots()
    {
    }

    /**
     * @param sourceRadiusX half the width of the mount, as a share of the chart's width
     * @param sourceRadiusY half the height of the mount, as a share of the chart's height
     */
    public static List<Branch> grow(long seed, float sourceX, float sourceRadiusX, float sourceRadiusY, float targetX, float targetY, float targetRadius)
    {
        var random = new Random(seed);
        var branches = new ArrayList<Branch>();
        var mount = new Mount(sourceX * ASPECT, Math.max(1.0E-4F, sourceRadiusX * ASPECT), Math.max(1.0E-4F, sourceRadiusY));
        float toX = targetX * ASPECT;
        float toY = targetY - targetRadius;

        var start = mount.rim((float) Math.atan2(toY, toX - mount.x()));
        float[][] main = walk(random, mount, start[0], start[1], toX, toY, MAIN_WOBBLE, -1, 0, 0);
        branches.add(branch(main, 0, 1, 0, 0, true));

        int deadEnds = DEAD_ENDS + Math.round(DEAD_ENDS_DEEP * targetY);
        for (int index = 0; index < deadEnds; index++)
            addDeadEnd(random, mount, branches, main, toX, targetY, targetRadius);

        for (int index = 0; index < MOUNT_ROOTS; index++)
        {
            float angle = Mth.clamp(heading(random, mount.x(), 0, toX, targetY, MOUNT_SPREAD), MOUNT_LOWEST, Mth.PI - MOUNT_LOWEST);
            var from = mount.rim(angle);
            float born = random.nextFloat() * 0.45F;
            addRoot(random, mount, branches, from[0], from[1], angle, born, randomBetween(random, SHORT_MIN, LONG_MIN), LAST_WITHER, toX, targetY, targetRadius);
        }
        return branches;
    }

    /**
     * Roughly the way to the bubble, give or take half the spread.
     */
    private static float heading(Random random, float fromX, float fromY, float targetX, float targetY, float spread)
    {
        return (float) Math.atan2(targetY - fromY, targetX - fromX) + (random.nextFloat() - 0.5F) * spread;
    }

    private static void addDeadEnd(Random random, Mount mount, List<Branch> branches, float[][] main, float targetX, float targetY, float targetRadius)
    {
        float along = 0.04F + random.nextFloat() * 0.86F;
        int point = Math.round(along * (main[0].length - 1));
        boolean isLong = random.nextFloat() < LONG_CHANCE;
        float length = isLong ? randomBetween(random, LONG_MIN, LONG_MAX) : randomBetween(random, SHORT_MIN, SHORT_MAX);

        float x = main[0][point];
        float y = main[1][point];
        var root = addRoot(random, mount, branches, x, y, heading(random, x, y, targetX, targetY, DEAD_END_SPREAD), along, length, LAST_WITHER, targetX, targetY, targetRadius);
        if (isLong)
            addTwigs(random, mount, branches, root, targetX, targetY, targetRadius);
    }

    private static void addTwigs(Random random, Mount mount, List<Branch> branches, Branch root, float targetX, float targetY, float targetRadius)
    {
        int twigs = 1 + random.nextInt(MAX_TWIGS);
        for (int index = 0; index < twigs; index++)
        {
            float along = 0.3F + random.nextFloat() * 0.6F;
            int point = Math.round(along * root.segments());
            float born = root.growStart() + along * (root.growEnd() - root.growStart());
            float x = root.xs()[point] * ASPECT;
            float y = root.ys()[point];
            // A twig is gone before what it grew from starts to wither, so none is ever left floating
            addRoot(random, mount, branches, x, y, heading(random, x, y, targetX, targetY, DEAD_END_SPREAD), born, randomBetween(random, TWIG_MIN, TWIG_MAX), root.witherStart(),
                    targetX, targetY, targetRadius);
        }
    }

    private static Branch addRoot(Random random, Mount mount, List<Branch> branches, float x, float y, float angle, float born, float length, float latestEnd,
                                  float targetX, float targetY, float targetRadius)
    {
        float endX = Mth.clamp(x + Mth.cos(angle) * length, EDGE, ASPECT - EDGE);
        float endY = Mth.clamp(y + Mth.sin(angle) * length, EDGE, FLOOR - EDGE);
        float[][] points = walk(random, mount, x, y, endX, endY, DEAD_END_WOBBLE, targetX, targetY, targetRadius * KEEP_OUT);

        float growEnd = born + GROWTH_MIN + random.nextFloat() * GROWTH_RANGE;
        float witherStart = growEnd + LINGER_MIN + random.nextFloat() * LINGER_RANGE;
        float witherEnd = witherStart + WITHER_MIN + random.nextFloat() * WITHER_RANGE;
        if (witherEnd > latestEnd)
        {
            float squeeze = (latestEnd - born) / (witherEnd - born);
            growEnd = born + (growEnd - born) * squeeze;
            witherStart = born + (witherStart - born) * squeeze;
            witherEnd = latestEnd;
        }

        var branch = branch(points, born, growEnd, witherStart, witherEnd, false);
        branches.add(branch);
        return branch;
    }

    private static Branch branch(float[][] points, float growStart, float growEnd, float witherStart, float witherEnd, boolean main)
    {
        float[] xs = new float[points[0].length];
        for (int index = 0; index < xs.length; index++)
            xs[index] = points[0][index] / ASPECT;
        return new Branch(xs, points[1], growStart, growEnd, witherStart, witherEnd, main);
    }

    /**
     * A walk steering ever harder toward its end, so that it wanders early and arrives exactly. Stops short of the circle it must keep out of, if any,
     * and is pushed back out of the mount whenever it wanders under it.
     */
    private static float[][] walk(Random random, Mount mount, float fromX, float fromY, float toX, float toY, float wobble, float avoidX, float avoidY, float avoidRadius)
    {
        float distance = Mth.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY));
        int steps = Math.max(3, Math.round(distance / STEP_LENGTH));
        float[] xs = new float[steps + 1];
        float[] ys = new float[steps + 1];
        xs[0] = fromX;
        ys[0] = fromY;

        float x = fromX, y = fromY;
        float heading = (float) Math.atan2(toY - fromY, toX - fromX);
        int count = 1;
        for (int step = 1; step <= steps; step++)
        {
            float towards = (float) Math.atan2(toY - y, toX - x);
            float steer = STEER_START + STEER_GROWTH * step / steps;
            heading += Mth.wrapDegrees((towards - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * steer + (random.nextFloat() - 0.5F) * wobble;

            float remaining = Mth.sqrt((toX - x) * (toX - x) + (toY - y) * (toY - y)) / (steps - step + 1);
            x = Mth.clamp(x + Mth.cos(heading) * remaining, EDGE, ASPECT - EDGE);
            y = Mth.clamp(y + Mth.sin(heading) * remaining, 0, FLOOR - EDGE);
            if (step == steps && avoidRadius <= 0)
            {
                x = toX;
                y = toY;
            }
            if (mount.contains(x, y))
            {
                var out = mount.pushOut(x, y);
                x = out[0];
                y = out[1];
            }

            if (avoidRadius > 0 && (x - avoidX) * (x - avoidX) + (y - avoidY) * (y - avoidY) < avoidRadius * avoidRadius)
                break;

            xs[count] = x;
            ys[count] = y;
            count++;
        }

        if (count < 2)
        {
            xs[1] = fromX;
            ys[1] = fromY;
            count = 2;
        }
        return new float[][] { Arrays.copyOf(xs, count), Arrays.copyOf(ys, count) };
    }

    private static float randomBetween(Random random, float min, float max)
    {
        return min + random.nextFloat() * (max - min);
    }
}
