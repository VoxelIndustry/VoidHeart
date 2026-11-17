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
}
