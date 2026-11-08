package net.voxelindustry.voidheart.common.content.essence;

/**
 * How far the pillars of a Ravenous Storage rise for the essence it holds.
 */
public final class StorageFill
{
    public static final int MAX_FILL = 9;

    private StorageFill()
    {
    }

    /**
     * Any essence at all shows the first step, only a full storage the last.
     */
    public static int fillOf(int amount, int capacity)
    {
        if (amount <= 0 || capacity <= 0)
            return 0;
        return (int) Math.clamp((long) Math.ceil(amount * (double) MAX_FILL / capacity), 1, MAX_FILL);
    }

    /**
     * Height in pixels from the bottom of the base to the top of the pillar caps: just out of the base when empty,
     * the top of the block above when full.
     */
    public static int pillarTop(int fill)
    {
        return 14 + 2 * fill;
    }
}
