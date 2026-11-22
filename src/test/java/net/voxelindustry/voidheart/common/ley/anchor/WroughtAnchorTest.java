package net.voxelindustry.voidheart.common.ley.anchor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WroughtAnchorTest
{
    @Test
    void tripsCostMoreTheFartherTheyGo()
    {
        assertThat(WroughtAnchorBalance.travelCost(0)).isEqualTo(WroughtAnchorBalance.TRAVEL_BASE);
        assertThat(WroughtAnchorBalance.travelCost(-5)).isEqualTo(WroughtAnchorBalance.TRAVEL_BASE);
        assertThat(WroughtAnchorBalance.travelCost(1000)).isEqualTo(WroughtAnchorBalance.TRAVEL_BASE + 1000 * WroughtAnchorBalance.TRAVEL_PER_BLOCK);
        assertThat(WroughtAnchorBalance.travelCost(10.2)).isGreaterThan(WroughtAnchorBalance.travelCost(10));
    }

    @Test
    void namesLoseFormattingAndControlCharacters()
    {
        assertThat(WroughtAnchorBalance.cleanName("  §cRed   Hollow\n ")).isEqualTo("Red Hollow");
        assertThat(WroughtAnchorBalance.cleanName("§§")).isEmpty();
        assertThat(WroughtAnchorBalance.cleanName("   ")).isEmpty();
        assertThat(WroughtAnchorBalance.cleanName("x".repeat(50))).hasSize(WroughtAnchorBalance.NAME_MAX_LENGTH);
    }

    @Test
    void idsAreNegativeAndNeverReused()
    {
        var state = new WroughtAnchorState();
        var first = state.add("A", 0, 64, 0);
        var second = state.add("B", 10, 64, 10);
        state.remove(second.id());
        var third = state.add("C", 20, 64, 20);

        assertThat(List.of(first.id(), second.id(), third.id())).allMatch(WroughtAnchor::isWrought).doesNotHaveDuplicates();
    }

    @Test
    void loadedStateDropsIdsThatCouldMeetANodeAndIssuesPastTheRest()
    {
        var state = new WroughtAnchorState(List.of(new WroughtAnchor(-7, "Kept", 0, 64, 0), new WroughtAnchor(42, "Node id", 5, 64, 5)), 2);

        assertThat(state.all()).extracting(WroughtAnchor::name).containsExactly("Kept");
        assertThat(state.add("Next", 1, 64, 1).id()).isEqualTo(-8);
    }

    @Test
    void renamingKeepsOnlyStandingAnchors()
    {
        var state = new WroughtAnchorState();
        var anchor = state.add("Old name", 0, 64, 0);
        state.update(anchor.named("New name"));
        state.remove(anchor.id());
        state.update(anchor.named("Ghost"));

        assertThat(state.all()).isEmpty();
    }
}
