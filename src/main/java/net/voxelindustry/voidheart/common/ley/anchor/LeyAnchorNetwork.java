package net.voxelindustry.voidheart.common.ley.anchor;

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

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

/**
 * Which ley nodes carry an Old Anchor and which anchors are linked to each other.
 */
public class LeyAnchorNetwork
{
    public static final int MIN_LINES = 5;
    public static final float ANCHOR_CHANCE = 0.8F;
    public static final float INACTIVE_CHANCE = 0.5F;
    public static final double MAX_DOMAIN_REACH = 1200;
    private static final double MAX_LINE_LENGTH = 3 * LeyLatticeGenerator.CELL_SIZE;

    private static final long SALT_ANCHOR = 21;
    private static final long SALT_INACTIVE = 22;

    private final long seed;
    private final LeyLattice lattice;

    private final Map<Long, List<OldAnchor>> anchorsByRegion = new HashMap<>();
    private final Map<Long, List<OldAnchor>> linkedByNode = new HashMap<>();

    public LeyAnchorNetwork(long seed, LeyLattice lattice)
    {
        this.seed = seed;
        this.lattice = lattice;
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
                    boolean active = LeyHash.unit(LeyHash.hash(seed, node.id(), 0, SALT_INACTIVE)) >= INACTIVE_CHANCE;
                    anchors.add(new OldAnchor(node.id(), node.x(), node.z(), LeyAnchorNames.name(seed, node.id()), active));
                }
            }
            return anchors;
        });
    }

    private boolean hasAnchor(LeyNode node)
    {
        return node.lines() >= MIN_LINES
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
            // A node bordering the origin's domain is within reach plus one line of it, and its owner within reach of that node
            int seedRadius = (int) (2 * MAX_DOMAIN_REACH + MAX_LINE_LENGTH);
            var seeds = anchorsWithin(origin.x() - seedRadius, origin.z() - seedRadius, origin.x() + seedRadius, origin.z() + seedRadius);
            var owners = domains(seeds);

            var linked = new LinkedHashSet<OldAnchor>();
            for (var entry : owners.entrySet())
            {
                if (entry.getValue().anchor().nodeId() != origin.nodeId())
                    continue;
                for (var edge : lattice.edgesOf(entry.getKey()))
                {
                    var neighbor = owners.get(edge.other(entry.getKey()));
                    if (neighbor != null && neighbor.anchor().nodeId() != origin.nodeId())
                        linked.add(neighbor.anchor());
                }
            }

            var sorted = new ArrayList<>(linked);
            sorted.sort(Comparator.comparingDouble(anchor -> Math.hypot(anchor.x() - origin.x(), anchor.z() - origin.z())));
            return sorted;
        });
    }

    /**
     * Multi-source shortest paths from every seed anchor; ties go to the lowest node id so every caller agrees.
     */
    private Map<Long, Claim> domains(List<OldAnchor> seeds)
    {
        var owners = new HashMap<Long, Claim>();
        var queue = new PriorityQueue<Claim>(Comparator.comparingDouble(Claim::distance).thenComparingLong(claim -> claim.anchor().nodeId()));
        var reached = new HashMap<Long, Claim>();
        for (var anchor : seeds)
        {
            var claim = new Claim(anchor.nodeId(), anchor, 0);
            reached.put(anchor.nodeId(), claim);
            queue.add(claim);
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

                var previous = reached.get(next);
                if (previous == null || distance < previous.distance()
                        || distance == previous.distance() && claim.anchor().nodeId() < previous.anchor().nodeId())
                {
                    var nextClaim = new Claim(next, claim.anchor(), distance);
                    reached.put(next, nextClaim);
                    queue.add(nextClaim);
                }
            }
        }
        return owners;
    }

    private record Claim(long nodeId, OldAnchor anchor, double distance)
    {
    }
}
