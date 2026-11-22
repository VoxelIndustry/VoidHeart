package net.voxelindustry.voidheart.common.content.levitation;

import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.*;

/**
 * How the belt's lift lets go of a wearer who flew out of a field, from the ticks since it started fading. Pure, so it is unit tested.
 * <p>
 * Gravity comes back in a straight line, and the fastest descent allowed rises with the cube of the fade, so the wearer drifts for the first seconds and
 * is falling plainly by the end. Falling only counts toward fall damage once the first part of the fade is over.
 */
public final class BeltLift
{
    private BeltLift()
    {
    }

    /**
     * From 0 when the lift starts fading to 1 once it is gone. Ticks before the start, as a client trailing the server sees them, count as the start.
     */
    public static double progress(long elapsed)
    {
        return Math.clamp(elapsed / (double) FADE_TICKS, 0, 1);
    }

    public static boolean fading(long elapsed)
    {
        return progress(elapsed) < 1;
    }

    /**
     * Share of gravity a falling wearer feels. Rising, they feel all of it, so a wearer flying up when the field is lost doesn't float on.
     */
    public static double gravityScale(long elapsed, boolean falling)
    {
        if (!falling || !fading(elapsed))
            return 1;
        return FADE_GRAVITY_START + (1 - FADE_GRAVITY_START) * progress(elapsed);
    }

    /**
     * Fastest the wearer may sink in blocks a tick, unbounded once the lift is gone.
     */
    public static double maxDescent(long elapsed)
    {
        if (!fading(elapsed))
            return Double.POSITIVE_INFINITY;
        double p = progress(elapsed);
        return FADE_DRIFT + (PLAIN_FALL_TOP - FADE_DRIFT) * p * p * p;
    }

    /**
     * Whether falling now still doesn't count toward fall damage.
     */
    public static boolean cushioned(long elapsed)
    {
        return progress(elapsed) < FADE_CUSHIONED;
    }

    /**
     * The vertical speed after one tick of the faded lift, as {@code travel} works it out in open air: gravity, then air drag, then the descent cap.
     */
    public static double step(double velocityY, long elapsed, double gravity)
    {
        double next = (velocityY - gravity * gravityScale(elapsed, velocityY <= 0)) * 0.98;
        return Math.max(next, -maxDescent(elapsed));
    }
}
