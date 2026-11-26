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

    /** The latest beat is the last one started, even one jittered early into the period before its own. */
    @Test
    void theLatestBeatIsTheLastStarted()
    {
        // This seed starts beat 1 at tick 27, early in the period of beat 0
        assertThat(HeartBeat.start(1, -1640531535, HeartBeat.ROOTED_PERIOD)).isLessThan(HeartBeat.ROOTED_PERIOD);
        assertThat(HeartBeat.latest(28, -1640531535, HeartBeat.ROOTED_PERIOD)).isEqualTo(1);

        for (int seed = 0; seed < 200; seed++)
        {
            int owner = seed * 0x9E3779B9;
            for (float time = 0; time < HeartBeat.ROOTED_PERIOD * 20; time += 0.5F)
            {
                int latest = HeartBeat.latest(time, owner, HeartBeat.ROOTED_PERIOD);
                assertThat(HeartBeat.start(latest, owner, HeartBeat.ROOTED_PERIOD)).isLessThanOrEqualTo(time);
                assertThat(HeartBeat.start(latest + 1, owner, HeartBeat.ROOTED_PERIOD)).isGreaterThan(time);
            }
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
