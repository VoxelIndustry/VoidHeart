package net.voxelindustry.voidheart.common.energy.pearl;

import net.voxelindustry.voidheart.common.energy.pearl.PearlGrowth.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PearlGrowthTest
{
    private static final long HOUR = 60L * 60L * 20L;

    @Test
    void alone_fullyFed_maturesInFourHours()
    {
        var growth = new PearlGrowth();
        var ticks = run(growth, 1, 1);

        assertThat(ticks).isEqualTo(4 * HOUR);
        assertThat(growth.stage()).isEqualTo(Stage.MATURE);
    }

    @Test
    void sixPearls_eachMatureInTwoHours()
    {
        var growth = new PearlGrowth();
        var ticks = run(growth, PearlGrowth.boost(6), 1);

        assertThat(ticks).isEqualTo(2 * HOUR);
    }

    @Test
    void boost_risesEvenlyToSixAndCapsThere()
    {
        assertThat(PearlGrowth.boost(1)).isEqualTo(1);
        assertThat(PearlGrowth.boost(2)).isCloseTo(1.2, within(1e-9));
        assertThat(PearlGrowth.boost(4)).isCloseTo(1.6, within(1e-9));
        assertThat(PearlGrowth.boost(6)).isEqualTo(2);
        assertThat(PearlGrowth.boost(7)).isEqualTo(2);
        assertThat(PearlGrowth.boost(40)).isEqualTo(2);
        assertThat(PearlGrowth.boost(0)).isEqualTo(1);
    }

    @Test
    void halfFed_takesTwiceAsLong()
    {
        var growth = new PearlGrowth();
        var ticks = run(growth, 1, 0.5);

        assertThat(ticks).isEqualTo(8 * HOUR);
    }

    @Test
    void starved_pausesWithoutLosingGrowth()
    {
        var growth = new PearlGrowth();
        for (int i = 0; i < 1000; i++)
            growth.tick(1, 1);
        for (int i = 0; i < 5000; i++)
            growth.tick(1, 0);

        assertThat(growth.progress()).isEqualTo(1000);
    }

    @Test
    void mature_stopsGrowing()
    {
        var growth = new PearlGrowth(100);
        growth.setProgress(100);

        assertThat(growth.tick(2, 1)).isFalse();
        assertThat(growth.progress()).isEqualTo(100);
    }

    @Test
    void stages_followGrowthInQuarters()
    {
        assertThat(PearlGrowth.stageOf(0)).isEqualTo(Stage.SPECK);
        assertThat(PearlGrowth.stageOf(0.24F)).isEqualTo(Stage.SPECK);
        assertThat(PearlGrowth.stageOf(0.25F)).isEqualTo(Stage.BUDDING);
        assertThat(PearlGrowth.stageOf(0.5F)).isEqualTo(Stage.FOLDING);
        assertThat(PearlGrowth.stageOf(0.75F)).isEqualTo(Stage.COLLAPSING);
        assertThat(PearlGrowth.stageOf(0.999F)).isEqualTo(Stage.COLLAPSING);
        assertThat(PearlGrowth.stageOf(1)).isEqualTo(Stage.MATURE);
    }

    @Test
    void setProgress_clampsStaleSavedValues()
    {
        var growth = new PearlGrowth(100);

        growth.setProgress(-5);
        assertThat(growth.progress()).isZero();
        growth.setProgress(500);
        assertThat(growth.progress()).isEqualTo(100);
        growth.setProgress(Double.NaN);
        assertThat(growth.progress()).isZero();
    }

    @Test
    void feed_fullTank_drinksFieryAtItsRateInWholePoints()
    {
        double credit = 0;
        var tank = 1000;
        double supplied = 0;
        for (int i = 0; i < 400; i++)
        {
            var feed = PearlGrowth.feed(credit, tank, PearlBalance.VOID_DRAW);
            supplied += feed.supply();
            tank -= (int) feed.fiery();
            credit = feed.credit();
            assertThat(credit).isBetween(0D, 1D);
        }

        assertThat(supplied).isEqualTo(400);
        assertThat(1000 - tank - credit).isCloseTo(400 * PearlBalance.FIERY_PER_TICK, within(1e-9));
    }

    @Test
    void feed_noFiery_asksForNoVoidAndGrowsNothing()
    {
        var share = PearlGrowth.fieryShare(0);

        assertThat(PearlGrowth.voidWanted(share)).isZero();
        assertThat(PearlGrowth.feed(0, 0, 0).supply()).isZero();
    }

    @Test
    void feed_weakField_slowsGrowthAndSparesFiery()
    {
        var feed = PearlGrowth.feed(0, 1000, PearlBalance.VOID_DRAW / 4);

        assertThat(feed.supply()).isCloseTo(0.25, within(1e-9));
        assertThat(feed.fiery() - feed.credit()).isCloseTo(0.25 * PearlBalance.FIERY_PER_TICK, within(1e-9));
    }

    @Test
    void feed_lastDropsOfFiery_feedOnlyTheirShare()
    {
        var feed = PearlGrowth.feed(0.1, 0, PearlBalance.VOID_DRAW);

        assertThat(feed.supply()).isCloseTo(0.1 / PearlBalance.FIERY_PER_TICK, within(1e-9));
        assertThat(feed.fiery()).isZero();
        assertThat(feed.credit()).isCloseTo(0, within(1e-9));
    }

    private static long run(PearlGrowth growth, double boost, double supply)
    {
        long ticks = 0;
        while (!growth.isMature() && ticks < 100 * HOUR)
        {
            growth.tick(boost, supply);
            ticks++;
        }
        return ticks;
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidDuration_stillRequiresOneTick(long duration)
    {
        var growth = new PearlGrowth(duration);
        assertThat(growth.duration()).isEqualTo(1);
        assertThat(growth.isMature()).isFalse();
        assertThat(growth.tick(1, 1)).isTrue();
        assertThat(growth.share()).isEqualTo(1F);
    }

    @Test
    void maturity_isReportedOnlyOnTheTickThatCrossesTheBoundary()
    {
        var growth = new PearlGrowth(10);
        growth.setProgress(9.5);
        assertThat(growth.tick(2, 0.125)).isFalse();
        assertThat(growth.progress()).isEqualTo(9.75);
        assertThat(growth.tick(2, 0.5)).isTrue();
        assertThat(growth.progress()).isEqualTo(10);
        assertThat(growth.tick(2, 1)).isFalse();
    }

    @Test
    void tick_clampsExcessSupplyAndDoesNotRegressForNegativeInputs()
    {
        var growth = new PearlGrowth(100);
        growth.setProgress(5);
        assertThat(growth.tick(-2, 1)).isFalse();
        assertThat(growth.tick(2, -1)).isFalse();
        assertThat(growth.progress()).isEqualTo(5);
        assertThat(growth.tick(2, 10)).isFalse();
        assertThat(growth.progress()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonFiniteSavedProgress_resetsGrowth(double progress)
    {
        var growth = new PearlGrowth(100);
        growth.setProgress(75);
        growth.setProgress(progress);
        assertThat(growth.progress()).isZero();
        assertThat(growth.stage()).isEqualTo(Stage.SPECK);
        assertThat(growth.isMature()).isFalse();
    }

    @Test
    void stages_changeExactlyAtEachQuarter()
    {
        for (var ordinal = 1; ordinal < Stage.values().length; ordinal++)
        {
            var boundary = ordinal / 4F;
            assertThat(PearlGrowth.stageOf(Math.nextDown(boundary))).isEqualTo(Stage.values()[ordinal - 1]);
            assertThat(PearlGrowth.stageOf(boundary)).isEqualTo(Stage.values()[ordinal]);
        }
        assertThat(Stage.byOrdinal(-1)).isEqualTo(Stage.SPECK);
        assertThat(Stage.byOrdinal(Stage.values().length)).isEqualTo(Stage.SPECK);
        for (var stage : Stage.values())
            assertThat(Stage.byOrdinal(stage.ordinal())).isEqualTo(stage);
    }

    @Test
    void voidWanted_roundsUpPartialDrawAndClampsToOneTick()
    {
        assertThat(PearlGrowth.voidWanted(-1)).isZero();
        assertThat(PearlGrowth.voidWanted(0.01)).isEqualTo(1);
        assertThat(PearlGrowth.voidWanted(0.25)).isEqualTo(PearlBalance.VOID_DRAW / 4);
        assertThat(PearlGrowth.voidWanted(2)).isEqualTo(PearlBalance.VOID_DRAW);
    }

    @Test
    void noVoid_preservesBothTankAndCredit()
    {
        for (var voidGot : new long[]{0, -1})
        {
            var feed = PearlGrowth.feed(0.125, 10, voidGot);
            assertThat(feed.supply()).isZero();
            assertThat(feed.fiery()).isZero();
            assertThat(feed.credit()).isEqualTo(0.125);
        }
    }

    @Test
    void credit_isSpentBeforeAnotherWholePointIsTaken()
    {
        var enoughCredit = PearlGrowth.feed(0.5, 10, PearlBalance.VOID_DRAW);
        assertThat(enoughCredit.fiery()).isZero();
        assertThat(enoughCredit.credit()).isEqualTo(0.5 - PearlBalance.FIERY_PER_TICK);

        var shortCredit = PearlGrowth.feed(0.125, 10, PearlBalance.VOID_DRAW);
        assertThat(shortCredit.supply()).isEqualTo(1);
        assertThat(shortCredit.fiery()).isEqualTo(1);
        assertThat(shortCredit.credit()).isEqualTo(1.125 - PearlBalance.FIERY_PER_TICK);
    }

    @Test
    void varyingField_eventuallyExhaustsTankWithoutCreatingOrLosingFiery()
    {
        var tank = 3;
        double credit = 0;
        double consumed = 0;
        for (var tick = 0; tick < 100; tick++)
        {
            var feed = PearlGrowth.feed(credit, tank, tick % 3 == 0 ? 0 : PearlBalance.VOID_DRAW / 2);
            tank -= (int) feed.fiery();
            credit = feed.credit();
            consumed += feed.supply() * PearlBalance.FIERY_PER_TICK;
            assertThat(tank).isNotNegative();
            assertThat(credit).isGreaterThanOrEqualTo(0).isLessThan(1);
            assertThat(tank + credit + consumed).isCloseTo(3, within(1e-9));
        }
        assertThat(tank).isZero();
        assertThat(credit).isZero();
        assertThat(consumed).isEqualTo(3);
    }

}
