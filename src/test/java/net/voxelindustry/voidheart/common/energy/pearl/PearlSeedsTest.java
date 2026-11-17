package net.voxelindustry.voidheart.common.energy.pearl;

import net.voxelindustry.voidheart.common.energy.reactor.ReactorBalance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PearlSeedsTest
{
    private static final long HOUR = 60L * 60L * 20L;

    @Test
    void fullestBurn_throwsOneSeedAnHour()
    {
        assertThat(seedsOver(HOUR, ReactorBalance.MAX_PAIRS)).isEqualTo(1);
        assertThat(seedsOver(3 * HOUR, ReactorBalance.MAX_PAIRS)).isEqualTo(3);
    }

    @Test
    void leastBurn_throwsOneInFourHours()
    {
        assertThat(seedsOver(4 * HOUR - 20, ReactorBalance.MIN_PAIRS)).isZero();
        assertThat(seedsOver(4 * HOUR, ReactorBalance.MIN_PAIRS)).isEqualTo(1);
    }

    @Test
    void add_keepsTheRemainderAndIgnoresBadValues()
    {
        var seeds = PearlSeeds.add(PearlBalance.SEED_PAIRS - 1, 3);

        assertThat(seeds.seeds()).isEqualTo(1);
        assertThat(seeds.progress()).isEqualTo(2);
        assertThat(PearlSeeds.add(Double.NaN, 1).progress()).isEqualTo(1);
        assertThat(PearlSeeds.add(-10, -3).progress()).isZero();
    }

    private static int seedsOver(long ticks, double pairsPerTick)
    {
        double progress = 0;
        var seeds = 0;
        for (long tick = 0; tick < ticks; tick++)
        {
            var step = PearlSeeds.add(progress, pairsPerTick);
            progress = step.progress();
            seeds += step.seeds();
        }
        return seeds;
    }

    @Test
    void exactThreshold_emitsOneSeedAndClearsProgress()
    {
        assertThat(PearlSeeds.add(PearlBalance.SEED_PAIRS - 0.5, 0.5)).isEqualTo(new PearlSeeds(0, 1));
        assertThat(PearlSeeds.add(PearlBalance.SEED_PAIRS - 0.5, 0.25)).isEqualTo(new PearlSeeds(PearlBalance.SEED_PAIRS - 0.25, 0));
    }

    @Test
    void largeBurn_emitsEveryCompletedSeedAndKeepsFractionalRemainder()
    {
        assertThat(PearlSeeds.add(0.25, 3 * PearlBalance.SEED_PAIRS + 0.5)).isEqualTo(new PearlSeeds(0.75, 3));
    }

    @Test
    void splitBurnAndSingleBurn_produceTheSameSeedsAndProgress()
    {
        var first = PearlSeeds.add(PearlBalance.SEED_PAIRS - 0.5, 0.25);
        var second = PearlSeeds.add(first.progress(), 2 * PearlBalance.SEED_PAIRS + 0.5);
        var combined = PearlSeeds.add(PearlBalance.SEED_PAIRS - 0.5, 2 * PearlBalance.SEED_PAIRS + 0.75);
        assertThat(first.seeds() + second.seeds()).isEqualTo(combined.seeds());
        assertThat(second.progress()).isEqualTo(combined.progress());
    }

    @Test
    void stoppedOrNegativeBurn_preservesExistingProgress()
    {
        assertThat(PearlSeeds.add(12.5, 0)).isEqualTo(new PearlSeeds(12.5, 0));
        assertThat(PearlSeeds.add(12.5, -3)).isEqualTo(new PearlSeeds(12.5, 0));
    }

}
