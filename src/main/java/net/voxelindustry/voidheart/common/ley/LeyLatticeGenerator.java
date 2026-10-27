package net.voxelindustry.voidheart.common.ley;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic ley lattice: nodes are placed first, then linked by a planar (Gabriel) graph.
 * Every decision only reads a bounded neighborhood, so any region can be generated on its own
 * and agrees with its neighbors on the edges they share.
 */
public class LeyLatticeGenerator
{
    public static final int CELL_SIZE = 256;
    public static final int REGION_CELLS = 8;
    public static final int REGION_SIZE = CELL_SIZE * REGION_CELLS;
    public static final int MAX_LINES = 8;

    private static final int CELL_MARGIN = 40;
    private static final int ANCHOR_MERGE_RADIUS = 96;
    private static final double SUPPRESSION_RANGE = 1.6 * CELL_SIZE;
    private static final double MAX_EDGE_LENGTH = 3 * CELL_SIZE;
    private static final int MIN_LINES = 2;
    private static final double PRESSURE_SCALE = 1536;

    // Suppression reaches 1.6 cells and edges 3 cells, plus one for in-cell positions.
    // Delaunay checks look 5 cells around an edge's midpoint: farther nodes can only bind circles
    // larger than any empty one the dense lattice leaves around a short edge.
    private static final int SUPPRESSION_REACH = 2;
    private static final int EDGE_REACH = 4;
    private static final int DELAUNAY_REACH = 5;

    private static final long SALT_POSITION = 1;
    private static final long SALT_WEIGHT = 2;
    private static final long SALT_PRESSURE = 3;

    private final long seed;
    private final LeyAnchorSource anchorSource;

    private final Map<Long, List<Candidate>> candidatesByCell = new HashMap<>();
    private final Map<Long, List<Candidate>> nodesByCell = new HashMap<>();
    private final Map<Long, List<Candidate>> planarByNode = new HashMap<>();

    public LeyLatticeGenerator(long seed, LeyAnchorSource anchorSource)
    {
        this.seed = seed;
        this.anchorSource = anchorSource;
    }

    public LeyRegion generateRegion(int regionX, int regionZ)
    {
        var nodes = new ArrayList<LeyNode>();
        var edges = new ArrayList<LeyEdge>();

        for (int cellX = regionX * REGION_CELLS; cellX < (regionX + 1) * REGION_CELLS; cellX++)
        {
            for (int cellZ = regionZ * REGION_CELLS; cellZ < (regionZ + 1) * REGION_CELLS; cellZ++)
            {
                for (var node : nodes(cellX, cellZ))
                {
                    var linked = linkedNodes(node);
                    if (linked.isEmpty())
                        continue;

                    nodes.add(new LeyNode(node.id, node.x, node.z, node.kind, node.weight, linked.size(), power(node.weight, linked.size())));

                    for (var other : linked)
                    {
                        boolean otherInRegion = Math.floorDiv(other.x, REGION_SIZE) == regionX && Math.floorDiv(other.z, REGION_SIZE) == regionZ;
                        if (!otherInRegion || node.id < other.id)
                            edges.add(orient(node, other));
                    }
                }
            }
        }

        candidatesByCell.clear();
        nodesByCell.clear();
        planarByNode.clear();
        return new LeyRegion(regionX, regionZ, nodes, edges);
    }

    public float pressure(double x, double z)
    {
        return LeyHash.valueNoise(seed + SALT_PRESSURE, x, z, PRESSURE_SCALE);
    }

    public static float power(float weight, int lines)
    {
        return (float) (Math.pow(lines, 1.5) * (0.6 + 0.8 * weight));
    }

    public static long nodeId(int cellX, int cellZ, int index)
    {
        return ((long) cellX & 0xFFFFFFL) << 36 | ((long) cellZ & 0xFFFFFFL) << 12 | index & 0xFFFL;
    }

    private LeyEdge orient(Candidate a, Candidate b)
    {
        float pressureA = pressure(a.x, a.z);
        float pressureB = pressure(b.x, b.z);
        boolean aFirst = pressureA > pressureB || pressureA == pressureB && a.id < b.id;

        var from = aFirst ? a : b;
        var to = aFirst ? b : a;
        return new LeyEdge(from.id, to.id, from.x, from.z, to.x, to.z);
    }

    private List<Candidate> candidates(int cellX, int cellZ)
    {
        return candidatesByCell.computeIfAbsent(cellKey(cellX, cellZ), key ->
        {
            int minX = cellX * CELL_SIZE;
            int minZ = cellZ * CELL_SIZE;
            var anchors = new ArrayList<>(anchorSource.anchorsIn(minX, minZ, minX + CELL_SIZE - 1, minZ + CELL_SIZE - 1));
            var candidates = new ArrayList<Candidate>();

            if (anchors.isEmpty())
            {
                long position = LeyHash.hash(seed, cellX, cellZ, SALT_POSITION);
                int span = CELL_SIZE - 2 * CELL_MARGIN;
                int x = minX + CELL_MARGIN + (int) ((position >>> 1) % span);
                int z = minZ + CELL_MARGIN + (int) ((position >>> 33) % span);
                float weight = LeyNodeKind.WILD.weight(LeyHash.unit(LeyHash.hash(seed, cellX, cellZ, SALT_WEIGHT)));
                candidates.add(new Candidate(nodeId(cellX, cellZ, 0), x, z, LeyNodeKind.WILD, weight));
                return candidates;
            }

            anchors.sort(Comparator.comparingInt(LeyAnchorSource.Anchor::x).thenComparingInt(LeyAnchorSource.Anchor::z));
            for (int i = 0; i < anchors.size(); i++)
            {
                var anchor = anchors.get(i);
                float roll = LeyHash.unit(LeyHash.hash(seed, anchor.x(), anchor.z(), SALT_WEIGHT));
                candidates.add(new Candidate(nodeId(cellX, cellZ, i + 1), anchor.x(), anchor.z(), anchor.kind(), anchor.kind().weight(roll)));
            }
            return candidates;
        });
    }

    /**
     * Candidates surviving suppression. Suppression is only tested against raw candidates, never chained,
     * so a cell's nodes depend on the candidates within {@link #SUPPRESSION_REACH} cells only.
     */
    private List<Candidate> nodes(int cellX, int cellZ)
    {
        return nodesByCell.computeIfAbsent(cellKey(cellX, cellZ), key ->
        {
            var survivors = new ArrayList<Candidate>();
            for (var candidate : candidates(cellX, cellZ))
            {
                if (!isSuppressed(candidate, cellX, cellZ))
                    survivors.add(candidate);
            }
            return survivors;
        });
    }

    private boolean isSuppressed(Candidate candidate, int cellX, int cellZ)
    {
        for (int dx = -SUPPRESSION_REACH; dx <= SUPPRESSION_REACH; dx++)
        {
            for (int dz = -SUPPRESSION_REACH; dz <= SUPPRESSION_REACH; dz++)
            {
                for (var other : candidates(cellX + dx, cellZ + dz))
                {
                    if (other.id == candidate.id || !other.outranks(candidate))
                        continue;

                    double range = candidate.kind.isAnchor() ? (other.kind.isAnchor() ? ANCHOR_MERGE_RADIUS : 0) : SUPPRESSION_RANGE * Math.pow(other.weight, 1.5);
                    if (candidate.distanceSq(other) < range * range)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Delaunay neighbors within {@link #MAX_EDGE_LENGTH}: candidate lines that can never cross each other.
     * Sorted by preference, closer and heavier first, the order used to spend line budgets and apply the cap.
     */
    private List<Candidate> planarNeighbors(Candidate node)
    {
        return planarByNode.computeIfAbsent(node.id, key ->
        {
            var neighbors = new ArrayList<Candidate>();
            for (var other : nodesAround(node.x, node.z, EDGE_REACH))
            {
                if (other.id != node.id && node.distanceSq(other) <= MAX_EDGE_LENGTH * MAX_EDGE_LENGTH && isDelaunay(node, other))
                    neighbors.add(other);
            }

            neighbors.sort(Comparator.<Candidate>comparingDouble(other -> Math.sqrt(node.distanceSq(other)) / (0.6 + other.weight))
                    .thenComparingLong(Candidate::id));
            return neighbors;
        });
    }

    /**
     * AB is a Delaunay edge when some circle through A and B contains no other node.
     * Circle centers slide along the bisector of AB: a node on the left caps how far left the center may go,
     * one on the right how far right, and an empty circle exists while the two bounds don't meet.
     * Only nodes around the midpoint are considered so both endpoints evaluate the exact same set.
     */
    private boolean isDelaunay(Candidate a, Candidate b)
    {
        double midX = (a.x + b.x) / 2D;
        double midZ = (a.z + b.z) / 2D;
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double normalX = -dz / length;
        double normalZ = dx / length;
        double halfLengthSq = (dx * dx + dz * dz) / 4;

        double leftBound = Double.POSITIVE_INFINITY;
        double rightBound = Double.NEGATIVE_INFINITY;
        for (var other : nodesAround(midX, midZ, DELAUNAY_REACH))
        {
            if (other.id == a.id || other.id == b.id)
                continue;

            double px = other.x - midX;
            double pz = other.z - midZ;
            double side = px * normalX + pz * normalZ;
            double power = px * px + pz * pz - halfLengthSq;

            if (side == 0)
            {
                if (power < 0)
                    return false;
                continue;
            }

            double bound = power / (2 * side);
            if (side > 0)
                leftBound = Math.min(leftBound, bound);
            else
                rightBound = Math.max(rightBound, bound);
        }
        return rightBound < leftBound;
    }

    private List<Candidate> nodesAround(double x, double z, int reach)
    {
        int cellX = (int) Math.floor(x / CELL_SIZE);
        int cellZ = (int) Math.floor(z / CELL_SIZE);

        var nearby = new ArrayList<Candidate>();
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
                nearby.addAll(nodes(cellX + dx, cellZ + dz));
        }
        return nearby;
    }

    private List<Candidate> linkedNodes(Candidate node)
    {
        var linked = new ArrayList<Candidate>();
        for (var other : planarNeighbors(node))
        {
            if (isLinked(node, other))
                linked.add(other);
        }
        return linked;
    }

    /**
     * Symmetric in a and b: both sides of a border reach the same answer.
     */
    private boolean isLinked(Candidate a, Candidate b)
    {
        var neighborsA = planarNeighbors(a);
        var neighborsB = planarNeighbors(b);

        int rankOfB = neighborsA.indexOf(b);
        int rankOfA = neighborsB.indexOf(a);
        if (rankOfB < 0 || rankOfA < 0 || rankOfB >= MAX_LINES || rankOfA >= MAX_LINES)
            return false;

        return rankOfB < lineBudget(a) || rankOfA < lineBudget(b);
    }

    /**
     * Lines a node reaches out for by itself; it can end up with more when neighbors pick it.
     */
    private static int lineBudget(Candidate node)
    {
        return MIN_LINES + (int) ((MAX_LINES - MIN_LINES + 0.99F) * node.weight * node.weight);
    }

    private static long cellKey(int cellX, int cellZ)
    {
        return (long) cellX << 32 | cellZ & 0xFFFFFFFFL;
    }

    private record Candidate(long id, int x, int z, LeyNodeKind kind, float weight)
    {
        boolean outranks(Candidate other)
        {
            if (kind != other.kind)
                return kind.ordinal() > other.kind.ordinal();
            if (weight != other.weight)
                return weight > other.weight;
            return id < other.id;
        }

        double distanceSq(Candidate other)
        {
            double dx = other.x - x;
            double dz = other.z - z;
            return dx * dx + dz * dz;
        }
    }
}
