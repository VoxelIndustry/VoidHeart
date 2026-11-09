package net.voxelindustry.voidheart.common.ley.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyFlowProvider;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.anchor.LeyAnchorState;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;

import java.util.List;
import java.util.Optional;

/**
 * The real flow on the overworld lattice: node power shaped by the moon tide, an awakened Old Anchor and taint, shared down
 * the node's lines, with claims served upstream first.
 */
public class LatticeLeyFlow implements LeyFlowProvider
{
    private static final LeyClaimLedger LEDGER = new LeyClaimLedger(LeyFlowBalance.CLAIM_HOLD_TICKS);

    private record NodePool(long node)
    {
    }

    private record LinePool(long from, long to)
    {
    }

    @Override
    public double lineFlowAt(ServerLevel level, BlockPos pos)
    {
        return LeyTopology.get(level).map(topology -> flowAt(level, topology, topology.attachment(pos.getX(), pos.getZ()))).orElse(0D);
    }

    @Override
    public Optional<LeyEdge> lineNear(ServerLevel level, BlockPos pos, int maxDistance)
    {
        return LeyTopology.get(level).flatMap(topology -> topology.lattice().nearestEdge(pos.getX(), pos.getZ()))
                .filter(edge -> edge.distanceSq(pos.getX(), pos.getZ()) <= (double) maxDistance * maxDistance);
    }

    @Override
    public double claim(ServerLevel level, BlockPos claimant, double amount)
    {
        var topology = LeyTopology.get(level);
        if (topology.isEmpty())
            return 0;

        var attachment = topology.get().attachment(claimant.getX(), claimant.getZ());
        if (attachment.isNone())
            return 0;

        long tick = level.getGameTime();
        if (!attachment.onLine())
            return LEDGER.claim(new NodePool(attachment.node()), claimant.asLong(), 0, nodeFlow(level, attachment.node()), amount, tick);

        var edge = attachment.edge();
        return LEDGER.claim(new LinePool(edge.from(), edge.to()), claimant.asLong(), attachment.position(), lineFlow(level, topology.get(), edge), amount, tick);
    }

    /**
     * Records a grant of nothing, so the claimant keeps its place among claimants at the same point if it claims again within the hold.
     */
    @Override
    public void release(ServerLevel level, BlockPos claimant)
    {
        claim(level, claimant, 0);
    }

    @Override
    public float taint(ServerLevel level, long nodeId)
    {
        return LeyTaint.taint(level, nodeId);
    }

    @Override
    public void addTaint(ServerLevel level, long nodeId, float amount)
    {
        LeyTaint.add(level, nodeId, amount);
    }

    @Override
    public TaintStage stage(ServerLevel level, long nodeId)
    {
        return LeyTaint.stage(level, nodeId);
    }

    /**
     * Flow at a spot: a node's whole flow, or a line's share of its upstream node.
     */
    public static double flowAt(ServerLevel level, LeyTopology topology, LeyTopology.Attachment attachment)
    {
        if (attachment.isNone())
            return 0;
        return attachment.onLine() ? lineFlow(level, topology, attachment.edge()) : nodeFlow(level, attachment.node());
    }

    /**
     * The node's raw power × the tide × the awakening bonus × the taint penalty.
     */
    public static double nodeFlow(ServerLevel level, long nodeId)
    {
        var topology = LeyTopology.get(level);
        if (topology.isEmpty())
            return 0;
        return topology.get().node(nodeId)
                .map(node -> LeyFlowMath.nodeFlow(node.power(), moonPhase(level), isAwakened(level, nodeId), LeyTaint.taint(level, nodeId)))
                .orElse(0D);
    }

    /**
     * What a line carries: its upstream node's flow, less the claims on the node itself, shared by the node's downstream lines.
     */
    public static double lineFlow(ServerLevel level, LeyTopology topology, LeyEdge edge)
    {
        return LeyFlowMath.lineFlow(nodeFlow(level, edge.from()), claimedOnNode(level, edge.from()), topology.downstreamLines(edge.from()));
    }

    public static double claimedOnNode(ServerLevel level, long nodeId)
    {
        return LEDGER.granted(new NodePool(nodeId), level.getGameTime());
    }

    public static double claimedOnLine(ServerLevel level, LeyEdge edge)
    {
        return LEDGER.granted(new LinePool(edge.from(), edge.to()), level.getGameTime());
    }

    public static List<LeyClaimLedger.Claim> claims(ServerLevel level, LeyTopology.Attachment attachment)
    {
        if (attachment.isNone())
            return List.of();
        Object pool = attachment.onLine() ? new LinePool(attachment.edge().from(), attachment.edge().to()) : new NodePool(attachment.node());
        return LEDGER.claims(pool, level.getGameTime());
    }

    public static int moonPhase(ServerLevel level)
    {
        return level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE).index();
    }

    public static boolean isAwakened(ServerLevel level, long nodeId)
    {
        return LeyAnchorState.get(level).isAwakened(nodeId);
    }

    public static void pruneClaims(long tick)
    {
        LEDGER.prune(tick);
    }

    public static void clear()
    {
        LEDGER.clear();
    }
}
