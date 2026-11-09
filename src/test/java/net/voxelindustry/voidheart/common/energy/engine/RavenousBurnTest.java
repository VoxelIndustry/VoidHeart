package net.voxelindustry.voidheart.common.energy.engine;

import org.junit.jupiter.api.Test;

import java.util.function.LongUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class RavenousBurnTest
{
    private static final LongUnaryOperator TAKES_ALL = amount -> amount;
    private static final LongUnaryOperator TAKES_NONE = amount -> 0;

    @Test
    void fullBurnRatesMatchTheNumbers()
    {
        assertThat(EngineFuel.STILLNESS.fullOutput()).isEqualTo(30);
        assertThat(EngineFuel.FIERY.fullOutput()).isEqualTo(48);
        assertThat(EngineFuel.SUFFERING.fullOutput()).isEqualTo(40);
    }

    @Test
    void fieryBurnsFastestStillnessSlowestAndSufferingIsDensest()
    {
        assertThat(EngineFuel.FIERY.burn()).isGreaterThan(EngineFuel.STILLNESS.burn()).isGreaterThan(EngineFuel.SUFFERING.burn());
        assertThat(EngineFuel.SUFFERING.density()).isGreaterThan(EngineFuel.STILLNESS.density()).isGreaterThan(EngineFuel.FIERY.density());
    }

    @Test
    void aFullTankBurnsAtTheFuelsRateWhenEverythingIsTaken()
    {
        for (var fuel : EngineFuel.values())
        {
            var step = RavenousBurn.step(fuel, RavenousEngineBalance.TANK, 0, TAKES_ALL);
            assertThat(step.essence()).as(fuel.name()).isEqualTo(fuel.burn());
            assertThat(step.energy()).as(fuel.name()).isEqualTo(fuel.fullOutput());
            assertThat(step.credit()).as(fuel.name()).isZero();
        }
    }

    @Test
    void nothingBurnsWhileNothingIsTaken()
    {
        for (var fuel : EngineFuel.values())
        {
            var step = RavenousBurn.step(fuel, RavenousEngineBalance.TANK, 0, TAKES_NONE);
            assertThat(step.essence()).isZero();
            assertThat(step.energy()).isZero();
            assertThat(step.credit()).isZero();
        }
    }

    @Test
    void aPartialTakeBurnsOnlyWhatPaysForItAndKeepsTheRestAsCredit()
    {
        // 7 VE of Stillness at 2.5 each takes 3 essence, which pays for 7.5
        var step = RavenousBurn.step(EngineFuel.STILLNESS, 100, 0, amount -> Math.min(amount, 7));
        assertThat(step.essence()).isEqualTo(3);
        assertThat(step.energy()).isEqualTo(7);
        assertThat(step.credit()).isEqualTo(0.5);

        // The credit is offered first, so the next 30 VE need only 12 essence and leave half a point
        var next = RavenousBurn.step(EngineFuel.STILLNESS, 97, step.credit(), TAKES_ALL);
        assertThat(next.energy()).isEqualTo(30);
        assertThat(next.essence()).isEqualTo(12);
        assertThat(next.credit()).isEqualTo(0.5);
    }

    @Test
    void aCreditCoveringTheTakeBurnsNoEssence()
    {
        var step = RavenousBurn.step(EngineFuel.SUFFERING, 50, 3, amount -> Math.min(amount, 2));
        assertThat(step.essence()).isZero();
        assertThat(step.energy()).isEqualTo(2);
        assertThat(step.credit()).isEqualTo(1);
    }

    @Test
    void aLowTankBurnsWhatIsLeft()
    {
        var step = RavenousBurn.step(EngineFuel.FIERY, 5, 0, TAKES_ALL);
        assertThat(step.essence()).isEqualTo(5);
        assertThat(step.energy()).isEqualTo(10);

        var empty = RavenousBurn.step(EngineFuel.FIERY, 0, 0, TAKES_ALL);
        assertThat(empty.essence()).isZero();
        assertThat(empty.energy()).isZero();
    }

    @Test
    void anOfferAnsweredWithMoreThanAskedIsClamped()
    {
        var step = RavenousBurn.step(EngineFuel.STILLNESS, 100, 0, amount -> amount * 10);
        assertThat(step.energy()).isEqualTo(30);
        assertThat(step.essence()).isEqualTo(12);
    }

    /**
     * Over a long run with any take, every essence burned turns into energy taken, with less than one essence's worth left as credit.
     */
    @Test
    void noEnergyIsWastedOverALongRun()
    {
        for (var fuel : EngineFuel.values())
        {
            int stored = 2_000;
            double credit = 0;
            long taken = 0;
            int burned = 0;
            for (int tick = 0; tick < 500; tick++)
            {
                long cap = tick % 7;
                var step = RavenousBurn.step(fuel, stored, credit, amount -> Math.min(amount, cap * 5));
                stored -= step.essence();
                burned += step.essence();
                taken += step.energy();
                credit = step.credit();
                assertThat(credit).as(fuel.name()).isBetween(0.0, fuel.density());
            }
            assertThat(burned * fuel.density()).as(fuel.name()).isCloseTo(taken + credit, org.assertj.core.data.Offset.offset(1e-6));
        }
    }

    @Test
    void aTankDrainsAtTheBurnRate()
    {
        for (var fuel : EngineFuel.values())
        {
            int stored = 1_000;
            int ticks = 0;
            double credit = 0;
            while (stored > 0)
            {
                var step = RavenousBurn.step(fuel, stored, credit, TAKES_ALL);
                stored -= step.essence();
                credit = step.credit();
                ticks++;
            }
            assertThat(ticks).as(fuel.name()).isEqualTo((int) Math.ceil(1_000.0 / fuel.burn()));
        }
    }

    @Test
    void theBurningFuelKeepsGoingUntilDryThenTheFullestTakesOver()
    {
        var amounts = new int[EngineFuel.values().length];
        assertThat(RavenousBurn.choose(null, amounts)).isNull();

        amounts[EngineFuel.STILLNESS.ordinal()] = 10;
        amounts[EngineFuel.FIERY.ordinal()] = 500;
        assertThat(RavenousBurn.choose(null, amounts)).isEqualTo(EngineFuel.FIERY);
        assertThat(RavenousBurn.choose(EngineFuel.STILLNESS, amounts)).isEqualTo(EngineFuel.STILLNESS);

        amounts[EngineFuel.STILLNESS.ordinal()] = 0;
        amounts[EngineFuel.SUFFERING.ordinal()] = 800;
        assertThat(RavenousBurn.choose(EngineFuel.STILLNESS, amounts)).isEqualTo(EngineFuel.SUFFERING);
    }
}
