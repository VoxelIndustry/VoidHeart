package net.voxelindustry.voidheart.common.ley;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.MAX_LINES;
import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

public class LeyLatticeGeneratorTest
{
    private static final long SEED = 8_675_309L;

    /**
     * Mimics vanilla random spread placement: one candidate per grid cell, kept with a probability standing in for biome checks.
     */
    public static final LeyAnchorSource FAKE_STRUCTURES = (minX, minZ, maxX, maxZ) ->
    {
        var anchors = new ArrayList<LeyAnchorSource.Anchor>();
        addSpread(anchors, minX, minZ, maxX, maxZ, 34 * 16, 8 * 16, 11, 0.6F, LeyNodeKind.ANCHOR);
        addSpread(anchors, minX, minZ, maxX, maxZ, 32 * 16, 8 * 16, 12, 0.35F, LeyNodeKind.MINOR_ANCHOR);
        addSpread(anchors, minX, minZ, maxX, maxZ, 80 * 16, 20 * 16, 13, 0.5F, LeyNodeKind.MAJOR_ANCHOR);
        return anchors;
    };

    private static void addSpread(List<LeyAnchorSource.Anchor> anchors, int minX, int minZ, int maxX, int maxZ, int spacing, int separation, long salt, float chance, LeyNodeKind kind)
    {
        for (int gx = Math.floorDiv(minX, spacing); gx <= Math.floorDiv(maxX, spacing); gx++)
        {
            for (int gz = Math.floorDiv(minZ, spacing); gz <= Math.floorDiv(maxZ, spacing); gz++)
            {
                long hash = LeyHash.hash(SEED, gx, gz, salt);
                if (LeyHash.unit(hash) > chance)
                    continue;
                int x = gx * spacing + (int) ((hash >>> 3) % (spacing - separation)) + 8;
                int z = gz * spacing + (int) ((hash >>> 23) % (spacing - separation)) + 8;
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ)
                    anchors.add(new LeyAnchorSource.Anchor(x, z, kind));
            }
        }
    }

    private static List<LeyRegion> generateArea(int radius)
    {
        var regions = new ArrayList<LeyRegion>();
        for (int rx = -radius; rx < radius; rx++)
        {
            for (int rz = -radius; rz < radius; rz++)
                regions.add(new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(rx, rz));
        }
        return regions;
    }

    @Test
    void neighborRegionsAgreeOnSharedEdges()
    {
        var left = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(0, 0);
        var right = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(1, 0);
        var below = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(0, 1);

        assertThat(crossing(left, right)).isNotEmpty().isEqualTo(crossing(right, left));
        assertThat(crossing(left, below)).isNotEmpty().isEqualTo(crossing(below, left));
    }

    private static Set<LeyEdge> crossing(LeyRegion owner, LeyRegion other)
    {
        var result = new HashSet<LeyEdge>();
        for (var edge : owner.edges())
        {
            if (other.contains(edge.fromX(), edge.fromZ()) || other.contains(edge.toX(), edge.toZ()))
                result.add(edge);
        }
        return result;
    }

    @Test
    void sameRegionIsDeterministic()
    {
        var first = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(-2, 3);
        var second = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES).generateRegion(-2, 3);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void linesNeverCrossOutsideNodes()
    {
        var edges = uniqueEdges(generateArea(2));

        for (int i = 0; i < edges.size(); i++)
        {
            for (int j = i + 1; j < edges.size(); j++)
                assertThat(properlyIntersect(edges.get(i), edges.get(j))).as("%s crosses %s", edges.get(i), edges.get(j)).isFalse();
        }
    }

    @Test
    void nodesHaveBetweenOneAndMaxLinesMatchingTheirEdges()
    {
        var regions = generateArea(1);
        var edges = uniqueEdges(regions);

        var degrees = new HashMap<Long, Integer>();
        for (var edge : edges)
        {
            degrees.merge(edge.from(), 1, Integer::sum);
            degrees.merge(edge.to(), 1, Integer::sum);
        }

        for (var region : regions)
        {
            for (var node : region.nodes())
            {
                assertThat(node.lines()).isBetween(1, MAX_LINES);
                assertThat(degrees.get(node.id())).isEqualTo(node.lines());
            }
        }
    }

    @Test
    void flowGoesDownPressure()
    {
        var generator = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES);
        var region = generator.generateRegion(0, 0);

        for (var edge : region.edges())
            assertThat(generator.pressure(edge.fromX(), edge.fromZ())).isGreaterThanOrEqualTo(generator.pressure(edge.toX(), edge.toZ()));
    }

    @Test
    void dumpLatticeForInspection() throws IOException
    {
        var regions = generateArea(2);
        var generator = new LeyLatticeGenerator(SEED, FAKE_STRUCTURES);

        Map<Integer, Integer> histogram = new TreeMap<>();
        Map<LeyNodeKind, Map<Integer, Integer>> kinds = new TreeMap<>();
        int nodeCount = 0;
        for (var region : regions)
        {
            for (var node : region.nodes())
            {
                nodeCount++;
                histogram.merge(node.lines(), 1, Integer::sum);
                kinds.computeIfAbsent(node.kind(), kind -> new TreeMap<>()).merge(node.lines(), 1, Integer::sum);
            }
        }
        var edges = uniqueEdges(regions);
        double meanLength = edges.stream().mapToDouble(LeyEdge::length).average().orElse(0);
        System.out.printf("nodes=%d edges=%d meanLength=%.0f lines=%s kinds=%s%n", nodeCount, edges.size(), meanLength, histogram, kinds);

        int size = 4 * REGION_SIZE;
        var image = LeyLatticeDump.render(regions, generator::pressure, -2 * REGION_SIZE, -2 * REGION_SIZE, size, 2048);
        var output = new File("build/ley/lattice.png");
        output.getParentFile().mkdirs();
        ImageIO.write(image, "png", output);
        assertThat(output).exists();
    }

    private static List<LeyEdge> uniqueEdges(List<LeyRegion> regions)
    {
        var seen = new HashSet<String>();
        var edges = new ArrayList<LeyEdge>();
        for (var region : regions)
        {
            for (var edge : region.edges())
            {
                if (seen.add(edge.from() + ":" + edge.to()))
                    edges.add(edge);
            }
        }
        return edges;
    }

    private static boolean properlyIntersect(LeyEdge a, LeyEdge b)
    {
        if (a.touches(b.from()) || a.touches(b.to()))
            return false;

        long d1 = cross(b.fromX(), b.fromZ(), b.toX(), b.toZ(), a.fromX(), a.fromZ());
        long d2 = cross(b.fromX(), b.fromZ(), b.toX(), b.toZ(), a.toX(), a.toZ());
        long d3 = cross(a.fromX(), a.fromZ(), a.toX(), a.toZ(), b.fromX(), b.fromZ());
        long d4 = cross(a.fromX(), a.fromZ(), a.toX(), a.toZ(), b.toX(), b.toZ());
        return Long.signum(d1) * Long.signum(d2) < 0 && Long.signum(d3) * Long.signum(d4) < 0;
    }

    private static long cross(long ax, long az, long bx, long bz, long px, long pz)
    {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }
}
