package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.util.Mth;

/**
 * How strongly a ward holds the air still, and what that does to a rise in corruption. Pure, so it can be tested without a level.
 */
public final class WardStrength
{
    private WardStrength()
    {
    }

    /**
     * Supply × (1 − 0.8 × taint/100) × (1 − seep), from 0 to 1.
     *
     * @param taint the taint of the line feeding the ward, from 0 to 100
     * @param seep  the void seep where the ward stands, from 0 to 1
     */
    public static float of(float supply, float taint, float seep)
    {
        return Mth.clamp(clamp01(supply) * health(taint, seep), 0, 1);
    }

    /**
     * What taint and seep leave of a fully supplied ward, from 0 to 1.
     */
    public static float health(float taint, float seep)
    {
        float taintCut = 1 - LeyStoneBalance.WARD_TAINT_CUT * Mth.clamp(Float.isNaN(taint) ? 0 : taint, 0, 100) / 100;
        return taintCut * (1 - clamp01(seep));
    }

    /**
     * The corruption multiplier for a player held by a ward of this strength. Never below 1 − WARD_SLOW, and never above 1, since a ward only slows the rise.
     */
    public static float multiplier(float strength)
    {
        return 1 - LeyStoneBalance.WARD_SLOW * clamp01(strength);
    }

    private static float clamp01(float value)
    {
        return Float.isNaN(value) ? 0 : Mth.clamp(value, 0, 1);
    }
}
