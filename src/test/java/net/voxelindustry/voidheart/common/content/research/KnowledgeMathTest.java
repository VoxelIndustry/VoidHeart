package net.voxelindustry.voidheart.common.content.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KnowledgeMathTest
{
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
    void secondsToReach_coversReachedRisingAndFalling()
    {
        assertThat(KnowledgeMath.secondsToReach(12, 10, 0.1)).isZero();
        assertThat(KnowledgeMath.secondsToReach(5, 10, 0.5)).isCloseTo(10, within(1E-9));
        assertThat(KnowledgeMath.secondsToReach(5, 10, -0.5)).isInfinite();
    }
}
