package net.voxelindustry.voidheart.common.ley.flow;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Lookups on the frozen lattice that flow and taint make every tick, cached since explored regions never change.
 */
public final class LeyTopology
{
    /**
     * Where a claimant or a spot sits on the lattice: on a node, or on a line at a position from its upstream node, 0 to 1.
     */
    public record Attachment(long node, @Nullable LeyEdge edge, double position)
    {
        public static final Attachment NONE = new Attachment(0, null, 0);

        public boolean onLine()
        {
            return edge != null;
        }

        public boolean isNone()
        {
            return this == NONE;
        }

        /**
         * The node whose flow this spot draws on: the node itself, or a line's upstream node.
         */
        public long sourceNode()
        {
            return edge != null ? edge.from() : node;
        }
    }

    private static final int MAX_ATTACHMENTS = 8192;
    private static final Map<LeyLattice, LeyTopology> BY_LATTICE = new WeakHashMap<>();

    private final LeyLattice lattice;
    private final Long2ObjectOpenHashMap<Attachment> attachments = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Optional<LeyNode>> nodes = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<LeyEdge>> edges = new Long2ObjectOpenHashMap<>();

    private LeyTopology(LeyLattice lattice)
    {
        this.lattice = lattice;
    }

    public static Optional<LeyTopology> get(ServerLevel level)
    {
        return LeyLattice.get(level).map(lattice -> BY_LATTICE.computeIfAbsent(lattice, LeyTopology::new));
    }

    public static void clear()
    {
        BY_LATTICE.clear();
    }

    public LeyLattice lattice()
    {
        return lattice;
    }

    /**
     * A node counts within {@link FoundationBalance#NODE_REACH} blocks, before any line within {@link FoundationBalance#LINE_REACH}.
     */
    public Attachment attachment(int x, int z)
    {
        long key = (long) x << 32 | z & 0xFFFFFFFFL;
        var cached = attachments.get(key);
        if (cached != null)
            return cached;

        if (attachments.size() >= MAX_ATTACHMENTS)
            attachments.clear();
        var resolved = resolve(x, z);
        attachments.put(key, resolved);
        return resolved;
    }

    private Attachment resolve(int x, int z)
    {
        var node = lattice.nearestNode(x, z);
        if (node.isPresent() && node.get().distanceSq(x, z) <= (double) FoundationBalance.NODE_REACH * FoundationBalance.NODE_REACH)
            return new Attachment(node.get().id(), null, 0);

        var edge = lattice.nearestEdge(x, z);
        if (edge.isPresent() && edge.get().distanceSq(x, z) <= (double) FoundationBalance.LINE_REACH * FoundationBalance.LINE_REACH)
            return new Attachment(edge.get().from(), edge.get(), positionAlong(edge.get(), x, z));
        return Attachment.NONE;
    }

    public static double positionAlong(LeyEdge edge, double x, double z)
    {
        double dx = edge.toX() - edge.fromX();
        double dz = edge.toZ() - edge.fromZ();
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq <= 0)
            return 0;
        return Math.clamp(((x - edge.fromX()) * dx + (z - edge.fromZ()) * dz) / lengthSq, 0, 1);
    }

    public Optional<LeyNode> node(long nodeId)
    {
        return nodes.computeIfAbsent(nodeId, id -> lattice.node(id));
    }

    /**
     * Every line touching the node, both ways.
     */
    public List<LeyEdge> edges(long nodeId)
    {
        return edges.computeIfAbsent(nodeId, id -> List.copyOf(lattice.edgesOf(id)));
    }

    public long[] downstream(long nodeId)
    {
        return edges(nodeId).stream().filter(edge -> edge.from() == nodeId).mapToLong(LeyEdge::to).toArray();
    }

    public int downstreamLines(long nodeId)
    {
        int count = 0;
        for (var edge : edges(nodeId))
        {
            if (edge.from() == nodeId)
                count++;
        }
        return count;
    }
}
