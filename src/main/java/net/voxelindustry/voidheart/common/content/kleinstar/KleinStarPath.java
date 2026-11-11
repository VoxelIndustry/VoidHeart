package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole way a Klein Star flies: one Bézier curve after another, each starting where the last ended. In the open it is the single swinging curve
 * of {@link KleinStarFlight}. Around obstacles it is a curve through each corner {@link KleinStarRoute} found, rounded as far as the walls allow.
 * It is told by its points alone, so the server that moves the star and the clients that draw it agree on all of it, and it is walked by distance,
 * so the star keeps its speed whatever the shape of the curve.
 */
public final class KleinStarPath
{
    // Each curve is measured as this many straight pieces
    private static final int      SAMPLES   = 24;
    // A curve that would cut into a wall is tried flatter, down to the straight line between its ends, which is known to be clear
    private static final double[] ROUNDINGS = {1, 0.5, 0};
    private static final int      CHECKS    = 8;

    private final List<Vec3> points;
    private final Vec3[]     samples;
    private final double[]   reached;

    /**
     * @param points the ends and the two guiding points of each curve in turn: four for one curve, three more for each one after
     */
    public KleinStarPath(List<Vec3> points)
    {
        if (points.size() < 4 || (points.size() - 1) % 3 != 0)
            throw new IllegalArgumentException("A path of Bézier curves has 3n + 1 points, not " + points.size());
        this.points = List.copyOf(points);

        int curves = (points.size() - 1) / 3;
        samples = new Vec3[curves * SAMPLES + 1];
        reached = new double[samples.length];
        samples[0] = points.getFirst();
        for (int curve = 0; curve < curves; curve++)
            for (int step = 1; step <= SAMPLES; step++)
            {
                int index = curve * SAMPLES + step;
                samples[index] = onCurve(curve, step / (double) SAMPLES);
                reached[index] = reached[index - 1] + samples[index].distanceTo(samples[index - 1]);
            }
    }

    public static KleinStarPath of(KleinStarFlight flight)
    {
        return new KleinStarPath(List.of(flight.from(), flight.first(), flight.second(), flight.to()));
    }

    /**
     * A curve through every point in turn.
     *
     * @param corners at least one point: a single one is a path that goes nowhere
     * @param heading which way the star is already going, so it does not turn on the spot, or null when it is at rest
     */
    public static KleinStarPath through(List<Vec3> corners, @Nullable Vec3 heading, KleinStarRoute.Sight sight)
    {
        if (corners.isEmpty())
            throw new IllegalArgumentException("A path goes through at least one point");
        if (corners.size() == 1)
            return new KleinStarPath(List.of(corners.getFirst(), corners.getFirst(), corners.getFirst(), corners.getFirst()));
        int last = corners.size() - 1;
        var tangents = new Vec3[corners.size()];
        for (int i = 0; i <= last; i++)
        {
            var before = corners.get(Math.max(i - 1, 0));
            var after = corners.get(Math.min(i + 1, last));
            var tangent = after.subtract(before).scale(i == 0 || i == last ? 1 : 0.5);
            if (i == 0 && heading != null && heading.lengthSqr() > 1e-9)
                tangent = heading.normalize().scale(tangent.length());
            // Never longer than the shorter of the two legs it joins, so the curve does not loop back on itself
            double shortest = Math.min(corners.get(i).distanceTo(before) > 1e-9 ? corners.get(i).distanceTo(before) : Double.MAX_VALUE,
                    corners.get(i).distanceTo(after) > 1e-9 ? corners.get(i).distanceTo(after) : Double.MAX_VALUE);
            if (tangent.length() > shortest)
                tangent = tangent.scale(shortest / tangent.length());
            tangents[i] = tangent;
        }

        var points = new ArrayList<Vec3>();
        points.add(corners.getFirst());
        for (int i = 0; i < last; i++)
        {
            var from = corners.get(i);
            var to = corners.get(i + 1);
            Vec3 first = from, second = to;
            for (double rounding : ROUNDINGS)
            {
                first = from.add(tangents[i].scale(rounding / 3));
                second = to.subtract(tangents[i + 1].scale(rounding / 3));
                if (rounding == 0 || clear(from, first, second, to, sight))
                    break;
            }
            points.add(first);
            points.add(second);
            points.add(to);
        }
        return new KleinStarPath(points);
    }

    private static boolean clear(Vec3 from, Vec3 first, Vec3 second, Vec3 to, KleinStarRoute.Sight sight)
    {
        var last = from;
        for (int check = 1; check <= CHECKS; check++)
        {
            var point = bezier(from, first, second, to, check / (double) CHECKS);
            if (!sight.clear(last, point))
                return false;
            last = point;
        }
        return true;
    }

    private static Vec3 bezier(Vec3 from, Vec3 first, Vec3 second, Vec3 to, double along)
    {
        double u = 1 - along;
        return from.scale(u * u * u).add(first.scale(3 * u * u * along)).add(second.scale(3 * u * along * along)).add(to.scale(along * along * along));
    }

    private Vec3 onCurve(int curve, double along)
    {
        int start = curve * 3;
        return bezier(points.get(start), points.get(start + 1), points.get(start + 2), points.get(start + 3), along);
    }

    public List<Vec3> points()
    {
        return points;
    }

    public Vec3 from()
    {
        return points.getFirst();
    }

    public Vec3 to()
    {
        return points.getLast();
    }

    public double length()
    {
        return reached[reached.length - 1];
    }

    /**
     * @return the point this many blocks along the path, its start before it and its end past it
     */
    public Vec3 at(double distance)
    {
        if (distance <= 0)
            return samples[0];
        if (distance >= length())
            return samples[samples.length - 1];

        int low = 0, high = samples.length - 1;
        while (high - low > 1)
        {
            int middle = (low + high) / 2;
            if (reached[middle] <= distance)
                low = middle;
            else
                high = middle;
        }
        double piece = reached[high] - reached[low];
        return piece < 1e-9 ? samples[low] : samples[low].lerp(samples[high], (distance - reached[low]) / piece);
    }
}
