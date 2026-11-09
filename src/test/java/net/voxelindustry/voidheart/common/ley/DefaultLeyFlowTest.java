package net.voxelindustry.voidheart.common.ley;

import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGeneratorTest.FAKE_STRUCTURES;
import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultLeyFlowTest
{
    private static final long SEED = 42L;

    private final LeyLattice lattice = new LeyLattice(new LeyLatticeGenerator(SEED, FAKE_STRUCTURES), new LeyLatticeTest.MemoryStore(),
            new LeyHeights((x, z) -> 64));

    @Test
    void nodeGivesItsRawPower()
    {
        var node = lattice.region(0, 0).nodes().getFirst();

        assertThat(DefaultLeyFlow.flowAt(lattice, node.x(), node.z())).isEqualTo(node.power());
        int edgeX = node.x() + FoundationBalance.NODE_REACH;
        assertThat(lattice.nearestNode(edgeX, node.z())).contains(node);
        assertThat(DefaultLeyFlow.flowAt(lattice, edgeX, node.z())).isEqualTo(node.power());
    }

    @Test
    void lineGivesItsUpstreamNodesPower()
    {
        var region = lattice.region(0, 0);
        var checked = 0;
        for (var edge : region.edges())
        {
            int x = (edge.fromX() + edge.toX()) / 2;
            int z = (edge.fromZ() + edge.toZ()) / 2;
            var nearestNode = lattice.nearestNode(x, z).orElseThrow();
            if (nearestNode.distanceSq(x, z) <= sq(FoundationBalance.NODE_REACH) || !lattice.nearestEdge(x, z).orElseThrow().equals(edge))
                continue;

            var upstream = lattice.node(edge.from()).orElseThrow();
            assertThat(DefaultLeyFlow.flowAt(lattice, x, z)).as("middle of the line from %d to %d", edge.from(), edge.to()).isEqualTo(upstream.power());
            checked++;
        }
        assertThat(checked).isPositive();
    }

    @Test
    void offTheLatticeGivesNothing()
    {
        var spot = farFromEverything();

        assertThat(DefaultLeyFlow.flowAt(lattice, spot[0], spot[1])).isZero();
    }

    @Test
    void lineNearFindsOnlyLinesWithinTheDistance()
    {
        var spot = farFromEverything();
        var nearest = lattice.nearestEdge(spot[0], spot[1]).orElseThrow();
        int distance = (int) Math.ceil(Math.sqrt(nearest.distanceSq(spot[0], spot[1])));

        assertThat(DefaultLeyFlow.edgeNear(lattice, spot[0], spot[1], FoundationBalance.LINE_REACH)).isEmpty();
        assertThat(DefaultLeyFlow.edgeNear(lattice, spot[0], spot[1], distance)).contains(nearest);
    }

    /**
     * A spot in the first region beyond the reach of every node and line.
     */
    private int[] farFromEverything()
    {
        for (int x = 0; x < REGION_SIZE; x += 7)
        {
            for (int z = 0; z < REGION_SIZE; z += 7)
            {
                int finalX = x;
                int finalZ = z;
                double node = lattice.nearestNode(x, z).map(candidate -> candidate.distanceSq(finalX, finalZ)).orElse(Double.MAX_VALUE);
                double line = lattice.regionsAround(x, z, REGION_SIZE / 2, true).stream()
                        .flatMap(region -> region.edges().stream())
                        .map(edge -> edge.distanceSq(finalX, finalZ))
                        .min(Comparator.naturalOrder())
                        .orElse(Double.MAX_VALUE);
                if (node > sq(FoundationBalance.NODE_REACH * 4) && line > sq(FoundationBalance.LINE_REACH * 4))
                    return new int[]{x, z};
            }
        }
        throw new AssertionError("No spot away from the lattice in the first region");
    }

    private static double sq(double value)
    {
        return value * value;
    }
}
