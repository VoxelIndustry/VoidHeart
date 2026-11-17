package net.voxelindustry.voidheart.common.energy.pearl;

/**
 * How a pearl hung on a chain grows: ticks of full growth, sped by the pearls reinforcing it and slowed by whatever it lacks of Fiery or void
 * energy. Going short only pauses it, so nothing it gained is ever lost. Pure, and unit tested.
 */
public final class PearlGrowth
{
    /**
     * What a pearl looks like as it grows: a speck, three steps of folding, then mature.
     */
    public enum Stage
    {
        SPECK,
        BUDDING,
        FOLDING,
        COLLAPSING,
        MATURE;

        public static Stage byOrdinal(int ordinal)
        {
            var values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SPECK;
        }
    }

    /**
     * What a tick of feeding gave.
     *
     * @param supply share of a full tick's growth fed, from 0 to 1
     * @param fiery  whole Fiery to take out of the tank this tick
     * @param credit Fiery taken from the tank and not yet drunk, always below one point
     */
    public record Feed(double supply, double fiery, double credit)
    {
    }

    private final long duration;
    private double progress;

    public PearlGrowth()
    {
        this(PearlBalance.GROWTH_TICKS);
    }

    public PearlGrowth(long duration)
    {
        this.duration = Math.max(1, duration);
    }

    /**
     * How much faster a pearl grows among others growing in reach, counting itself. Six give the full boost, and more add nothing.
     */
    public static double boost(int choir)
    {
        var counted = Math.clamp(choir, 1, PearlBalance.MAX_CHOIR);
        return 1 + (PearlBalance.FULL_BOOST - 1) * (counted - 1) / (PearlBalance.MAX_CHOIR - 1);
    }

    /**
     * The share of a tick a pearl can be fed, from the Fiery it holds and the void energy the field gave against what it asked.
     *
     * @param fieryHeld Fiery in its tank and credit together
     */
    public static double fieryShare(double fieryHeld)
    {
        if (PearlBalance.FIERY_PER_TICK <= 0)
            return 1;
        return Math.clamp(fieryHeld / PearlBalance.FIERY_PER_TICK, 0, 1);
    }

    /**
     * Void energy to ask the field for, so a pearl short of Fiery doesn't take power it can't use.
     */
    public static long voidWanted(double fieryShare)
    {
        return (long) Math.ceil(PearlBalance.VOID_DRAW * Math.clamp(fieryShare, 0, 1) - 1e-9);
    }

    /**
     * Feeds a tick: the lesser of the two shares sets the growth, and only the Fiery that growth drinks is spent.
     *
     * @param credit   Fiery taken from the tank before and not yet drunk
     * @param tank     whole Fiery left in the tank
     * @param voidGot  void energy the field gave this tick
     */
    public static Feed feed(double credit, int tank, long voidGot)
    {
        var fieryShare = fieryShare(credit + tank);
        var voidShare = PearlBalance.VOID_DRAW <= 0 ? 1 : Math.clamp(voidGot / (double) PearlBalance.VOID_DRAW, 0, 1);
        var supply = Math.min(fieryShare, voidShare);
        var drunk = supply * PearlBalance.FIERY_PER_TICK;

        // Whole points come out of the tank only once the credit runs short, and what's left of them waits for the next tick
        var owed = Math.max(0, drunk - credit);
        var taken = Math.clamp(Math.ceil(owed - 1e-9), 0, Math.max(0, tank));
        var left = credit + taken - drunk;
        return new Feed(supply, taken, Math.max(0, left));
    }

    /**
     * Grows by one tick at this boost and supply, and says whether it has just matured.
     */
    public boolean tick(double boost, double supply)
    {
        if (isMature() || supply <= 0)
            return false;
        progress = Math.min(duration, progress + Math.max(0, boost) * Math.min(1, supply));
        return isMature();
    }

    public boolean isMature()
    {
        return progress >= duration;
    }

    public double progress()
    {
        return progress;
    }

    public void setProgress(double progress)
    {
        this.progress = Double.isFinite(progress) ? Math.clamp(progress, 0, duration) : 0;
    }

    public long duration()
    {
        return duration;
    }

    /**
     * How far it has grown, from 0 to 1.
     */
    public float share()
    {
        return (float) (progress / duration);
    }

    public Stage stage()
    {
        return stageOf(share());
    }

    public static Stage stageOf(float share)
    {
        if (share >= 1)
            return Stage.MATURE;
        if (share >= 0.75F)
            return Stage.COLLAPSING;
        if (share >= 0.5F)
            return Stage.FOLDING;
        if (share >= 0.25F)
            return Stage.BUDDING;
        return Stage.SPECK;
    }
}
