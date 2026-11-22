package net.voxelindustry.voidheart.common.energy.storage;

/**
 * Pure rules of the Large Rubrum Cell's charge: how much it loses while not being charged, how many segments of its
 * column glow, and what a comparator reads.
 */
public final class LargeCellCharge
{
    public static final int  SEGMENTS       = 4;
    public static final long TICKS_PER_HOUR = 72_000;

    /** Loss is counted in parts of this size, so a capacity's 1% an hour is kept exact over any number of ticks. */
    private static final long PARTS_PER_ENERGY = 100 * TICKS_PER_HOUR / RubrumBalance.LARGE_CELL_DECAY_PERCENT;

    private LargeCellCharge()
    {
    }

    /**
     * @param loss  whole energy lost
     * @param carry the part of an energy lost so far and not yet taken, to pass to the next call
     */
    public record Decay(long loss, long carry)
    {
    }

    /**
     * What a cell loses over idle ticks, losing its capacity's {@link RubrumBalance#LARGE_CELL_DECAY_PERCENT}% an hour.
     *
     * @param carry what the last call returned, or 0
     */
    public static Decay decay(long carry, long idleTicks, long capacity)
    {
        if (idleTicks <= 0 || capacity <= 0)
            return new Decay(0, Math.clamp(carry, 0, PARTS_PER_ENERGY - 1));
        // Long enough idle to lose everything, whose parts would overflow
        if (idleTicks >= PARTS_PER_ENERGY)
            return new Decay(capacity, 0);
        var parts = Math.clamp(carry, 0, PARTS_PER_ENERGY - 1) + idleTicks * capacity;
        return new Decay(parts / PARTS_PER_ENERGY, parts % PARTS_PER_ENERGY);
    }

    /**
     * Glowing segments of the column: none when empty, then one per started quarter.
     */
    public static int segments(long charge, long capacity)
    {
        if (charge <= 0 || capacity <= 0)
            return 0;
        return (int) Math.clamp(Math.ceilDiv(Math.min(charge, capacity) * SEGMENTS, capacity), 1, SEGMENTS);
    }

    /**
     * A comparator reads how full the cell is, and at least 1 while any charge is held.
     */
    public static int comparatorSignal(long charge, long capacity)
    {
        if (charge <= 0 || capacity <= 0)
            return 0;
        return 1 + (int) Math.floor(14D * Math.min(charge, capacity) / capacity);
    }
}
