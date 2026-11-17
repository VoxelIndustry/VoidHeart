package net.voxelindustry.voidheart.common.energy.pearl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PearlTremorTest
{
    private static final int TICKS = 20 * 60 * 20;

    @Test
    void alone_burstsAtIrregularGapsAndIsStillBetween()
    {
        var seed = PearlTremor.seedOf(123456789L);
        var starts = burstStarts(seed, seed, 0);

        assertThat(starts.size()).isGreaterThan(TICKS / PearlTremor.SLOT_TICKS / 3);
        var gaps = new ArrayList<Integer>();
        for (int i = 1; i < starts.size(); i++)
            gaps.add(starts.get(i) - starts.get(i - 1));
        assertThat(gaps.stream().distinct().count()).isGreaterThan(5);
        assertThat(gaps).allMatch(gap -> gap >= PearlTremor.BURST_TICKS - 1);

        var still = 0;
        for (int tick = 0; tick < TICKS; tick++)
            if (PearlTremor.at(seed, seed, 0, tick).intensity() == 0)
                still++;
        assertThat(still).isGreaterThan(TICKS / 2);
    }

    @Test
    void intensity_risesAndFallsWithinOneBurst()
    {
        var seed = PearlTremor.seedOf(42);
        var start = burstStarts(seed, seed, 0).getFirst();
        var peak = 0F;
        for (double time = start; time < start + PearlTremor.BURST_TICKS; time += 0.25)
        {
            var intensity = PearlTremor.at(seed, seed, 0, time).intensity();
            assertThat(intensity).isBetween(0F, 1F);
            peak = Math.max(peak, intensity);
        }
        assertThat(peak).isGreaterThan(0.9F);
    }

    @Test
    void inStep_choirBurstsTogether()
    {
        var first = PearlTremor.seedOf(1);
        var second = PearlTremor.seedOf(2);
        var choir = PearlTremor.seedOf(0);

        for (int tick = 0; tick < TICKS; tick++)
        {
            var a = PearlTremor.at(first, choir, 1, tick);
            var b = PearlTremor.at(second, choir, 1, tick);
            assertThat(a.intensity()).isEqualTo(b.intensity());
            if (a.intensity() > 0)
                assertThat(a.shared()).isTrue();
        }
    }

    @Test
    void choirsOwnSeed_sharesEveryBurstOnceInAChoir()
    {
        var leader = PearlTremor.seedOf(0);
        var other = PearlTremor.seedOf(5);

        assertThat(sharedBursts(leader, leader, 0)).isZero();
        for (int tick = 0; tick < TICKS; tick++)
        {
            var a = PearlTremor.at(leader, leader, 0.1F, tick);
            var b = PearlTremor.at(other, leader, 1, tick);
            if (a.intensity() > 0)
                assertThat(a.shared()).isTrue();
            assertThat(a.intensity()).isEqualTo(b.intensity());
        }
    }

    @Test
    void fallingIntoStep_sharesMoreBurstsAsSyncRises()
    {
        var seed = PearlTremor.seedOf(7);
        var choir = PearlTremor.seedOf(3);

        assertThat(sharedBursts(seed, choir, 0)).isZero();
        assertThat(sharedBursts(seed, choir, 0.3F)).isLessThan(sharedBursts(seed, choir, 0.8F));
    }

    @Test
    void jolt_staysWithinOneAndDiffersByPart()
    {
        var seed = PearlTremor.seedOf(9);
        var differs = false;
        for (long tick = 0; tick < 200; tick++)
        {
            var a = PearlTremor.jolt(seed, tick, 0, 0);
            var b = PearlTremor.jolt(seed, tick, 1, 0);
            assertThat(a).isBetween(-1F, 1F);
            differs |= a != b;
        }
        assertThat(differs).isTrue();
    }

    private static ArrayList<Integer> burstStarts(long seed, long choir, float sync)
    {
        var starts = new ArrayList<Integer>();
        var wasStill = true;
        for (int tick = 0; tick < TICKS; tick++)
        {
            // A burst seen on whole ticks starts on the first tick it moves
            var moving = PearlTremor.at(seed, choir, sync, tick).intensity() > 0;
            if (moving && wasStill)
                starts.add(tick);
            wasStill = !moving;
        }
        return starts;
    }

    private static int sharedBursts(long seed, long choir, float sync)
    {
        var shared = 0;
        for (int tick = 0; tick < TICKS; tick++)
        {
            var moment = PearlTremor.at(seed, choir, sync, tick);
            if (moment.intensity() > 0 && moment.shared())
                shared++;
        }
        return shared;
    }

    @Test
    void zeroSync_ignoresChoirSeedAtFractionalTimes()
    {
        var seed = PearlTremor.seedOf(17);
        var moving = false;
        for (double time = -300; time < 300; time += 0.25)
        {
            var alone = PearlTremor.at(seed, seed, 0, time);
            var withChoir = PearlTremor.at(seed, PearlTremor.seedOf(-42), 0, time);
            assertThat(withChoir).isEqualTo(alone);
            assertThat(withChoir.shared()).isFalse();
            moving |= withChoir.intensity() > 0;
        }
        assertThat(moving).isTrue();
    }

    @Test
    void fullSync_matchesChoirLeaderAtFractionalAndNegativeTimes()
    {
        var choir = PearlTremor.seedOf(-10);
        var moving = false;
        for (double time = -300; time < 300; time += 0.25)
        {
            var follower = PearlTremor.at(PearlTremor.seedOf(25), choir, 1, time);
            assertThat(follower).isEqualTo(PearlTremor.at(choir, choir, 1, time));
            assertThat(follower.intensity()).isBetween(0F, 1F);
            if (follower.intensity() == 0)
                assertThat(follower).isEqualTo(PearlTremor.Moment.STILL);
            else
            {
                moving = true;
                assertThat(follower.shared()).isTrue();
            }
        }
        assertThat(moving).isTrue();
    }

    @Test
    void jolt_isRepeatableAndVariesAcrossAxesAndTicks()
    {
        var seed = PearlTremor.seedOf(123);
        var x = new ArrayList<Float>();
        var y = new ArrayList<Float>();
        for (long tick = 0; tick < 50; tick++)
        {
            var value = PearlTremor.jolt(seed, tick, 2, 0);
            assertThat(PearlTremor.jolt(seed, tick, 2, 0)).isEqualTo(value);
            x.add(value);
            var otherAxis = PearlTremor.jolt(seed, tick, 2, 1);
            assertThat(otherAxis).isBetween(-1F, 1F);
            y.add(otherAxis);
        }
        assertThat(x.stream().distinct().count()).isGreaterThan(1);
        assertThat(x).isNotEqualTo(y);
    }
}
