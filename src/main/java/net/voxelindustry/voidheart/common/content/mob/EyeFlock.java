package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The three rules of a flock: keep off your closest neighbours, drift to the middle of the others and fly the way they fly.
 */
public final class EyeFlock
{
    public static final double SEPARATION_RADIUS = 1.6;

    private static final double SEPARATION_WEIGHT = 2.2;
    private static final double COHESION_WEIGHT = 1.1;
    private static final double ALIGNMENT_WEIGHT = 0.6;
    private static final double COHESION_FULL_PULL = 4;
    private static final double MIN_LENGTH_SQR = 1e-6;

    public record Member(Vec3 position, Vec3 velocity)
    {
    }

    private EyeFlock()
    {
    }

    public static Vec3 pull(Vec3 position, List<Member> neighbours)
    {
        if (neighbours.isEmpty())
            return Vec3.ZERO;

        var separation = Vec3.ZERO;
        var center = Vec3.ZERO;
        var heading = Vec3.ZERO;
        for (var neighbour : neighbours)
        {
            var away = position.subtract(neighbour.position());
            double distance = away.length();
            if (distance > 0 && distance < SEPARATION_RADIUS)
                separation = separation.add(away.scale((1 - distance / SEPARATION_RADIUS) / distance));
            center = center.add(neighbour.position());
            heading = heading.add(neighbour.velocity());
        }

        var toCenter = center.scale(1.0 / neighbours.size()).subtract(position);
        double pullShare = Math.min(toCenter.length() / COHESION_FULL_PULL, 1);
        var alignment = heading.lengthSqr() > MIN_LENGTH_SQR ? heading.normalize() : Vec3.ZERO;
        return separation.scale(SEPARATION_WEIGHT)
                .add(toCenter.normalize().scale(COHESION_WEIGHT * pullShare))
                .add(alignment.scale(ALIGNMENT_WEIGHT));
    }

    /**
     * What it should fly at, given what it wants, what its flock wants and what the walls push it off.
     */
    public static Vec3 velocity(Vec3 wish, Vec3 flock, Vec3 walls, double speed)
    {
        var sum = wish.add(flock).add(walls);
        return sum.lengthSqr() > MIN_LENGTH_SQR ? sum.normalize().scale(speed) : Vec3.ZERO;
    }
}
