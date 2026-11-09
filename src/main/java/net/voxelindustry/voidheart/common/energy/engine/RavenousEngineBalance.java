package net.voxelindustry.voidheart.common.energy.engine;

/**
 * Numbers owned by the Ravenous Engine, copied from docs/plans/energy-numbers.md (N0). Densities are void energy per essence, burns are essence per tick.
 */
public final class RavenousEngineBalance
{
    /**
     * Sim: 30 VE/t, slow and steady. One Stillness engine is the yardstick of the whole unit scale.
     */
    public static final double STILLNESS_DENSITY = 2.5;
    public static final int    STILLNESS_BURN    = 12;

    /**
     * Sim: 48 VE/t, the fastest and the least per essence. It needs a full relay path of 25 essence/t to keep up.
     */
    public static final double FIERY_DENSITY = 2.0;
    public static final int    FIERY_BURN    = 24;

    /**
     * Sim: 40 VE/t, the densest.
     */
    public static final double SUFFERING_DENSITY = 4.0;
    public static final int    SUFFERING_BURN    = 10;

    /**
     * Guess: essence held per aspect.
     */
    public static final int TANK = 4_000;

    private RavenousEngineBalance()
    {
    }
}
