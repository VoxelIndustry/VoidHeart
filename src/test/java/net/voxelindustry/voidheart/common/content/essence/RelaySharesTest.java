package net.voxelindustry.voidheart.common.content.essence;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RelaySharesTest
{
    @Test
    void manySmallNeeds_areAllMet()
    {
        assertThat(RelayShares.split(250, new int[]{3, 3, 2, 3, 3, 3})).containsExactly(3, 3, 2, 3, 3, 3);
    }

    @Test
    void largeNeeds_shareEvenly_nearestTakingTheRemainder()
    {
        var shares = RelayShares.split(250, new int[]{1000, 1000, 1000, 1000, 1000, 1000});
        assertThat(Arrays.stream(shares).sum()).isEqualTo(250);
        assertThat(shares).containsExactly(42, 42, 42, 42, 41, 41);
    }

    @Test
    void whatSmallNeedsLeave_goesToTheLargeOnes()
    {
        assertThat(RelayShares.split(250, new int[]{1000, 10, 1000})).containsExactly(120, 10, 120);
    }

    @Test
    void noRoom_getsNothing_andNeverMoreThanItsRoom()
    {
        assertThat(RelayShares.split(250, new int[]{0, 5, 0})).containsExactly(0, 5, 0);
        assertThat(RelayShares.split(250, new int[]{0, 0})).containsExactly(0, 0);
        assertThat(RelayShares.split(0, new int[]{100})).containsExactly(0);
    }

    @Test
    void oneMachine_takesTheWholeBudget()
    {
        assertThat(RelayShares.split(250, new int[]{1500})).containsExactly(250);
    }

    @Test
    void noReceivers_returnsAnEmptyAllocation()
    {
        assertThat(RelayShares.split(250, new int[0])).isEmpty();
    }

    @Test
    void negativeBudget_sendsNothing()
    {
        assertThat(RelayShares.split(-1, new int[]{3, 0, 10})).containsExactly(0, 0, 0);
    }

    @Test
    void unavailableReceivers_doNotDiluteTheSplit()
    {
        assertThat(RelayShares.split(5, new int[]{0, 10, -2, 10})).containsExactly(0, 3, 0, 2);
    }

    @Test
    void budgetSmallerThanReceiverCount_goesToNearestEqualNeeds()
    {
        assertThat(RelayShares.split(2, new int[]{10, 10, 10, 10})).containsExactly(1, 1, 0, 0);
    }

    @Test
    void split_preservesCallerOrderAndDoesNotMutateSpaces()
    {
        var spaces = new int[]{100, 1, 100, 2};
        assertThat(RelayShares.split(10, spaces)).containsExactly(4, 1, 3, 2);
        assertThat(spaces).containsExactly(100, 1, 100, 2);
    }

    @Test
    void smallAllocations_conserveBudgetAndRespectEveryCapacity()
    {
        for (var first = -1; first <= 5; first++)
            for (var second = -1; second <= 5; second++)
                for (var budget = 0; budget <= 12; budget++)
                {
                    var shares = RelayShares.split(budget, new int[]{first, second});
                    assertThat(shares[0]).isBetween(0, Math.max(0, first));
                    assertThat(shares[1]).isBetween(0, Math.max(0, second));
                    assertThat(Arrays.stream(shares).sum()).isEqualTo(Math.min(budget, Math.max(0, first) + Math.max(0, second)));
                }
    }
}
