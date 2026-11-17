package net.voxelindustry.voidheart.common.energy.pearl;

/**
 * The fractal seeds a burning Contravoid reactor throws off, counted in pairs cancelled so a slow burn makes them slower. Pure, and unit tested.
 *
 * @param progress pairs counted toward the next seed
 * @param seeds    seeds ready to throw off
 */
public record PearlSeeds(double progress, int seeds)
{
    public static PearlSeeds add(double progress, double burned)
    {
        var total = Math.max(0, Double.isFinite(progress) ? progress : 0) + Math.max(0, burned);
        var seeds = (int) Math.floor(total / PearlBalance.SEED_PAIRS);
        return new PearlSeeds(total - seeds * PearlBalance.SEED_PAIRS, seeds);
    }
}
