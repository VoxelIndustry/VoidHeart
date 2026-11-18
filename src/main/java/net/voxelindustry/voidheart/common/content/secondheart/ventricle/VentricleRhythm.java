package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * How the Twin Ventricle's two basins pulse. While kindling each keeps its own beat, out of step, and the beats draw together as the kindling
 * nears its end. While beating they lock into one heartbeat: the essence basin on the lub, the Contravoid basin and the cradle on the dub.
 * <p>
 * Pure, so the rhythm is tested without a client.
 */
public final class VentricleRhythm
{
    /** How fast a pulse dies away, per tick. */
    public static final float DECAY      = 0.28F;
    /** The dub follows the lub by this much of a beat. */
    public static final float DUB        = 0.18F;
    /** Ticks a pulse takes to run along a strand from its basin to the cradle. */
    public static final float STRAND_RUN = 8;

    private static final float KINDLE_OFFSET = 0.5F;

    private VentricleRhythm()
    {
    }

    /**
     * @param essence    pulse of the essence basin, 0 to 1
     * @param contravoid pulse of the Contravoid basin
     * @param cradle     the cradle's glow
     * @param essenceRun how far the essence strand's pulse has run toward the cradle, 0 to 1, or -1 for none
     * @param contravoidRun likewise for the Contravoid strand
     */
    public record Pulses(float essence, float contravoid, float cradle, float essenceRun, float contravoidRun)
    {
        public static final Pulses NONE = new Pulses(0, 0, 0, -1, -1);
    }

    public static float pulse(float sinceBeat)
    {
        if (sinceBeat < 0)
            return 0;
        return (float) Math.exp(-sinceBeat * DECAY);
    }

    private static float run(float sinceBeat)
    {
        return sinceBeat < 0 || sinceBeat > STRAND_RUN ? -1 : sinceBeat / STRAND_RUN;
    }

    /**
     * @param ticks ticks into the kindling
     */
    public static Pulses kindling(float ticks)
    {
        float progress = Math.clamp(ticks / KINDLING_TICKS, 0, 1);
        float essencePeriod = lerp(progress, LEFT_KINDLE_BEAT, BEAT_TICKS);
        float contravoidPeriod = lerp(progress, RIGHT_KINDLE_BEAT, BEAT_TICKS);
        float offset = lerp(progress, KINDLE_OFFSET, DUB);
        float essenceSince = since(ticks, essencePeriod, 0);
        float contravoidSince = since(ticks, contravoidPeriod, offset);
        // Faint at first, the chamber warming
        float warmth = 0.35F + 0.65F * progress;
        return new Pulses(warmth * pulse(essenceSince), warmth * pulse(contravoidSince), 0, run(essenceSince), run(contravoidSince));
    }

    /**
     * @param ticks ticks into the beating
     */
    public static Pulses beating(float ticks)
    {
        float lub = since(ticks, BEAT_TICKS, 0);
        float dub = since(ticks, BEAT_TICKS, DUB);
        return new Pulses(pulse(lub), pulse(dub), pulse(dub), run(lub), run(dub));
    }

    /**
     * @return ticks since the last beat of a pulse with this period, shifted later by a share of it
     */
    static float since(float ticks, float period, float offset)
    {
        float shifted = ticks - offset * period;
        return shifted - (float) Math.floor(shifted / period) * period;
    }

    private static float lerp(float t, float from, float to)
    {
        return from + (to - from) * t;
    }
}
