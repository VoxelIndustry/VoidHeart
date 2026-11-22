package net.voxelindustry.voidheart.common.ley.anchor;

import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyHash;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.LeyRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

/**
 * Which ley nodes carry an Old Anchor and which anchors are linked to each other.
 */
public class LeyAnchorNetwork
{
    public static final int MIN_LINES = 5;
    public static final float ANCHOR_CHANCE = 0.8F;
    public static final float DEFAULT_INACTIVE_CHANCE = 0.5F;
    public static final double MAX_DOMAIN_REACH = 1200;
    private static final double MAX_LINE_LENGTH = 3 * LeyLatticeGenerator.CELL_SIZE;
    /**
     * A node bordering a domain is within reach plus one line of it, and its owner within reach of that node: anchors farther apart are never linked.
     */
    public static final int SEED_RADIUS = (int) (2 * MAX_DOMAIN_REACH + MAX_LINE_LENGTH);

    private static final long SALT_ANCHOR = 21;
    private static final long SALT_INACTIVE = 22;
    // Higher than any node id, so an Old Anchor keeps a node a point ties it for, its own above all
    private static final long POINT_OWNER = Long.MAX_VALUE;

    private final long seed;
    private final LeyLattice lattice;
    private final boolean generateAnchors;
    private final float inactiveChance;

    private final Map<Long, List<OldAnchor>> anchorsByRegion = new HashMap<>();
    private final Map<Long, List<OldAnchor>> linkedByNode = new HashMap<>();

    public LeyAnchorNetwork(long seed, LeyLattice lattice)
    {
        this(seed, lattice, true, DEFAULT_INACTIVE_CHANCE);
    }

    public LeyAnchorNetwork(long seed, LeyLattice lattice, boolean generateAnchors, float inactiveChance)
    {
        this.seed = seed;
        this.lattice = lattice;
        this.generateAnchors = generateAnchors;
        this.inactiveChance = inactiveChance;
    }

    public List<OldAnchor> anchorsIn(int regionX, int regionZ)
    {
        return anchorsByRegion.computeIfAbsent(LeyRegion.key(regionX, regionZ), key ->
        {
            var anchors = new ArrayList<OldAnchor>();
            for (var node : lattice.region(regionX, regionZ).nodes())
            {
                if (hasAnchor(node))
                {
                    boolean active = LeyHash.unit(LeyHash.hash(seed, node.id(), 0, SALT_INACTIVE)) >= inactiveChance;
                    anchors.add(new OldAnchor(node.id(), node.x(), node.z(), LeyAnchorNames.name(seed, node.id()), active));
                }
            }
            return anchors;
        });
    }

    private boolean hasAnchor(LeyNode node)
    {
        return generateAnchors
                && node.lines() >= MIN_LINES
                && LeyHash.unit(LeyHash.hash(seed, node.id(), 0, SALT_ANCHOR)) < ANCHOR_CHANCE
                && !lattice.heights().isSubmerged(node.x(), node.z());
    }

    public Optional<OldAnchor> anchor(long nodeId)
    {
        return anchorsIn(LeyLatticeGenerator.regionXOf(nodeId), LeyLatticeGenerator.regionZOf(nodeId)).stream()
                .filter(anchor -> anchor.nodeId() == nodeId)
                .findFirst();
    }

    public Optional<OldAnchor> anchorAt(int x, int z)
    {
        return anchorsIn(Math.floorDiv(x, REGION_SIZE), Math.floorDiv(z, REGION_SIZE)).stream()
                .filter(anchor -> anchor.x() == x && anchor.z() == z)
                .findFirst();
    }

    public List<OldAnchor> anchorsWithin(int minX, int minZ, int maxX, int maxZ)
    {
        var anchors = new ArrayList<OldAnchor>();
        for (int regionX = Math.floorDiv(minX, REGION_SIZE); regionX <= Math.floorDiv(maxX, REGION_SIZE); regionX++)
        {
            for (int regionZ = Math.floorDiv(minZ, REGION_SIZE); regionZ <= Math.floorDiv(maxZ, REGION_SIZE); regionZ++)
            {
                for (var anchor : anchorsIn(regionX, regionZ))
                {
                    if (anchor.x() >= minX && anchor.x() <= maxX && anchor.z() >= minZ && anchor.z() <= maxZ)
                        anchors.add(anchor);
                }
            }
        }
        return anchors;
    }

    /**
     * Every node belongs to the anchor closest to it along the lines (its domain), up to {@link #MAX_DOMAIN_REACH}.
     * Two anchors are linked when a line joins their domains, so links are symmetric and stay local.
     */
    public List<OldAnchor> linked(OldAnchor origin)
    {
        return linkedByNode.computeIfAbsent(origin.nodeId(), key ->
        {
            var seeds = seedsAround(origin.x(), origin.z());
            var owners = domains(seeds);

            var linked = new LinkedHashSet<Long>();
            for (var entry : owners.entrySet())
            {
                if (entry.getValue().owner() == origin.nodeId())
                    addNeighborOwners(entry.getKey(), origin.nodeId(), owners, linked);
            }
            return sortedAnchors(linked, origin.x(), origin.z());
        });
    }

    /**
     * The Old Anchors an anchor standing at x, z beside its nearest line would be linked to. It enters the line between the line's two nodes and
     * claims them at their distance along it; it always reaches the anchors whose domains hold those two nodes. No other point takes part, so
     * placing one never changes the links between Old Anchors.
     */
    public List<OldAnchor> linkedAt(int x, int z)
    {
        var edge = lattice.nearestEdge(x, z);
        if (edge.isEmpty())
            return List.of();

        var line = edge.get();
        double along = alongLine(line, x, z);
        var seeds = new ArrayList<>(seedsAround(x, z));
        seeds.add(new Seed(POINT_OWNER, line.from(), along));
        seeds.add(new Seed(POINT_OWNER, line.to(), line.length() - along));
        var owners = domains(seeds);

        var linked = new LinkedHashSet<Long>();
        for (long end : new long[] { line.from(), line.to() })
        {
            var claim = owners.get(end);
            if (claim != null && claim.owner() != POINT_OWNER)
                linked.add(claim.owner());
        }
        for (var entry : owners.entrySet())
        {
            if (entry.getValue().owner() == POINT_OWNER)
                addNeighborOwners(entry.getKey(), POINT_OWNER, owners, linked);
        }
        return sortedAnchors(linked, x, z);
    }

    /**
     * Whether x, z lies within reach blocks of a ley line, measured flat.
     */
    public boolean nearLine(int x, int z, int reach)
    {
        return lattice.nearestEdge(x, z).filter(edge -> edge.distanceSq(x + 0.5, z + 0.5) <= (double) reach * reach).isPresent();
    }

    private static double alongLine(LeyEdge line, double x, double z)
    {
        double dx = line.toX() - line.fromX();
        double dz = line.toZ() - line.fromZ();
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq == 0)
            return 0;
        double t = Math.clamp(((x - line.fromX()) * dx + (z - line.fromZ()) * dz) / lengthSq, 0, 1);
        return t * Math.sqrt(lengthSq);
    }

    private List<Seed> seedsAround(int x, int z)
    {
        return anchorsWithin(x - SEED_RADIUS, z - SEED_RADIUS, x + SEED_RADIUS, z + SEED_RADIUS).stream()
                .map(anchor -> new Seed(anchor.nodeId(), anchor.nodeId(), 0))
                .toList();
    }

    private void addNeighborOwners(long nodeId, long self, Map<Long, Claim> owners, Set<Long> linked)
    {
        for (var edge : lattice.edgesOf(nodeId))
        {
            var neighbor = owners.get(edge.other(nodeId));
            if (neighbor != null && neighbor.owner() != self)
                linked.add(neighbor.owner());
        }
    }

    private List<OldAnchor> sortedAnchors(Set<Long> nodeIds, int x, int z)
    {
        var sorted = new ArrayList<OldAnchor>();
        for (long nodeId : nodeIds)
            anchor(nodeId).ifPresent(sorted::add);
        sorted.sort(Comparator.comparingDouble(anchor -> Math.hypot(anchor.x() - x, anchor.z() - z)));
        return sorted;
    }

    /**
     * Multi-source shortest paths from every seed; ties go to the lowest owner so every caller agrees.
     */
    private Map<Long, Claim> domains(List<Seed> seeds)
    {
        var owners = new HashMap<Long, Claim>();
        var queue = new PriorityQueue<Claim>(Comparator.comparingDouble(Claim::distance).thenComparingLong(Claim::owner));
        var reached = new HashMap<Long, Claim>();
        for (var seed : seeds)
        {
            var claim = new Claim(seed.nodeId(), seed.owner(), seed.distance());
            var previous = reached.get(seed.nodeId());
            if (previous == null || isBetter(claim, previous))
            {
                reached.put(seed.nodeId(), claim);
                queue.add(claim);
            }
        }

        while (!queue.isEmpty())
        {
            var claim = queue.poll();
            if (owners.containsKey(claim.nodeId()))
                continue;
            owners.put(claim.nodeId(), claim);

            for (var edge : lattice.edgesOf(claim.nodeId()))
            {
                long next = edge.other(claim.nodeId());
                double distance = claim.distance() + edge.length();
                if (distance > MAX_DOMAIN_REACH || owners.containsKey(next))
                    continue;

                var nextClaim = new Claim(next, claim.owner(), distance);
                var previous = reached.get(next);
                if (previous == null || isBetter(nextClaim, previous))
                {
                    reached.put(next, nextClaim);
                    queue.add(nextClaim);
                }
            }
        }
        return owners;
    }

    private static boolean isBetter(Claim claim, Claim than)
    {
        return claim.distance() < than.distance() || claim.distance() == than.distance() && claim.owner() < than.owner();
    }

    private record Seed(long owner, long nodeId, double distance)
    {
    }

    private record Claim(long nodeId, long owner, double distance)
    {
    }
}
