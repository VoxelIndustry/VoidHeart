package net.voxelindustry.voidheart.common.energy.machine;

import java.util.function.LongUnaryOperator;

/**
 * Pays a machine's steady draw tick by tick. What a weak supply gives is kept toward the next tick rather than lost, so the machine slows down instead of
 * stopping, and only stops with nothing at all.
 */
public final class DrawCredit
{
    private long credit;
    private long lastDrawn;

    /**
     * @param draw takes up to the amount asked from the network and returns what it took
     * @return whether need was covered, and spent, this tick
     */
    public boolean pay(long need, LongUnaryOperator draw)
    {
        lastDrawn = 0;
        if (need <= 0)
            return true;
        if (credit < need)
        {
            lastDrawn = Math.max(0, draw.applyAsLong(need - credit));
            credit += lastDrawn;
        }
        if (credit < need)
            return false;
        credit -= need;
        return true;
    }

    public long lastDrawn()
    {
        return lastDrawn;
    }

    public long credit()
    {
        return credit;
    }
}
