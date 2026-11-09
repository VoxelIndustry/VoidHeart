package net.voxelindustry.voidheart.common.energy.machine;

/**
 * Sums a machine's draw over a second before judging it, so a supply that wavers from tick to tick doesn't make it flicker. One short tick in a second that
 * was otherwise paid still reads as powered, since the grid can miss the first tick of a new draw.
 */
public final class PowerMeter
{
    public static final int WINDOW = 20;

    private long         wanted;
    private long         got;
    private int          ticks;
    private int          shortTicks;
    private MachinePower state = MachinePower.IDLE;

    /**
     * @return whether the state changed, which only happens at the end of a window
     */
    public boolean record(long want, long taken)
    {
        wanted += Math.max(0, want);
        got += Math.max(0, taken);
        if (taken < want)
            shortTicks++;
        if (++ticks < WINDOW)
            return false;

        var next = MachinePower.of(wanted, got);
        if (next.isStarved() && shortTicks <= 1 && got > 0)
            next = MachinePower.POWERED;
        wanted = 0;
        got = 0;
        ticks = 0;
        shortTicks = 0;
        if (next == state)
            return false;
        state = next;
        return true;
    }

    public MachinePower state()
    {
        return state;
    }

    /**
     * Restores a state saved with the machine, so it doesn't light up for a second after a reload.
     */
    public void restore(MachinePower state)
    {
        this.state = state;
    }
}
