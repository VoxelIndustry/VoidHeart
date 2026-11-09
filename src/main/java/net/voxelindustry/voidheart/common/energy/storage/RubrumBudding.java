package net.voxelindustry.voidheart.common.energy.storage;

/**
 * The bud a construct grows while it stays above the threshold. Time spent below pauses it rather than undoing it,
 * so a brief draw never throws away hours. A ready bud waits until the construct can pay for it.
 */
public class RubrumBudding
{
    public enum Stage
    {
        NONE,
        SPECK,
        SHARD,
        READY
    }

    private final float threshold;
    private final long  duration;
    private final long  cost;

    private long progress;

    public RubrumBudding()
    {
        this(RubrumBalance.BUD_THRESHOLD, RubrumBalance.BUD_TICKS, RubrumBalance.BUD_COST);
    }

    public RubrumBudding(float threshold, long duration, long cost)
    {
        this.threshold = threshold;
        this.duration = Math.max(1, duration);
        this.cost = cost;
    }

    public void tick(float fill, long elapsed)
    {
        if (fill >= threshold && elapsed > 0)
            progress = Math.min(duration, progress + elapsed);
    }

    public Stage stage()
    {
        if (progress <= 0)
            return Stage.NONE;
        if (progress >= duration)
            return Stage.READY;
        return progress * 2 < duration ? Stage.SPECK : Stage.SHARD;
    }

    public boolean canGrow(long stored)
    {
        return stage() == Stage.READY && stored >= cost;
    }

    public long cost()
    {
        return cost;
    }

    public void grown()
    {
        progress = 0;
    }

    public long progress()
    {
        return progress;
    }

    public void setProgress(long progress)
    {
        this.progress = Math.clamp(progress, 0, duration);
    }

    public float share()
    {
        return progress / (float) duration;
    }

    public long duration()
    {
        return duration;
    }
}
