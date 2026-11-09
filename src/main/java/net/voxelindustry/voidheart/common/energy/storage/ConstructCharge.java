package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.energy.IVoidHolder;

import java.util.function.LongSupplier;

/**
 * The energy a construct holds. Its rate caps what goes in and what comes out over each tick, however many calls the
 * network makes in it, and it moves nothing while the construct is not formed.
 * <p>
 * Stored never exceeds capacity: the construct refuses to give up rubrum that would push it over.
 */
public class ConstructCharge implements IVoidHolder
{
    private final LongSupplier clock;

    private long    stored;
    private long    capacity;
    private long    rate;
    private boolean active;

    private long tick = Long.MIN_VALUE;
    private long inThisTick;
    private long outThisTick;
    private long lastTick = Long.MIN_VALUE;
    private long lastIn;
    private long lastOut;

    public ConstructCharge(LongSupplier clock)
    {
        this.clock = clock;
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
        return active ? rate : 0;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    /**
     * Sets what the sockets give. A capacity below what is stored only comes from stale saved data, so it is clamped.
     */
    public void setSockets(long capacity, long rate)
    {
        this.capacity = Math.max(0, capacity);
        this.rate = Math.max(0, rate);
        stored = Math.clamp(stored, 0, this.capacity);
    }

    public void setStored(long stored)
    {
        this.stored = Math.clamp(stored, 0, capacity);
    }

    public float fill()
    {
        return capacity <= 0 ? 0 : (float) ((double) stored / capacity);
    }

    /**
     * Whether rubrum giving this much capacity could leave without the stored energy overflowing what remains.
     */
    public boolean canLoseCapacity(long lost)
    {
        return stored <= capacity - lost;
    }

    private void roll()
    {
        var now = clock.getAsLong();
        if (now == tick)
            return;
        lastTick = tick;
        lastIn = inThisTick;
        lastOut = outThisTick;
        tick = now;
        inThisTick = 0;
        outThisTick = 0;
    }

    @Override
    public long insert(long amount, boolean simulate)
    {
        if (!active || amount <= 0)
            return 0;
        roll();
        var accepted = Math.max(0, Math.min(Math.min(amount, rate - inThisTick), capacity - stored));
        if (!simulate)
        {
            stored += accepted;
            inThisTick += accepted;
        }
        return accepted;
    }

    @Override
    public long extract(long amount, boolean simulate)
    {
        if (!active || amount <= 0)
            return 0;
        roll();
        var taken = Math.max(0, Math.min(Math.min(amount, rate - outThisTick), stored));
        if (!simulate)
        {
            stored -= taken;
            outThisTick += taken;
        }
        return taken;
    }

    /**
     * Takes energy out for the construct's own use, such as a bud, ignoring the rate.
     */
    public void spend(long amount)
    {
        stored = Math.max(0, stored - Math.max(0, amount));
    }

    /**
     * What went in minus what came out over the last whole tick before now, positive while charging.
     */
    public long lastNetFlow()
    {
        roll();
        if (lastTick != tick - 1)
            return 0;
        return lastIn - lastOut;
    }
}
