package net.voxelindustry.voidheart.client.curse;

import net.voxelindustry.voidheart.common.curse.CurseTimings;

/**
 * When the flesh comes out of the screen's edges, when the heart beats and when it all snaps back. Time is in ticks since the curse struck.
 */
public final class HymnTimeline
{
    public static final int TICKS = CurseTimings.HYMN_TICKS;

    /** The flesh holds out until here, then whips back out of sight. */
    public static final int RETRACT_TICKS = 7;
    public static final int RETRACT_FROM  = TICKS - RETRACT_TICKS;

    private static final int   FIRST_BEAT  = 2;
    private static final float FIRST_GAP   = 20;
    private static final float GAP_SHRINK  = 0.8F;
    private static final float LEAST_GAP   = 4.5F;
    /** How long the flesh stays swollen after a beat. */
    private static final float PULSE_DECAY = 3.5F;

    private static final float FIRST_PITCH  = 0.75F;
    private static final float LAST_PITCH   = 1.15F;
    private static final float FIRST_VOLUME = 0.6F;
    private static final float LAST_VOLUME  = 1;

    private static final int[] BEATS = beats();

    private HymnTimeline()
    {
    }

    private static int[] beats()
    {
        var beats = new java.util.ArrayList<Integer>();
        float at = FIRST_BEAT;
        float gap = FIRST_GAP;
        while (Math.round(at) < RETRACT_FROM)
        {
            beats.add(Math.round(at));
            at += gap;
            gap = Math.max(LEAST_GAP, gap * GAP_SHRINK);
        }
        return beats.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The ticks the heart beats at, closer and closer together.
     */
    public static int[] beatTicks()
    {
        return BEATS.clone();
    }

    /**
     * @return the beat falling on this tick, or -1
     */
    public static int beatAt(int tick)
    {
        for (int beat = 0; beat < BEATS.length; beat++)
            if (BEATS[beat] == tick)
                return beat;
        return -1;
    }

    public static float pitch(int beat)
    {
        return lerp(quickening(beat), FIRST_PITCH, LAST_PITCH);
    }

    public static float volume(int beat)
    {
        return lerp(quickening(beat), FIRST_VOLUME, LAST_VOLUME);
    }

    private static float quickening(int beat)
    {
        return BEATS.length < 2 ? 1 : beat / (float) (BEATS.length - 1);
    }

    /**
     * How swollen the flesh is from the last beat, 1 right on it and fading after.
     */
    public static float pulse(float time)
    {
        float last = -1;
        for (int beat : BEATS)
            if (beat <= time)
                last = beat;
        return last < 0 ? 0 : (float) Math.exp(-(time - last) / PULSE_DECAY) * held(time);
    }

    /**
     * How far something that set off at {@code start} and takes {@code crawl} ticks to come all the way has come out, from 0 to 1.
     * It slows as it arrives, and everything goes back together at the end.
     */
    public static float extent(float time, float start, float crawl)
    {
        float out = clamp((time - start) / crawl);
        float left = 1 - out;
        return (1 - left * left * left) * held(time);
    }

    /**
     * 1 while the curse holds, falling to 0 faster and faster as it lets go.
     */
    public static float held(float time)
    {
        float back = clamp((time - RETRACT_FROM) / RETRACT_TICKS);
        return 1 - back * back;
    }

    static float clamp(float value)
    {
        return Math.max(0, Math.min(1, value));
    }

    static float lerp(float delta, float from, float to)
    {
        return from + (to - from) * delta;
    }
}
