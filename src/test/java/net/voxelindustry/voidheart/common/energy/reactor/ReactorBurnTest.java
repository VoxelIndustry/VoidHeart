package net.voxelindustry.voidheart.common.energy.reactor;

import org.junit.jupiter.api.Test;

import java.util.function.LongUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReactorBurnTest
{
    private static final LongUnaryOperator TAKES_ALL  = amount -> amount;
    private static final LongUnaryOperator TAKES_NONE = amount -> 0;

    @Test
    void fullOutputsMatchTheNumbers()
    {
        assertThat(ReactorEssence.FIERY.fullOutput()).isEqualTo(400);
        assertThat(ReactorEssence.STILLNESS.fullOutput()).isEqualTo(320);
        assertThat(ReactorEssence.SUFFERING.fullOutput()).isEqualTo(500);
    }

    @Test
    void theFullestBurnCancelsTwoPairsATick()
    {
        for (var fuel : ReactorEssence.values())
        {
            var step = ReactorBurn.step(fuel, 1_000, 1_000, 0, TAKES_ALL);
            assertThat(step.pairs()).as(fuel.name()).isEqualTo(2);
            assertThat(step.energy()).as(fuel.name()).isEqualTo(fuel.fullOutput());
            assertThat(step.burned()).as(fuel.name()).isEqualTo(ReactorBalance.MAX_PAIRS);
            assertThat(step.credit()).as(fuel.name()).isZero();
        }
    }

    @Test
    void withNothingTakenItStillBurnsTheMinimum()
    {
        var credit = 0.0;
        var taken = 0;
        for (int tick = 0; tick < 40; tick++)
        {
            var step = ReactorBurn.step(ReactorEssence.FIERY, 1_000, 1_000, credit, TAKES_NONE);
            assertThat(step.energy()).isZero();
            assertThat(step.burned()).isEqualTo(ReactorBalance.MIN_PAIRS);
            credit = step.credit();
            taken += step.pairs();
        }
        // A quarter of two pairs a tick, over 40 ticks
        assertThat(taken).isEqualTo(20);
        assertThat(credit).isCloseTo(0, within(1e-9));
    }

    @Test
    void itBurnsJustWhatTheNetworkTakesAboveTheMinimum()
    {
        // The advanced base's 131 VE/t with Fiery: 0.655 pairs a tick
        var credit = 0.0;
        var taken = 0;
        for (int tick = 0; tick < 200; tick++)
        {
            var step = ReactorBurn.step(ReactorEssence.FIERY, 1_000, 1_000, credit, amount -> Math.min(amount, 131));
            assertThat(step.energy()).isEqualTo(131);
            assertThat(step.burned()).isCloseTo(0.655, within(1e-9));
            credit = step.credit();
            taken += step.pairs();
        }
        assertThat(taken + credit).isCloseTo(131, within(1e-6));
    }

    @Test
    void theCreditIsBurnedBeforeNewFuel()
    {
        var step = ReactorBurn.step(ReactorEssence.FIERY, 1_000, 1_000, 0.75, TAKES_NONE);
        assertThat(step.pairs()).isZero();
        assertThat(step.burned()).isEqualTo(ReactorBalance.MIN_PAIRS);
        assertThat(step.credit()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void theShorterInputSetsWhatCanBurn()
    {
        var step = ReactorBurn.step(ReactorEssence.SUFFERING, 1, 1_000, 0, TAKES_ALL);
        assertThat(step.pairs()).isEqualTo(1);
        assertThat(step.energy()).isEqualTo(250);
        assertThat(step.burned()).isEqualTo(1);
        assertThat(step.credit()).isZero();
    }

    @Test
    void nothingToBurnOffersNothing()
    {
        long[] offered = {0};
        var step = ReactorBurn.step(ReactorEssence.STILLNESS, 0, 1_000, 0, amount ->
        {
            offered[0] += amount;
            return amount;
        });
        assertThat(offered[0]).isZero();
        assertThat(step).isEqualTo(new ReactorBurn.Step(0, 0, 0, 0));
    }

    @Test
    void anOfferTakingMoreThanItWasGivenIsClamped()
    {
        var step = ReactorBurn.step(ReactorEssence.FIERY, 1_000, 1_000, 0, amount -> amount * 10);
        assertThat(step.energy()).isEqualTo(400);
        assertThat(step.pairs()).isEqualTo(2);
    }

    @Test
    void creditNeverReachesAWholePair()
    {
        var credit = 0.0;
        for (int tick = 0; tick < 500; tick++)
        {
            var takes = tick % 3 == 0 ? 0 : 97L;
            var step = ReactorBurn.step(ReactorEssence.STILLNESS, 1_000, 1_000, credit, amount -> Math.min(amount, takes));
            credit = step.credit();
            assertThat(credit).isBetween(0.0, 1.0);
            assertThat(credit).isLessThan(1.0);
        }
    }
}
