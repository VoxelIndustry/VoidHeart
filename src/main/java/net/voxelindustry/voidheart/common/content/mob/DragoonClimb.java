package net.voxelindustry.voidheart.common.content.mob;

/**
 * How the Kernacian Dragoon climbs after a target it can't walk to: up the face in front of it, out from under a lip that stops its head, and over the top once
 * the face ends below its feet. Pure, so the entity only has to say what is around its body each tick.
 */
public final class DragoonClimb
{
    /**
     * How far above its feet a target must stand before it climbs rather than steps or jumps.
     */
    public static final double MIN_RISE = 1.5;
    public static final double SPEED = 0.1;
    /**
     * Toward the face while rising, so it stays against it.
     */
    public static final double PUSH = 0.02;
    /**
     * How far ahead a face counts as being in front of it.
     */
    public static final double PROBE = 0.2;
    /**
     * How far below its feet a top counts as something to step onto.
     */
    public static final double LEDGE_PROBE = 0.6;
    /**
     * How far in from its sides it must be over a top before it lets its weight down on it.
     */
    public static final double FOOTING = 0.3;
    /**
     * Its shoulders: a target no higher than this above its feet can be struck from the wall.
     */
    public static final double SWIPE_HEIGHT = 1.8;
    /**
     * It lets go once its target is this far below its feet.
     */
    public static final double LET_GO_BELOW = 1;
    /**
     * It edges out from under at most two and a half blocks of overhang before it gives up.
     */
    public static final int MAX_LIP_TICKS = 25;
    public static final int MAX_TICKS = 600;
    /**
     * After letting go of a wall it couldn't get up, it waits this long before trying again.
     */
    public static final int RETRY_TICKS = 60;

    public enum Step
    {
        /**
         * Up the face in front.
         */
        RISE,
        /**
         * Backward, out from under what stops its head.
         */
        LIP_OUT,
        /**
         * Forward over the top it has risen above.
         */
        TOP_OUT,
        /**
         * Standing over the top: the climb is done.
         */
        DONE,
        /**
         * Nothing in front to hold, so it drops.
         */
        LET_GO,
        /**
         * Its head is stopped and it can't edge out, so it drops and waits before trying again.
         */
        STUCK
    }

    /**
     * What is around its body.
     *
     * @param wallAhead    something in front of it, within {@link #PROBE}
     * @param ledgeAhead   something in front of it and below its feet, within {@link #LEDGE_PROBE}
     * @param groundBelow  something below its feet, within {@link #LEDGE_PROBE}, under more than its edges
     * @param headClear    room to rise {@link #SPEED}
     * @param outwardClear room to back away {@link #SPEED}
     */
    public record Probe(boolean wallAhead, boolean ledgeAhead, boolean groundBelow, boolean headClear, boolean outwardClear)
    {
    }

    private DragoonClimb()
    {
    }

    /**
     * Whether it starts climbing toward a target this far above its feet. It only climbs when walking can't get it there.
     */
    public static boolean wantsToClimb(double rise, boolean pathReaches)
    {
        return rise > MIN_RISE && !pathReaches;
    }

    /**
     * Whether it keeps climbing toward a target this far above its feet, after this many ticks on the wall.
     */
    public static boolean keepsClimbing(double rise, int ticks)
    {
        return rise > -LET_GO_BELOW && ticks < MAX_TICKS;
    }

    /**
     * Whether a target this far above its feet is low enough to strike from the wall.
     */
    public static boolean canSwipe(double rise)
    {
        return rise <= SWIPE_HEIGHT;
    }

    /**
     * @param last     what it did the tick before, {@link Step#RISE} on the first
     * @param lipTicks how many ticks in a row it has edged out from under a lip
     */
    public static Step next(Probe probe, Step last, int lipTicks)
    {
        // Once out from under a lip it rises past it, which brings the lip's outer face in front of it
        if (last == Step.LIP_OUT)
        {
            if (probe.headClear())
                return Step.RISE;
            return probe.outwardClear() && lipTicks < MAX_LIP_TICKS ? Step.LIP_OUT : Step.STUCK;
        }
        if (!probe.wallAhead())
        {
            if (probe.groundBelow())
                return Step.DONE;
            if (probe.ledgeAhead())
                return Step.TOP_OUT;
            return Step.LET_GO;
        }
        if (probe.headClear())
            return Step.RISE;
        return probe.outwardClear() ? Step.LIP_OUT : Step.STUCK;
    }

    /**
     * @return how it moves on this step, as forward toward the face and up, in blocks per tick
     */
    public static double[] motion(Step step)
    {
        return switch (step)
        {
            case RISE -> new double[] { PUSH, SPEED };
            case LIP_OUT -> new double[] { -SPEED, 0 };
            case TOP_OUT -> new double[] { SPEED, 0 };
            case DONE, LET_GO, STUCK -> new double[] { 0, 0 };
        };
    }
}
