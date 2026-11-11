package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;

/**
 * The way a Klein Star takes to where it bursts when nothing stands between: never straight, but a curve that lifts and swings out to one side
 * before it comes down on its target, sometimes crossing over to the other side on the way. A cubic Bézier curve, the first and often the only
 * one of its {@link KleinStarPath}.
 *
 * @param first  what the star heads for as it leaves
 * @param second what it comes in from as it arrives
 */
public record KleinStarFlight(Vec3 from, Vec3 first, Vec3 second, Vec3 to)
{
    // Where the two inner points sit along the straight line
    private static final double FIRST_ALONG  = 0.25;
    private static final double SECOND_ALONG = 0.8;
    // How far they are pushed off it, against its length
    private static final double LIFT         = 0.3;
    private static final double LIFT_FLAT    = 1;
    private static final double SWAY         = 0.5;
    private static final double LATE_LIFT    = 0.2;
    private static final double LATE_SWAY    = 0.3;
    private static final int    LENGTH_STEPS = 16;

    /**
     * @param side  -1 to 1, which side it swings out to and how far
     * @param twist -1 to 1, where it comes in from: the same side at 1, the other at -1
     * @param swing 0 to 1, how much of the curve is kept: none at 0, which is the straight line
     */
    public static KleinStarFlight plan(Vec3 from, Vec3 to, double side, double twist, double swing)
    {
        var span = to.subtract(from);
        double length = span.length();
        if (length < 1e-6)
            return new KleinStarFlight(from, from, to, to);

        var heading = span.scale(1 / length);
        var up = new Vec3(0, 1, 0);
        var sideways = heading.cross(up);
        sideways = sideways.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : sideways.normalize();

        var first = from.add(span.scale(FIRST_ALONG))
                .add(up.scale((LIFT * length + LIFT_FLAT) * swing))
                .add(sideways.scale(side * SWAY * length * swing));
        var second = from.add(span.scale(SECOND_ALONG))
                .add(up.scale(LATE_LIFT * length * swing))
                .add(sideways.scale(side * twist * LATE_SWAY * length * swing));
        return new KleinStarFlight(from, first, second, to);
    }

    /**
     * @return the point this far along the curve, from 0 to 1
     */
    public Vec3 at(double along)
    {
        double t = Math.clamp(along, 0, 1);
        double u = 1 - t;
        return from.scale(u * u * u).add(first.scale(3 * u * u * t)).add(second.scale(3 * u * t * t)).add(to.scale(t * t * t));
    }

    public double length()
    {
        double length = 0;
        var last = from;
        for (int step = 1; step <= LENGTH_STEPS; step++)
        {
            var point = at(step / (double) LENGTH_STEPS);
            length += point.distanceTo(last);
            last = point;
        }
        return length;
    }
}
