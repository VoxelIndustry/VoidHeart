package net.voxelindustry.voidheart.common.content.hoard;

import net.voxelindustry.voidheart.common.content.crystal.BundleSize;

/**
 * Numbers of the hoard, guesses until tuned from play.
 */
public final class HoardBalance
{
    /** Void energy a Fold draws for each item moved through it. */
    public static final long FOLD_COST_PER_ITEM = 2;

    /** Essence an Aspect Fold lets into the reserve for each unit of void energy. */
    public static final long ESSENCE_PER_ENERGY = 5;

    /** Void energy an Aspect Fold draws for each unit of Contravoid let in, ten times the toll of the rest. */
    public static final long CONTRAVOID_ENERGY_PER_ESSENCE = 2;

    private HoardBalance()
    {
    }

    /**
     * @return the void energy letting that much essence into the reserve costs
     */
    public static long toll(boolean contravoid, long essence)
    {
        if (essence <= 0)
            return 0;
        return contravoid ? essence * CONTRAVOID_ENERGY_PER_ESSENCE : (essence + ESSENCE_PER_ENERGY - 1) / ESSENCE_PER_ENERGY;
    }

    /**
     * @return how much essence that much void energy lets in
     */
    public static long affordableEssence(boolean contravoid, long energy)
    {
        if (energy <= 0)
            return 0;
        if (energy > Long.MAX_VALUE / ESSENCE_PER_ENERGY)
            return Long.MAX_VALUE;
        return contravoid ? energy / CONTRAVOID_ENERGY_PER_ESSENCE : energy * ESSENCE_PER_ENERGY;
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
