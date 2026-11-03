package net.voxelindustry.voidheart.client.scene;

import java.util.Arrays;

/**
 * Where a scene's steps fall in time. Everything shown is worked out from a tick alone, so jumping to a step needs no replaying of what came before.
 * A step lasts as long as its animation or as long as its text takes to read, whichever is longer, so that a translation can never outrun its reader.
 */
public final class SceneTimeline
{
    private static final float TICKS_PER_CHARACTER = 1.1F;
    private static final int   READING_LEAD        = 30;

    private final int[] starts;
    private final int   total;

    public SceneTimeline(int[] durations)
    {
        if (durations.length == 0)
            throw new IllegalArgumentException("A scene needs at least one step");

        starts = new int[durations.length];
        int at = 0;
        for (int step = 0; step < durations.length; step++)
        {
            starts[step] = at;
            at += durations[step];
        }
        total = at;
    }

    public static int duration(int animationTicks, int textLength)
    {
        return Math.max(animationTicks, READING_LEAD + (int) Math.ceil(textLength * TICKS_PER_CHARACTER));
    }

    public int total()
    {
        return total;
    }

    public int steps()
    {
        return starts.length;
    }

    public int startOf(int step)
    {
        return starts[step];
    }

    public int at(int step, int offset)
    {
        return starts[step] + offset;
    }

    public int stepAt(float ticks)
    {
        int found = Arrays.binarySearch(starts, (int) Math.floor(ticks));
        int step = found >= 0 ? found : -found - 2;
        return Math.clamp(step, 0, starts.length - 1);
    }

    /**
     * How far along the whole timeline a tick is, with each step given an equal share so that the marks stay evenly spaced however long the text is.
     */
    public float share(float ticks)
    {
        int step = stepAt(ticks);
        int end = step + 1 < starts.length ? starts[step + 1] : total;
        float within = Math.clamp((ticks - starts[step]) / Math.max(1, end - starts[step]), 0, 1);
        return (step + within) / starts.length;
    }
}
