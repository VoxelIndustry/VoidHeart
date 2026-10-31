package net.voxelindustry.voidheart.client.render.oculus;

/**
 * Whether a hologram shows the surface of the land or the hollows inside it, as a blend gliding between the two:
 * 0 for the surface, 1 for the hollows. The table's own choice decides, unless it follows a player: then the
 * hollows show while they are under cover, after a while showing both as they go in or come out.
 */
final class OculusCaving
{
    /**
     * Blend at which both the surface, thinned out, and the hollows show.
     */
    static final float MIXED = 0.5F;
    /**
     * Share of the way between surface and hollows travelled per second.
     */
    private static final float BLEND_RATE = 1.5F;
    /**
     * Ticks a followed player must stay under cover or out in the open before it counts, so a doorway or a step
     * under a ledge does not start a switch.
     */
    static final int SETTLE_TICKS = 10;

    private boolean target;
    private boolean followedCovered;
    private int coverChangedTicks;
    private boolean wasFollowing;
    private long mixedUntil = Long.MIN_VALUE;
    private float blend = Float.NaN;

    /**
     * Called every tick with what decides the mode.
     *
     * @param chosen     whether the table itself is set to show the hollows
     * @param covered    whether the followed player is under cover, ignored when no one is followed
     * @param mixedTicks how long both show when the followed player goes in or out
     */
    void tick(long tick, boolean chosen, boolean following, boolean covered, int mixedTicks)
    {
        if (!following)
        {
            followedCovered = false;
            coverChangedTicks = 0;
            mixedUntil = Long.MIN_VALUE;
            target = chosen;
            wasFollowing = false;
            return;
        }

        if (!wasFollowing)
        {
            // Starting to follow someone settles on their surroundings at once
            followedCovered = covered;
            coverChangedTicks = 0;
            target = covered;
            wasFollowing = true;
            return;
        }

        coverChangedTicks = covered == followedCovered ? 0 : coverChangedTicks + 1;
        if (coverChangedTicks < SETTLE_TICKS)
            return;

        followedCovered = covered;
        coverChangedTicks = 0;
        target = covered;
        mixedUntil = tick + mixedTicks;
    }

    /**
     * Where the blend is headed at this tick.
     */
    float goal(long tick)
    {
        if (tick < mixedUntil)
            return MIXED;
        return target ? 1 : 0;
    }

    /**
     * Glides the drawn blend toward its goal. Called once a frame.
     */
    float advance(long tick, float seconds)
    {
        float goal = goal(tick);
        if (Float.isNaN(blend))
            blend = goal;
        else
        {
            float step = BLEND_RATE * seconds;
            blend = blend < goal ? Math.min(goal, blend + step) : Math.max(goal, blend - step);
        }
        return blend;
    }

    float blend()
    {
        return Float.isNaN(blend) ? 0 : blend;
    }
}
