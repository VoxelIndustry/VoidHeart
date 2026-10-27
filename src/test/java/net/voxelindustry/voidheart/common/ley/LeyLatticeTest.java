package net.voxelindustry.voidheart.common.ley;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGeneratorTest.FAKE_STRUCTURES;
import static org.assertj.core.api.Assertions.assertThat;

class LeyLatticeTest
{
    private static final long SEED = 42L;

    static class MemoryStore implements LeyLattice.RegionStore
    {
        final Map<Long, LeyRegion> regions = new HashMap<>();

        @Override
        public LeyRegion get(int regionX, int regionZ)
        {
            return regions.get(LeyRegion.key(regionX, regionZ));
        }

        @Override
        public void put(LeyRegion region)
        {
            regions.put(region.key(), region);
        }
    }

    @Test
    void reconcileKeepsRegionUntouchedWhenRulesAreUnchanged()
    {
        var store = new MemoryStore();
        var lattice = new LeyLattice(new LeyLatticeGenerator(SEED, FAKE_STRUCTURES), store);
        lattice.region(0, 0);

        var expected = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(1, 0);
        var region = lattice.region(1, 0);
        assertThat(region.nodes()).isEqualTo(expected.nodes());
        assertThat(region.edges()).containsExactlyInAnyOrderElementsOf(expected.edges());
    }

    @Test
    void frozenNeighborWinsOnSharedBorderAfterRulesChange()
    {
        var store = new MemoryStore();
        var frozen = new LeyLattice(new LeyLatticeGenerator(SEED, LeyAnchorSource.NONE), store).region(0, 0);

        var reconciled = new LeyLattice(new LeyLatticeGenerator(SEED, FAKE_STRUCTURES), store).region(1, 0);

        var nodeIds = reconciled.nodes().stream().map(LeyNode::id).collect(Collectors.toSet());
        var frozenBorder = frozen.edges().stream()
                .filter(edge -> reconciled.contains(edge.fromX(), edge.fromZ()) || reconciled.contains(edge.toX(), edge.toZ()))
                .filter(edge -> nodeIds.contains(edge.from()) || nodeIds.contains(edge.to()))
                .collect(Collectors.toSet());
        var reconciledBorder = reconciled.edges().stream()
                .filter(edge -> frozen.contains(edge.fromX(), edge.fromZ()) || frozen.contains(edge.toX(), edge.toZ()))
                .collect(Collectors.toSet());

        assertThat(reconciledBorder).isEqualTo(frozenBorder);
        for (var node : reconciled.nodes())
            assertThat(node.lines()).isEqualTo(reconciled.edges().stream().filter(edge -> edge.touches(node.id())).count());
    }

    @Test
    void previewDoesNotFreeze()
    {
        var store = new MemoryStore();
        new LeyLattice(new LeyLatticeGenerator(SEED, FAKE_STRUCTURES), store).preview(3, 3);

        assertThat(store.regions).isEmpty();
    }
}
