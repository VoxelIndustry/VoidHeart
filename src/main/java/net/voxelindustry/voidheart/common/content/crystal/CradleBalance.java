package net.voxelindustry.voidheart.common.content.crystal;

/**
 * The cradle's numbers. Guesses for the tuning sheet.
 */
public final class CradleBalance
{
    /** Ley the cradle asks of its stone while growing, the same as a grower. */
    public static final double LEY_NEED = 1.0;

    /** Ticks to grow one shard's worth at full supply: 20 minutes, so 80 for a large bundle. */
    public static final int TICKS_PER_WORTH = 20 * 60 * 20;

    /** How often a growing cradle tells its watchers how far it is. */
    public static final int SYNC_TICKS = 20;

    private CradleBalance()
    {
    }
}
