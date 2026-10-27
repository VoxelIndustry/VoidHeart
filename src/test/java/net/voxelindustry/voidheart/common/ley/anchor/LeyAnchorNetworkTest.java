package net.voxelindustry.voidheart.common.ley.anchor;

import net.voxelindustry.voidheart.common.ley.LeyHeights;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGeneratorTest;
import net.voxelindustry.voidheart.common.ley.LeyRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class LeyAnchorNetworkTest
{
    private static final long SEED = 1234L;

    private static LeyLattice lattice(LeyHeights heights)
    {
        Map<Long, LeyRegion> regions = new HashMap<>();
        var store = new LeyLattice.RegionStore()
        {
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
        };
        return new LeyLattice(new LeyLatticeGenerator(SEED, LeyLatticeGeneratorTest.FAKE_STRUCTURES), store, heights);
    }

    private static List<OldAnchor> anchorsAround(LeyAnchorNetwork network)
    {
        return network.anchorsWithin(-REGION_SIZE, -REGION_SIZE, REGION_SIZE - 1, REGION_SIZE - 1);
    }

    @Test
    void anchorsOnlySitOnWellConnectedNodes()
    {
        var lattice = lattice(new LeyHeights((x, z) -> 64));
        var network = new LeyAnchorNetwork(SEED, lattice);

        var anchors = anchorsAround(network);
        assertThat(anchors).isNotEmpty();
        for (var anchor : anchors)
            assertThat(lattice.node(anchor.nodeId()).orElseThrow().lines()).isGreaterThanOrEqualTo(LeyAnchorNetwork.MIN_LINES);

        long inactive = anchors.stream().filter(anchor -> !anchor.initiallyActive()).count();
        assertThat(inactive).isBetween(anchors.size() / 5L, anchors.size() * 4L / 5);
    }

    @Test
    void submergedNodesHaveNoAnchor()
    {
        var network = new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 63, (x, z) -> 40)));

        assertThat(anchorsAround(network)).isEmpty();
    }

    @Test
    void linksAreSymmetric()
    {
        var network = new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 64)));

        Map<Integer, Integer> linkCounts = new TreeMap<>();
        for (var anchor : anchorsAround(network))
        {
            var linked = network.linked(anchor);
            linkCounts.merge(linked.size(), 1, Integer::sum);
            for (var other : linked)
                assertThat(network.linked(other)).as("%s linked from %s", anchor.name(), other.name()).contains(anchor);
        }
        System.out.println("anchors by link count: " + linkCounts);
    }

    @Test
    void namesAreStablePerNode()
    {
        var names = new ArrayList<String>();
        for (long id = 0; id < 12; id++)
        {
            assertThat(LeyAnchorNames.name(SEED, id)).isEqualTo(LeyAnchorNames.name(SEED, id)).isNotBlank();
            names.add(LeyAnchorNames.name(SEED, id * 7919));
        }
        System.out.println("sample names: " + names);
    }
}
