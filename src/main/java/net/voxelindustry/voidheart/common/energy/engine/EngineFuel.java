package net.voxelindustry.voidheart.common.energy.engine;

/**
 * The essences a Ravenous Engine burns, each at its own pace. Pure, so the burn rates are tested without the essence registry.
 */
public enum EngineFuel
{
    STILLNESS(RavenousEngineBalance.STILLNESS_DENSITY, RavenousEngineBalance.STILLNESS_BURN),
    FIERY(RavenousEngineBalance.FIERY_DENSITY, RavenousEngineBalance.FIERY_BURN),
    SUFFERING(RavenousEngineBalance.SUFFERING_DENSITY, RavenousEngineBalance.SUFFERING_BURN);

    private final double density;
    private final int    burn;

    EngineFuel(double density, int burn)
    {
        this.density = density;
        this.burn = burn;
    }

    /**
     * Void energy one essence burns into.
     */
    public double density()
    {
        return density;
    }

    /**
     * Most essence burned in a tick.
     */
    public int burn()
    {
        return burn;
    }

    /**
     * Void energy a tick at full burn gives.
     */
    public long fullOutput()
    {
        return (long) Math.floor(burn * density);
    }
}
