package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VentricleRhythmTest
{
    @Test
    void beating_lubThenDub_everyBeat()
    {
        for (int beat = 0; beat < 5; beat++)
        {
            float at = beat * BEAT_TICKS;
            var lub = VentricleRhythm.beating(at);
            assertThat(lub.essence()).isCloseTo(1, within(1.0E-4F));
            assertThat(lub.contravoid()).isLessThan(0.5F);
            var dub = VentricleRhythm.beating(at + VentricleRhythm.DUB * BEAT_TICKS);
            assertThat(dub.contravoid()).isCloseTo(1, within(1.0E-3F));
            assertThat(dub.cradle()).isCloseTo(1, within(1.0E-3F));
        }
    }

    @Test
    void beating_strandPulseRunsToTheCradle()
    {
        assertThat(VentricleRhythm.beating(0).essenceRun()).isZero();
        assertThat(VentricleRhythm.beating(VentricleRhythm.STRAND_RUN / 2).essenceRun()).isCloseTo(0.5F, within(1.0E-4F));
        assertThat(VentricleRhythm.beating(VentricleRhythm.STRAND_RUN + 1).essenceRun()).isEqualTo(-1);
    }

    @Test
    void kindling_startsOutOfStepAndEndsInStep()
    {
        int apart = 0;
        for (int tick = 0; tick < 200; tick++)
        {
            var pulses = VentricleRhythm.kindling(tick);
            if (Math.abs(pulses.essence() - pulses.contravoid()) > 0.1F)
                apart++;
        }
        // Faint while the chamber warms, but rarely together
        assertThat(apart).isGreaterThan(40);

        float end = KINDLING_TICKS;
        var kindled = VentricleRhythm.kindling(end + 0.01F);
        var beating = VentricleRhythm.beating(0.01F);
        assertThat(kindled.essence()).isCloseTo(beating.essence(), within(0.05F));
        assertThat(kindled.contravoid()).isCloseTo(beating.contravoid(), within(0.05F));
    }

    @Test
    void since_wrapsWithinThePeriod()
    {
        assertThat(VentricleRhythm.since(25, 24, 0)).isCloseTo(1, within(1.0E-4F));
        assertThat(VentricleRhythm.since(0, 24, 0.5F)).isCloseTo(12, within(1.0E-4F));
    }
}
