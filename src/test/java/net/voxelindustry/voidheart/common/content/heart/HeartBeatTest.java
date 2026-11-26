package net.voxelindustry.voidheart.common.content.heart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartBeatTest
{
    @Test
    void beatsComeAboutOncePerPeriodInOrder()
    {
        for (float period : new float[] { HeartBeat.DORMANT_PERIOD, HeartBeat.ROOTED_PERIOD })
        {
            int last = HeartBeat.latest(0, 99, period);
            int beats = 0;
            for (float time = 0; time < period * 100; time += 0.25F)
            {
                int latest = HeartBeat.latest(time, 99, period);
                assertThat(latest).isBetween(last, last + 1);
                if (latest != last)
                    beats++;
                last = latest;
                assertThat(HeartBeat.since(time, 99, period)).isBetween(0F, period + 6);
            }
            assertThat(beats).isBetween(99, 101);
        }
    }

    @Test
    void aRootedHeartBeatsTwiceAndTheSecondIsWeaker()
    {
        float lub = 0, dub = 0;
        for (float since = 0; since < HeartBeat.DUB_DELAY; since += 0.25F)
            lub = Math.max(lub, HeartBeat.swell(since, true));
        for (float since = HeartBeat.DUB_DELAY; since < HeartBeat.ROOTED_PERIOD; since += 0.25F)
            dub = Math.max(dub, HeartBeat.swell(since, true));

        assertThat(lub).isGreaterThan(0.99F);
        assertThat(dub).isBetween(0.5F, lub);
        // Between them the heart eases back enough to be seen apart
        assertThat(HeartBeat.swell(HeartBeat.DUB_DELAY, true)).isLessThan(lub);
        assertThat(HeartBeat.swell(HeartBeat.DUB_DELAY + 4, false)).isLessThan(HeartBeat.swell(HeartBeat.DUB_DELAY + 4, true));
    }

    @Test
    void nothingSwellsBeforeABeat()
    {
        assertThat(HeartBeat.pulse(-1)).isZero();
        assertThat(HeartBeat.swell(-0.5F, true)).isZero();
    }
}
