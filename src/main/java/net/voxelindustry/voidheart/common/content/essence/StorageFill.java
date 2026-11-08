package net.voxelindustry.voidheart.common.content.essence;

/**
 * How far the pillars of a Ravenous Storage rise for the essence it holds.
 */
public final class StorageFill
{
    public static final int MAX_FILL = 9;
    /** Steps of each column of a storage matrix, which only reach across one block. */
    public static final int MATRIX_MAX_FILL = 7;

    private StorageFill()
    {
    }

    /**
     * Any essence at all shows the first step, only a full storage the last.
     */
    public static int fillOf(int amount, int capacity)
    {
        return fillOf(amount, capacity, MAX_FILL);
    }

    public static int fillOf(int amount, int capacity, int maxFill)
    {
        if (amount <= 0 || capacity <= 0)
            return 0;
        return (int) Math.clamp((long) Math.ceil(amount * (double) maxFill / capacity), 1, maxFill);
    }

    /**
     * Height in pixels from the bottom of the base to the top of the pillar caps: just out of the base when empty,
     * the top of the block above when full.
     */
    public static int pillarTop(int fill)
    {
        return 14 + 2 * fill;
    }

    /**
     * How far in pixels a column of a storage matrix reaches into the block beside its core, caps included: a stub
     * when empty, the whole block when full.
     */
    public static int matrixColumnLength(int fill)
    {
        return 2 + 2 * fill;
    }
}
