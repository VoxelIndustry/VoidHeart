package net.voxelindustry.voidheart.common.energy.ley;

/**
 * How many extra random ticks a grower hands out. Vanilla gives each block randomTickSpeed chances in 4096 every tick, so picking that many blocks at random
 * in the grower's volume, scaled by boost and supply, adds the same again to every plant in it. Fractions carry over between ticks. Pure, for tests.
 */
public final class GrowerTicks
{
    private static final float SECTION_BLOCKS = 16 * 16 * 16;

    private float carry;

    /**
     * Blocks to pick this tick.
     */
    public int next(int volume, int randomTickSpeed, float boost, float supply)
    {
        if (!(supply > 0) || randomTickSpeed <= 0 || volume <= 0)
            return 0;

        carry += volume * randomTickSpeed / SECTION_BLOCKS * boost * Math.min(supply, 1);
        int picks = (int) carry;
        carry -= picks;
        return picks;
    }

    public static int volume(int radius, int height)
    {
        int side = radius * 2 + 1;
        return side * side * (height * 2 + 1);
    }
}
