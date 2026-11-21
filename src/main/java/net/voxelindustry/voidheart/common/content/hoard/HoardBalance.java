package net.voxelindustry.voidheart.common.content.hoard;

import net.voxelindustry.voidheart.common.content.crystal.BundleSize;

/**
 * Numbers of the hoard, guesses until tuned from play.
 */
public final class HoardBalance
{
    /** Void energy a Fold draws for each item moved through it. */
    public static final long FOLD_COST_PER_ITEM = 2;

    private HoardBalance()
    {
    }

    /**
     * @return how many items a coffer holding a rubrum bundle of that size adds to its pocket's hoard
     */
    public static long cofferCapacity(BundleSize size)
    {
        return switch (size)
        {
            case SMALL -> 4_096;
            case MEDIUM -> 16_384;
            case LARGE -> 65_536;
        };
    }
}
