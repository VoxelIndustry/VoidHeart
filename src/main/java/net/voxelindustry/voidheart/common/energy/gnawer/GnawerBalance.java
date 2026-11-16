package net.voxelindustry.voidheart.common.energy.gnawer;

/**
 * Every number of the Gnawer. All are guesses to tune from play.
 */
public final class GnawerBalance
{
    /**
     * The widest a box may be on either side.
     */
    public static final int    MAX_WIDTH         = 32;
    /**
     * How far from its box a Gnawer may stand, on any axis.
     */
    public static final int    RANGE             = 8;
    /**
     * Void energy per block, times its hardness, never below {@link #MIN_COST}. Stone costs 120.
     */
    public static final long   COST_PER_HARDNESS = 80;
    public static final long   MIN_COST          = 40;
    /**
     * Erasing costs this share of keeping.
     */
    public static final double ERASE_SHARE       = 0.5;
    /**
     * The most a Gnawer draws a tick, which sets its speed: about 10 stone a second.
     */
    public static final long   MAX_DRAW          = 64;
    /**
     * Blocks eaten in a tick at most, for cheap blocks such as sand.
     */
    public static final int    BLOCKS_PER_TICK   = 16;
    /**
     * Positions looked at in a tick at most, so a box full of air is crossed quickly without stalling the server.
     */
    public static final int    SCAN_PER_TICK     = 512;
    /**
     * Suffering essence from each erased block, handed to an essence storage beside the Gnawer.
     */
    public static final double ERASE_ESSENCE     = 0.1;

    private GnawerBalance()
    {
    }

    /**
     * @param hardness the block's destroy speed
     * @param fluid    whether it's a fluid, whose hardness means nothing here
     */
    public static long cost(float hardness, boolean fluid, boolean erase)
    {
        var keep = fluid ? MIN_COST : Math.max(MIN_COST, Math.round(hardness * COST_PER_HARDNESS));
        return erase ? Math.round(keep * ERASE_SHARE) : keep;
    }
}
