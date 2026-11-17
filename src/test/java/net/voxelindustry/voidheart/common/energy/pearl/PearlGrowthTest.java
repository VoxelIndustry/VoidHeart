package net.voxelindustry.voidheart.common.energy.pearl;

import net.voxelindustry.voidheart.common.energy.pearl.PearlGrowth.Stage;
import org.junit.jupiter.api.Test;

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
}
