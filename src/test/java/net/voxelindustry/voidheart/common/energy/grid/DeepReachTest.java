package net.voxelindustry.voidheart.common.energy.grid;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeepReachTest
{
    private static DeepReach.CoreSupply core(Set<Object> names, long exclusive, long total)
    {
        return new DeepReach.CoreSupply(names, exclusive, total);
    }

    @Test
    void combine_givenTwoCoresOnOneGrid_thenCountsWhatIsRoutedToThemOnce()
    {
        var grid = Set.<Object>of("grid");

        assertThat(DeepReach.combine(List.of(core(grid, 0, 2), core(grid, 0, 2)))).isEqualTo(2);
    }

    @Test
    void combine_givenEnergyWaitingAtEachCoreOfOneGrid_thenAddsThatUp()
    {
        var grid = Set.<Object>of("grid");

        // 3 and 1 waiting at each core, and at most 3 routed to either
        assertThat(DeepReach.combine(List.of(core(grid, 3, 5), core(grid, 1, 4)))).isEqualTo(7);
    }

    @Test
    void combine_givenCoresOnSeparateGrids_thenAddsThem()
    {
        assertThat(DeepReach.combine(List.of(core(Set.of("a"), 0, 2), core(Set.of("b"), 0, 2)))).isEqualTo(4);
    }

    @Test
    void combine_givenACoreOnBothOfTwoGrids_thenStillAddsTheGridsApart()
    {
        // The core on both shares with each, so the two that draw on nothing in common count
        assertThat(DeepReach.combine(List.of(core(Set.of("a"), 0, 2), core(Set.of("b"), 0, 3), core(Set.of("a", "b"), 0, 1)))).isEqualTo(5);
    }

    @Test
    void combine_givenACoreOnBothOfTwoGridsGettingMore_thenCountsIt()
    {
        assertThat(DeepReach.combine(List.of(core(Set.of("a"), 0, 2), core(Set.of("b"), 0, 3), core(Set.of("a", "b"), 0, 7)))).isEqualTo(7);
    }

    @Test
    void combine_givenManyCoresAndAFewSharing_thenWeighsTheSharingOnesApartFromTheRest()
    {
        // Thirteen cores, past what is weighed every way at once, but only three of them share anything
        var cores = new java.util.ArrayList<DeepReach.CoreSupply>();
        cores.add(core(Set.of("a"), 0, 2));
        cores.add(core(Set.of("b"), 0, 3));
        for (int i = 0; i < 10; i++)
            cores.add(core(Set.of("alone " + i), 0, 1));
        cores.add(core(Set.of("a", "b"), 0, 4));

        assertThat(DeepReach.combine(cores)).isEqualTo(15);
    }

    @Test
    void combine_givenAnEndlessCore_thenStaysBounded()
    {
        assertThat(DeepReach.combine(List.of(core(Set.of(), 0, Long.MAX_VALUE), core(Set.of("a"), 0, 5)))).isEqualTo(Long.MAX_VALUE / 2);
    }
}
