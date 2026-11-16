package net.voxelindustry.voidheart.common.energy.pearl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

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
        assertThat(PearlChoir.walk(10, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(10);
        assertThat(PearlChoir.walk(20, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(10);
        assertThat(PearlChoir.walk(30, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(10);
    }

    @Test
    void longRow_walkedFromEitherEnd_findsTheSameRootAndEveryMember()
    {
        // Far longer than any choir players build, so no walk stops short and leaves the two ends with different roots
        var length = 1_000L;
        LongFunction<List<Long>> row = pos -> pos == 0 ? List.of(1L) : pos == length - 1 ? List.of(pos - 1) : List.of(pos - 1, pos + 1);
        var fromStart = PearlChoir.walk(0, row);
        var fromEnd = PearlChoir.walk(length - 1, row);
        assertThat(fromStart.root()).isZero();
        assertThat(fromEnd.root()).isZero();
        assertThat(fromStart.members()).hasSize((int) length).isEqualTo(fromEnd.members());
    }

    @Test
    void separateChoirs_keepTheirOwnRoots()
    {
        Map<Long, List<Long>> reach = Map.of(
                1L, List.of(2L), 2L, List.of(1L),
                7L, List.of(8L), 8L, List.of(7L));
        assertThat(PearlChoir.walk(2, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(1);
        var other = PearlChoir.walk(8, pos -> reach.getOrDefault(pos, List.of()));
        assertThat(other.root()).isEqualTo(7);
        assertThat(other.members()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void lonePearl_isItsOwnRoot()
    {
        var walk = PearlChoir.walk(5, pos -> List.of());
        assertThat(walk.root()).isEqualTo(5);
        assertThat(walk.members()).containsExactly(5L);
    }

    @Test
    void oneSidedReach_stillJoins()
    {
        // A pearl not yet fed is left out of the others' counts while still counting them
        Map<Long, List<Long>> reach = Map.of(40L, List.of(3L), 3L, List.of());
        assertThat(PearlChoir.walk(40, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(3);
    }

    @Test
    void negativePositions_orderAsPackedLongs()
    {
        Map<Long, List<Long>> reach = Map.of(-5L, List.of(4L), 4L, List.of(-5L, 9L), 9L, List.of(4L));
        assertThat(PearlChoir.walk(9, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(-5);
    }
}
