package net.voxelindustry.voidheart.common.content.works;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class SlotAssignmentTest
{
    private static Predicate<String> any(String... kinds)
    {
        return Set.of(kinds)::contains;
    }

    @Test
    void assign_givenASlotThatOnlyOneKindFits_thenLeavesThatKindForIt()
    {
        // Slots a|b, a|c, a|b with two a and one c: the first fit would give the second slot an a and leave the third with nothing
        var picks = SlotAssignment.assign(List.of(any("a", "b"), any("a", "c"), any("a", "b")), List.of("a", "c"), new int[] { 2, 1 });

        assertThat(picks).containsExactly(0, 1, 0);
    }

    @Test
    void assign_givenTooFewItems_thenFindsNoWay()
    {
        assertThat(SlotAssignment.assign(List.of(any("a"), any("a")), List.of("a"), new int[] { 1 })).isNull();
    }

    @Test
    void assign_givenNoSlots_thenNeedsNothing()
    {
        assertThat(SlotAssignment.assign(List.<Predicate<String>>of(), List.of("a"), new int[] { 1 })).isEmpty();
    }
}
