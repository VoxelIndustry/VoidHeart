package net.voxelindustry.voidheart.common.energy.pearl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PearlChoirTest
{
    @Test
    void row_whoseEndsNeverMeet_sharesOneRoot()
    {
        // Three in a row, each reaching only its neighbours
        Map<Long, List<Long>> reach = Map.of(
                10L, List.of(20L),
                20L, List.of(10L, 30L),
                30L, List.of(20L));
        assertThat(PearlChoir.root(10, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(10);
        assertThat(PearlChoir.root(20, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(10);
        assertThat(PearlChoir.root(30, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(10);
    }

    @Test
    void separateChoirs_keepTheirOwnRoots()
    {
        Map<Long, List<Long>> reach = Map.of(
                1L, List.of(2L), 2L, List.of(1L),
                7L, List.of(8L), 8L, List.of(7L));
        assertThat(PearlChoir.root(2, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(1);
        assertThat(PearlChoir.root(8, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(7);
    }

    @Test
    void lonePearl_isItsOwnRoot_evenIfOthersCountIt()
    {
        assertThat(PearlChoir.root(5, pos -> List.of())).isEqualTo(5);
    }

    @Test
    void oneSidedReach_stillJoins()
    {
        // A pearl not yet fed is left out of the others' counts while still counting them
        Map<Long, List<Long>> reach = Map.of(40L, List.of(3L), 3L, List.of());
        assertThat(PearlChoir.root(40, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(3);
    }

    @Test
    void negativePositions_orderAsPackedLongs()
    {
        Map<Long, List<Long>> reach = Map.of(-5L, List.of(4L), 4L, List.of(-5L, 9L), 9L, List.of(4L));
        assertThat(PearlChoir.root(9, pos -> reach.getOrDefault(pos, List.of()))).isEqualTo(-5);
    }
}
