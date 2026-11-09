package net.voxelindustry.voidheart.common.energy.reactor;

/**
 * The essences Contravoid cancels against, each giving its own share of a pair's energy. Pure, so the burn is tested without the essence registry.
 */
public enum ReactorEssence
{
    STILLNESS(ReactorBalance.STILLNESS_FACTOR),
    FIERY(ReactorBalance.FIERY_FACTOR),
    SUFFERING(ReactorBalance.SUFFERING_FACTOR);

    private final double factor;

    ReactorEssence(double factor)
    {
        this.factor = factor;
    }

    /**
     * Void energy one pair gives with this essence.
     */
    public double pairEnergy()
    {
        return ReactorBalance.PAIR_ENERGY * factor;
    }

    /**
     * Void energy a tick at the fullest burn.
     */
    public long fullOutput()
    {
        return (long) Math.floor(ReactorBalance.MAX_PAIRS * pairEnergy() + 1e-9);
    }
}
