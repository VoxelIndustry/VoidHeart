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
     * @return how many items a coffer holding a rubrum bundle of that size adds to its pocket's hoard: a medium bundle holds a chest of full stacks
     */
    public static long cofferCapacity(BundleSize size)
    {
        return switch (size)
        {
            case SMALL -> 576;
            case MEDIUM -> 1_728;
            case LARGE -> 3_456;
        };
    }
}
