package net.voxelindustry.voidheart.common.content.heart;

/**
 * When a pocket heart beats and how far it swells, worked out from the time and its owner's seed alone, so the renderer, the beat sound and the veins'
 * pulses agree without keeping any state.
 */
public final class HeartBeat
{
    /** A dormant heart's single slow swell. */
    public static final float DORMANT_PERIOD = 40;
    /** A rooted heart's lub-dub. */
    public static final float ROOTED_PERIOD  = 30;

    /** How far each beat may come early or late, so it doesn't tick like a clock. */
    private static final float JITTER_TICKS = 3;
    private static final float RISE_TICKS   = 4;
    private static final float FALL_TICKS   = 8;
    /** The dub follows the lub this late, and this much weaker. */
    public static final float  DUB_DELAY    = 7;
    public static final float  DUB_STRENGTH = 0.6F;

    private HeartBeat()
    {
    }

    /** The number of the latest beat started by this time. */
    public static int latest(float time, int seed, float period)
    {
        int index = (int) Math.floor(time / period);
        return time < start(index, seed, period) ? index - 1 : index;
    }

    /** Ticks since the latest beat started. */
    public static float since(float time, int seed, float period)
    {
        return time - start(latest(time, seed, period), seed, period);
    }

    public static float start(int index, int seed, float period)
    {
        int hash = index * 0x9E3779B1 ^ seed;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        hash ^= hash >>> 12;
        float jitter = ((hash & 0xFFFF) / 65535F * 2 - 1) * JITTER_TICKS;
        return index * period + jitter;
    }

    /** How far the heart has swelled this long after a beat started, 0 at rest and 1 at its fullest: once, or lub then dub. */
    public static float swell(float since, boolean lubDub)
    {
        float lub = pulse(since);
        return lubDub ? Math.max(lub, DUB_STRENGTH * pulse(since - DUB_DELAY)) : lub;
    }

    /** A quick rise then a slower ease back. */
    public static float pulse(float since)
    {
        if (since < 0)
            return 0;
        if (since < RISE_TICKS)
            return (float) Math.sin(Math.PI / 2 * since / RISE_TICKS);
        return (float) Math.exp(-(since - RISE_TICKS) / FALL_TICKS);
    }
}
