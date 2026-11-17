package net.voxelindustry.voidheart.common.energy.pearl;

/**
 * When a pearl shudders. Time is cut into slots, and each slot may hold one short burst at a place in it drawn from a seed, so the bursts come at
 * irregular gaps and are still between. A pearl among others draws some slots from its choir's seed instead of its own, more of them as it falls
 * into step, so the choir's bursts slowly line up. Worked out from the time alone, so every client sees the same thing. Pure, and unit tested.
 */
public final class PearlTremor
{
    public static final int    SLOT_TICKS   = 30;
    public static final int    BURST_TICKS  = 9;
    public static final double BURST_CHANCE = 0.55;

    /**
     * Ticks a pearl takes to fall fully into step with its choir.
     */
    public static final int SYNC_TICKS = 60 * 20;

    /**
     * A moment of the tremor.
     *
     * @param intensity from 0 when still to 1 at the height of a burst
     * @param shared    whether this burst was drawn from the choir's seed, so the choir shudders together
     */
    public record Moment(float intensity, boolean shared)
    {
        public static final Moment STILL = new Moment(0, false);
    }

    private PearlTremor()
    {
    }

    /**
     * @param seed      the pearl's own seed
     * @param choirSeed the seed its choir shares, the same as its own when alone
     * @param sync      how far it has fallen into step, from 0 to 1
     * @param time      ticks, with the part of a tick
     */
    public static Moment at(long seed, long choirSeed, float sync, double time)
    {
        var slot = (long) Math.floor(time / SLOT_TICKS);
        // The pearl whose seed the choir took keeps its own, so all of its bursts are the choir's once it has one
        var shared = sync > 0 && (choirSeed == seed || unit(mix(seed, slot, 1)) < sync);
        var drawn = shared ? choirSeed : seed;

        if (unit(mix(drawn, slot, 2)) >= BURST_CHANCE)
            return Moment.STILL;

        var start = slot * SLOT_TICKS + unit(mix(drawn, slot, 3)) * (SLOT_TICKS - BURST_TICKS);
        var into = (time - start) / BURST_TICKS;
        if (into < 0 || into >= 1)
            return Moment.STILL;
        return new Moment((float) Math.sin(Math.PI * into), shared);
    }

    /**
     * A pearl's own seed, from where it hangs.
     */
    public static long seedOf(long packedPos)
    {
        return mix(packedPos, 0x5EED, 0);
    }

    /**
     * A jolt for one part of a pearl on one tick, from -1 to 1, so parts jump apart rather than sway together.
     */
    public static float jolt(long seed, long tick, int part, int axis)
    {
        return (float) (unit(mix(seed ^ part * 0x9E3779B97F4A7C15L, tick, 4 + axis)) * 2 - 1);
    }

    private static long mix(long seed, long slot, int salt)
    {
        var z = seed + slot * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long hash)
    {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
