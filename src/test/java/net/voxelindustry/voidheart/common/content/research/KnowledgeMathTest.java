package net.voxelindustry.voidheart.common.content.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KnowledgeMathTest
{
    @Test
    void affordableInsights_paysEachDearerInsightUpToTheLimit()
    {
        assertThat(KnowledgeMath.affordableInsights(9, 10, 1.5, 10)).isZero();
        assertThat(KnowledgeMath.affordableInsights(10, 10, 1.5, 10)).isEqualTo(1);
        assertThat(KnowledgeMath.affordableInsights(25, 10, 1.5, 10)).isEqualTo(2);
        assertThat(KnowledgeMath.affordableInsights(1000, 10, 1, 4)).isEqualTo(4);
        assertThat(KnowledgeMath.affordableInsights(1000, 0, 1.5, 10)).isZero();
    }

    @Test
    void feedMultiplier_followsTheDiminishingSchedule()
    {
        double[] expected = {1, 0.3, 0.3, 0.3, 0.3, 0.1, 0.1, 0.1, 0, 0};

        for (int fed = 0; fed < expected.length; fed++)
            assertThat(KnowledgeMath.feedMultiplier(fed)).isEqualTo(expected[fed]);
    }

    @Test
    void exhaustion_followsTheSameSchedule()
    {
        assertThat(KnowledgeMath.exhaustion(0)).isEqualTo(KnowledgeMath.Exhaustion.UNTOUCHED);
        assertThat(KnowledgeMath.exhaustion(1)).isEqualTo(KnowledgeMath.Exhaustion.DRAINED);
        assertThat(KnowledgeMath.exhaustion(4)).isEqualTo(KnowledgeMath.Exhaustion.DRAINED);
        assertThat(KnowledgeMath.exhaustion(5)).isEqualTo(KnowledgeMath.Exhaustion.FADING);
        assertThat(KnowledgeMath.exhaustion(7)).isEqualTo(KnowledgeMath.Exhaustion.FADING);
        assertThat(KnowledgeMath.exhaustion(8)).isEqualTo(KnowledgeMath.Exhaustion.SPENT);
    }

    @Test
    void accrue_givenTimeBeyondCap_thenStopsAtCap()
    {
        assertThat(KnowledgeMath.accrue(10, 30, 0.1, 100)).isEqualTo(20);
        assertThat(KnowledgeMath.accrue(10, 30, 0.1, 10000)).isEqualTo(30);
    }

    @Test
    void accrue_givenPoolAboveCap_thenKeepsIt()
    {
        assertThat(KnowledgeMath.accrue(50, 30, 0.1, 100)).isEqualTo(50);
    }

    @Test
    void research_givenEnoughKnowledge_thenFinishesAtFullSpeed()
    {
        var flow = KnowledgeMath.research(30, 30, 0.1, 10, 1, 100);

        assertThat(flow.remaining()).isZero();
        assertThat(flow.seconds()).isEqualTo(10);
        assertThat(flow.knowledge()).isCloseTo(21, within(1E-9));
    }

    @Test
    void research_givenPoolRunningDry_thenContinuesAtTheRateOfIncome()
    {
        // Net loss of 0.9/s empties the 9 of the pool in 10s, pouring 10. The 10 left then trickle at 0.1/s for 100s.
        var flow = KnowledgeMath.research(9, 30, 0.1, 20, 1, 1000);

        assertThat(flow.remaining()).isZero();
        assertThat(flow.seconds()).isCloseTo(110, within(1E-9));
        assertThat(flow.knowledge()).isZero();
    }

    @Test
    void research_givenNoIncomeAndEmptyPool_thenStalls()
    {
        var flow = KnowledgeMath.research(0, 30, 0, 20, 1, 1000);

        assertThat(flow.remaining()).isEqualTo(20);
        assertThat(flow.seconds()).isEqualTo(1000);
    }

    @Test
    void research_givenIncomeAboveDrain_thenPoolGrowsUpToCap()
    {
        var flow = KnowledgeMath.research(29, 30, 2, 100, 1, 50);

        assertThat(flow.remaining()).isEqualTo(50);
        assertThat(flow.knowledge()).isEqualTo(30);
    }

    @Test
    void research_splittingTheTime_thenGivesTheSameResult()
    {
        var once = KnowledgeMath.research(9, 30, 0.1, 20, 1, 60);

        var first = KnowledgeMath.research(9, 30, 0.1, 20, 1, 7);
        var second = KnowledgeMath.research(first.knowledge(), 30, 0.1, first.remaining(), 1, 53);

        assertThat(second.remaining()).isCloseTo(once.remaining(), within(1E-9));
        assertThat(second.knowledge()).isCloseTo(once.knowledge(), within(1E-9));
    }

    @Test
    void secondsToFinish_withEnoughKnowledge_thenRunsAtFullSpeed()
    {
        assertThat(KnowledgeMath.secondsToFinish(100, 0.1, 20, 1)).isCloseTo(20, within(1E-9));
    }

    @Test
    void secondsToFinish_runningDry_thenMatchesTheFlow()
    {
        double seconds = KnowledgeMath.secondsToFinish(9, 0.1, 20, 1);

        assertThat(KnowledgeMath.research(9, 30, 0.1, 20, 1, seconds).remaining()).isCloseTo(0, within(1E-9));
        assertThat(KnowledgeMath.research(9, 30, 0.1, 20, 1, seconds - 1).remaining()).isGreaterThan(0);
    }

    @Test
    void secondsToFinish_dryWithNothingComingIn_thenNever()
    {
        assertThat(KnowledgeMath.secondsToFinish(5, 0, 20, 1)).isInfinite();
    }

    @Test
    void columnQuarter_splitsTheSpaceInFour()
    {
        assertThat(KnowledgeMath.columnQuarter(0.25, 0.25)).isEqualTo(0);
        assertThat(KnowledgeMath.columnQuarter(0.75, 0.25)).isEqualTo(1);
        assertThat(KnowledgeMath.columnQuarter(0.25, 0.75)).isEqualTo(2);
        assertThat(KnowledgeMath.columnQuarter(0.75, 0.75)).isEqualTo(3);
        for (int quarter = 0; quarter < 4; quarter++)
            assertThat(KnowledgeMath.columnQuarter(KnowledgeMath.columnCenterX(quarter), KnowledgeMath.columnCenterZ(quarter))).isEqualTo(quarter);
    }

    @Test
    void columnHeightLevel_neverMatchesATouchingColumn()
    {
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (dx != 0 || dz != 0)
                            assertThat(KnowledgeMath.columnHeightLevel(x, z)).as("%s %s against %s %s", x, z, dx, dz).isNotEqualTo(KnowledgeMath.columnHeightLevel(x + dx, z + dz));
    }

    @Test
    void columnWave_reachesFurtherColumnsLater()
    {
        // At the crest in the center, a column one wavelength away is too, and a quarter of a period later than one a quarter away
        assertThat(KnowledgeMath.columnWave(35, 0, 140, 5)).isCloseTo(1, within(1E-9));
        assertThat(KnowledgeMath.columnWave(35, 5, 140, 5)).isCloseTo(1, within(1E-9));
        assertThat(KnowledgeMath.columnWave(35, 1.25, 140, 5)).isCloseTo(0, within(1E-9));
        assertThat(KnowledgeMath.columnWave(70, 1.25, 140, 5)).isCloseTo(1, within(1E-9));
    }

    @Test
    void secondsToReach_coversReachedRisingAndFalling()
    {
        assertThat(KnowledgeMath.secondsToReach(12, 10, 0.1)).isZero();
        assertThat(KnowledgeMath.secondsToReach(5, 10, 0.5)).isCloseTo(10, within(1E-9));
        assertThat(KnowledgeMath.secondsToReach(5, 10, -0.5)).isInfinite();
    }
}
