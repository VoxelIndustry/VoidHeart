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
    void disabledGenerationHasNoAnchor()
    {
        var network = new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 64)), false, LeyAnchorNetwork.DEFAULT_INACTIVE_CHANCE);

        assertThat(anchorsAround(network)).isEmpty();
    }

    @Test
    void inactiveRateSetsDormantAnchors()
    {
        var allActive = anchorsAround(new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 64)), true, 0));
        var allDormant = anchorsAround(new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 64)), true, 1));

        assertThat(allActive).isNotEmpty().allMatch(OldAnchor::initiallyActive);
        assertThat(allDormant).hasSameSizeAs(allActive).noneMatch(OldAnchor::initiallyActive);
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
    void aPointBesideAnAnchorReachesIt()
    {
        var lattice = lattice(new LeyHeights((x, z) -> 64));
        var network = new LeyAnchorNetwork(SEED, lattice);

        int checked = 0, sameAsAnchor = 0;
        for (var anchor : anchorsAround(network))
        {
            for (var line : lattice.edgesOf(anchor.nodeId()))
            {
                // Two blocks out along the line from the anchor's node
                double towardX = line.other(anchor.nodeId()) == line.to() ? line.toX() - line.fromX() : line.fromX() - line.toX();
                double towardZ = line.other(anchor.nodeId()) == line.to() ? line.toZ() - line.fromZ() : line.fromZ() - line.toZ();
                double length = Math.hypot(towardX, towardZ);
                int x = (int) Math.round(anchor.x() + towardX / length * 2);
                int z = (int) Math.round(anchor.z() + towardZ / length * 2);
                if (lattice.nearestEdge(x, z).filter(nearest -> nearest.touches(anchor.nodeId())).isEmpty())
                    continue;

                var reached = network.linkedAt(x, z);
                assertThat(reached).as("beside %s", anchor.name()).contains(anchor);
                // Mostly the anchor's own neighbors: the point only wins nodes a little closer to it than to their owner
                var neighbors = new ArrayList<>(network.linked(anchor));
                neighbors.add(anchor);
                if (neighbors.containsAll(reached))
                    sameAsAnchor++;
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(10);
        System.out.println("points beside an anchor reaching only its neighbors: " + sameAsAnchor + " of " + checked);
        assertThat(sameAsAnchor).isGreaterThan(checked * 3 / 4);
    }

    @Test
    void aPointOnAnAnchorsNodeLeavesItTheNode()
    {
        var network = new LeyAnchorNetwork(SEED, lattice(new LeyHeights((x, z) -> 64)));

        for (var anchor : anchorsAround(network))
            assertThat(network.linkedAt(anchor.x(), anchor.z())).as(anchor.name()).contains(anchor);
    }

    @Test
    void pointsNeverChangeTheLinksBetweenOldAnchors()
    {
        var lattice = lattice(new LeyHeights((x, z) -> 64));
        var fresh = new LeyAnchorNetwork(SEED, lattice);
        var touched = new LeyAnchorNetwork(SEED, lattice);

        for (var anchor : anchorsAround(touched))
            touched.linkedAt(anchor.x() + 3, anchor.z() - 2);
        for (var anchor : anchorsAround(touched))
            assertThat(touched.linked(anchor)).isEqualTo(fresh.linked(anchor));
    }

    @Test
    void nearLineMeasuresFromTheClosestLineToTheBlockCenter()
    {
        var lattice = lattice(new LeyHeights((x, z) -> 64));
        var network = new LeyAnchorNetwork(SEED, lattice);

        int near = 0, far = 0;
        for (int x = -REGION_SIZE; x < REGION_SIZE; x += 7)
        {
            for (int z = -REGION_SIZE; z < REGION_SIZE; z += 7)
            {
                double centerX = x + 0.5, centerZ = z + 0.5;
                double distanceSq = lattice.regionsAround(x, z, REGION_SIZE / 2, true).stream().flatMap(region -> region.edges().stream())
                        .mapToDouble(edge -> edge.distanceSq(centerX, centerZ)).min().orElseThrow();
                boolean expected = distanceSq <= WroughtAnchorBalance.LINE_REACH * WroughtAnchorBalance.LINE_REACH;
                assertThat(network.nearLine(x, z, WroughtAnchorBalance.LINE_REACH)).isEqualTo(expected);
                if (expected)
                    near++;
                else
                    far++;
            }
        }
        assertThat(near).isPositive();
        assertThat(far).isPositive();
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
