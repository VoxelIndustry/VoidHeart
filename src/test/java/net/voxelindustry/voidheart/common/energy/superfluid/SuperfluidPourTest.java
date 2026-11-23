package net.voxelindustry.voidheart.common.energy.superfluid;

import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.PEARL_CAPACITY;
import static net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance.SHARD_UNITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SuperfluidPourTest
{
    @Test
    void largeBundle_poursIntoTwentyMinutesOfUnits()
    {
        var credit = 0;
        var worth = 4;
        var poured = 0;
        var ticks = 0;
        while (worth > 0)
        {
            var step = SuperfluidPour.tick(credit, worth, PEARL_CAPACITY - poured);
            poured += step.poured();
            credit = step.credit();
            worth -= step.drained();
            ticks++;
        }

        assertThat(poured).isEqualTo(4 * SHARD_UNITS);
        assertThat(ticks).isEqualTo(4 * 5 * 60 * 20);
        assertThat(credit).isZero();
    }

    @Test
    void shardLeavesOnlyOnceAllOfItHasPoured()
    {
        var step = SuperfluidPour.tick(SHARD_UNITS - 2, 3, PEARL_CAPACITY);
        assertThat(step.drained()).isZero();
        assertThat(step.credit()).isEqualTo(SHARD_UNITS - 1);

        step = SuperfluidPour.tick(step.credit(), 3, PEARL_CAPACITY);
        assertThat(step.drained()).isOne();
        assertThat(step.credit()).isZero();
    }

    @Test
    void fullPearl_takesNothing_andCradleKeepsItsCredit()
    {
        var step = SuperfluidPour.tick(1200, 2, 0);

        assertThat(step.poured()).isZero();
        assertThat(step.credit()).isEqualTo(1200);
        assertThat(step.drained()).isZero();
    }

    @Test
    void pearlNearlyFull_takesOnlyItsRoom()
    {
        var step = SuperfluidPour.pour(0, 4, 500, 2 * SHARD_UNITS);

        assertThat(step.poured()).isEqualTo(500);
        assertThat(step.credit()).isEqualTo(500);
    }

    @Test
    void pourAtOnce_neverTakesMoreThanTheCradleHolds()
    {
        var step = SuperfluidPour.pour(100, 2, PEARL_CAPACITY, 10 * SHARD_UNITS);

        assertThat(step.poured()).isEqualTo(2 * SHARD_UNITS - 100);
        assertThat(step.drained()).isEqualTo(2);
        assertThat(step.credit()).isZero();
    }

    @Test
    void emptied_shardAlreadyPouringIsSpent()
    {
        assertThat(SuperfluidPour.kept(4, 0)).isEqualTo(4);
        assertThat(SuperfluidPour.kept(4, 1)).isEqualTo(3);
        assertThat(SuperfluidPour.kept(0, 0)).isZero();
    }

    @Test
    void held_countsWhatIsLeftOfThePouringShard()
    {
        assertThat(SuperfluidPour.held(3, SHARD_UNITS / 4)).isCloseTo(2.75, within(1e-9));
        assertThat(SuperfluidPour.held(0, 0)).isZero();
    }

    @Test
    void staleCredit_isClampedBelowAShard()
    {
        var step = SuperfluidPour.tick(SHARD_UNITS * 5, 1, PEARL_CAPACITY);

        assertThat(step.poured()).isOne();
        assertThat(step.drained()).isOne();
        assertThat(step.credit()).isZero();
    }
}
