package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.world.phys.Vec3;

/**
 * The hole of darkness a thrown spear opens where it lands: how big it is through its life, when it crushes, and how hard it pulls. It opens from a
 * point, holds, pinches a little at each crush, then shrinks back to a point before the spear tears free.
 */
public final class SpearHole
{
    private SpearHole()
    {
    }

    /** Whether the hole has closed, and the spear can fly back. */
    public static boolean isClosed(int age)
    {
        return age >= BlightBalance.HOLE_TICKS;
    }

    /** Whether it crushes what is inside on this tick of its life: every so often while it is fully open. */
    public static boolean crushesAt(int age)
    {
        return age >= BlightBalance.HOLE_OPEN_TICKS && age < BlightBalance.HOLE_TICKS && (age - BlightBalance.HOLE_OPEN_TICKS) % BlightBalance.HOLE_CRUSH_EVERY == 0;
    }

    /** How open it is, from 0 a point to 1 its full size, before any pinch. */
    public static float openness(float age)
    {
        if (age < 0 || age >= BlightBalance.HOLE_TICKS)
            return 0;
        if (age < BlightBalance.HOLE_OPEN_TICKS)
        {
            float t = age / BlightBalance.HOLE_OPEN_TICKS;
            return 1 - (1 - t) * (1 - t);
        }
        float left = BlightBalance.HOLE_TICKS - age;
        if (left < BlightBalance.HOLE_CLOSE_TICKS)
        {
            float t = left / BlightBalance.HOLE_CLOSE_TICKS;
            return t * t;
        }
        return 1;
    }

    /** Its size against its full one: how open it is, pinched for a moment after each crush. */
    public static float scale(float age)
    {
        float open = openness(age);
        if (age < BlightBalance.HOLE_OPEN_TICKS)
            return open;
        float sinceCrush = (age - BlightBalance.HOLE_OPEN_TICKS) % BlightBalance.HOLE_CRUSH_EVERY;
        float pinch = sinceCrush < BlightBalance.HOLE_PINCH_TICKS ? 1 - sinceCrush / BlightBalance.HOLE_PINCH_TICKS : 0;
        return open * (1 - BlightBalance.HOLE_PINCH * pinch);
    }

    /**
     * The speed a mob is given toward the hole this tick: most of what it had is lost, so it is drawn straight in rather than swung round, harder the
     * closer it is. Inside the hole it is held near the middle.
     *
     * @param offset   from the mob's middle to the hole's
     * @param velocity the mob's speed now
     * @return its new speed, or the same one when it is out of reach
     */
    public static Vec3 pulled(Vec3 offset, Vec3 velocity)
    {
        double distance = offset.length();
        if (distance > BlightBalance.HOLE_PULL_RANGE)
            return velocity;
        if (distance < 1.0E-3)
            return velocity.scale(BlightBalance.HOLE_HOLD);
        var toward = offset.scale(1 / distance);
        if (distance < BlightBalance.HOLE_RADIUS)
            return velocity.scale(BlightBalance.HOLE_HOLD).add(toward.scale(Math.min(distance, BlightBalance.HOLE_PULL) * 0.5));
        double strength = BlightBalance.HOLE_PULL * (2 - distance / BlightBalance.HOLE_PULL_RANGE);
        return velocity.scale(BlightBalance.HOLE_DRAG).add(toward.scale(strength));
    }

    /** Whether a mob is inside the hole, so it is crushed: its box reaches within the hole's radius of the middle. */
    public static boolean isInside(double distanceFromBox)
    {
        return distanceFromBox <= BlightBalance.HOLE_RADIUS;
    }
}
