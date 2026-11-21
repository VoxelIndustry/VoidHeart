package net.voxelindustry.voidheart.common.content.crystal;

/**
 * Scoured rubrum set down near a ley node draws the node's taint into itself, a little each minute, until it holds about two thirds of what scouring it
 * cost. Then it is Fouled again, so moving taint off a line always dirties the line that scours it by more. Pure.
 */
public final class ScouredSoak
{
    /** Taint drawn in each time it soaks. */
    public static final int PER_SOAK = 1;

    /** How often it soaks: once a minute. */
    public static final int SOAK_TICKS = 20 * 60;

    /** How near a node, across the ground, it has to lie. */
    public static final int NODE_REACH = 8;

    /** The most any bundle holds, the large one's. */
    public static final int MAX = 13;

    private ScouredSoak()
    {
    }

    /**
     * What a bundle holds before it turns: about two thirds of the 5, 10 or 20 taint scouring it pushed into a line.
     */
    public static int capacity(BundleSize size)
    {
        return switch (size)
        {
            case SMALL -> 3;
            case MEDIUM -> 7;
            case LARGE -> MAX;
        };
    }

    /**
     * @param soaked taint the bundle holds after this soak, back to 0 once it turns
     * @param taken  taint drawn off the node now
     * @param fouled whether it is full and turned Fouled
     */
    public record Step(int soaked, int taken, boolean fouled)
    {
    }

    /**
     * One soak of a bundle holding soaked beside a node of that taint. A node with less than a soak's worth left is clean enough, and gives nothing.
     */
    public static Step step(BundleSize size, int soaked, float nodeTaint)
    {
        int held = Math.clamp(soaked, 0, capacity(size));
        if (!(nodeTaint >= PER_SOAK))
            return new Step(held, 0, false);
        held += PER_SOAK;
        if (held >= capacity(size))
            return new Step(0, PER_SOAK, true);
        return new Step(held, PER_SOAK, false);
    }
}
