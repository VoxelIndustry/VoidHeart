package net.voxelindustry.voidheart.common.energy.pearl;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static anyLong;
import static eq;
import static mock;
import static never;
import static verify;
import static when;

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
    void unfedEnds_borrowTheFedChoirsRoot_andNeverUnsettleIt()
    {
        // X, A, B and Y in a row with only A and B fed: X counts A and Y counts B without being counted back. The unfed ends sit lowest, so a root
        // of their own making would win over the choir's
        long x = 1, a = 5, b = 9, y = 3;
        Map<Long, List<Long>> neighbours = Map.of(x, List.of(a), a, List.of(b), b, List.of(a), y, List.of(b));
        Map<Long, Boolean> fed = Map.of(x, false, a, true, b, true, y, false);
        var roots = new HashMap<>(Map.of(x, x, a, a, b, b, y, y));
        var field = new PearlChoir.Field()
        {
            @Override
            public List<Long> neighbours(long pearl)
            {
                return neighbours.getOrDefault(pearl, List.of());
            }

            @Override
            public long root(long pearl)
            {
                return roots.get(pearl);
            }

            @Override
            public void hand(long pearl, long root)
            {
                roots.put(pearl, root);
            }
        };

        for (var round = 0; round < 6; round++)
        {
            var order = round % 2 == 0 ? List.of(x, a, b, y) : List.of(y, b, a, x);
            for (var pearl : order)
            {
                roots.put(pearl, PearlChoir.scan(pearl, fed.get(pearl), field));
                if (round > 0)
                    assertThat(List.of(roots.get(a), roots.get(b))).as("the fed pair after %s scanned in round %s", pearl, round).containsOnly(a);
            }
        }
        assertThat(roots).containsEntry(x, a).containsEntry(y, a);
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

    @Test
    void cyclesAndDuplicateNeighbours_visitEachMemberOnce()
    {
        Map<Long, List<Long>> reach = Map.of(
                9L, List.of(9L, 4L, 4L),
                4L, List.of(9L, -2L),
                -2L, List.of(4L, 9L));
        var visits = new HashMap<Long, Integer>();
        var walk = PearlChoir.walk(9, pos ->
        {
            visits.merge(pos, 1, Integer::sum);
            return reach.get(pos);
        });
        assertThat(walk.root()).isEqualTo(-2);
        assertThat(walk.members()).containsExactlyInAnyOrder(9L, 4L, -2L);
        assertThat(visits).containsOnly(Map.entry(9L, 1), Map.entry(4L, 1), Map.entry(-2L, 1));
    }

    @Test
    void fedScan_handsRootToEveryOtherMemberOnlyOnce()
    {
        var field = mock(PearlChoir.Field.class);
        when(field.neighbours(9)).thenReturn(List.of(4L, 4L));
        when(field.neighbours(4)).thenReturn(List.of(9L, -2L));
        when(field.neighbours(-2)).thenReturn(List.of(4L));

        assertThat(PearlChoir.scan(9, true, field)).isEqualTo(-2);

        verify(field).hand(4, -2);
        verify(field).hand(-2, -2);
        verify(field, never()).hand(eq(9L), anyLong());
        verify(field, never()).root(anyLong());
    }

    @Test
    void unfedScan_borrowsLowestExistingRootWithoutJoiningChoirs()
    {
        var field = mock(PearlChoir.Field.class);
        when(field.neighbours(-100)).thenReturn(List.of(20L, 30L));
        when(field.root(20)).thenReturn(8L);
        when(field.root(30)).thenReturn(-5L);

        assertThat(PearlChoir.scan(-100, false, field)).isEqualTo(-5);

        verify(field, never()).hand(anyLong(), anyLong());
        verify(field, never()).neighbours(20);
        verify(field, never()).neighbours(30);
    }

    @Test
    void isolatedScan_usesOwnRootWhetherFedOrUnfed()
    {
        var field = mock(PearlChoir.Field.class);
        when(field.neighbours(7)).thenReturn(List.of());
        assertThat(PearlChoir.scan(7, true, field)).isEqualTo(7);
        assertThat(PearlChoir.scan(7, false, field)).isEqualTo(7);
        verify(field, never()).root(anyLong());
        verify(field, never()).hand(anyLong(), anyLong());
    }

    @Test
    void removedBridge_splitsPreviouslyConnectedChoir()
    {
        var reach = new HashMap<>(Map.of(1L, List.of(5L), 5L, List.of(1L, 9L), 9L, List.of(5L)));
        assertThat(PearlChoir.walk(9, pos -> reach.getOrDefault(pos, List.of())).root()).isEqualTo(1);
        reach.put(1L, List.of());
        reach.put(9L, List.of());
        reach.remove(5L);
        var separated = PearlChoir.walk(9, pos -> reach.getOrDefault(pos, List.of()));
        assertThat(separated.root()).isEqualTo(9);
        assertThat(separated.members()).containsExactly(9L);
    }

}
