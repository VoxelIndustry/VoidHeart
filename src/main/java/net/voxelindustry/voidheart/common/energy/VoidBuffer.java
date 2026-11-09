package net.voxelindustry.voidheart.common.energy;

/**
 * A plain holder with a fixed capacity and rate, for machines that keep a little energy on hand.
 */
public class VoidBuffer implements IVoidHolder
{
    private final long capacity;
    private final long maxRate;
    private       long stored;

    public VoidBuffer(long capacity, long maxRate)
    {
        this.capacity = Math.max(0, capacity);
        this.maxRate = Math.max(0, maxRate);
    }

    @Override
    public long stored()
    {
        return stored;
    }

    @Override
    public long capacity()
    {
        return capacity;
    }

    @Override
    public long maxRate()
    {
        return maxRate;
    }

    /**
     * Saved values may come from a larger buffer, so they are clamped to this one.
     */
    public void setStored(long stored)
    {
        this.stored = Math.clamp(stored, 0, capacity);
    }

    @Override
    public long insert(long amount, boolean simulate)
    {
        long accepted = Math.clamp(Math.min(amount, maxRate), 0, capacity - stored);
        if (!simulate)
            stored += accepted;
        return accepted;
    }

    @Override
    public long extract(long amount, boolean simulate)
    {
        long taken = Math.clamp(Math.min(amount, maxRate), 0, stored);
        if (!simulate)
            stored -= taken;
        return taken;
    }
}
