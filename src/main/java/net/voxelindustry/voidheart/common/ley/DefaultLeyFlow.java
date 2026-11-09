package net.voxelindustry.voidheart.common.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintState;

import java.util.Optional;

/**
 * The flow before tides, awakening and taint shape it: a node gives its raw power, a line its upstream node's, and claims never compete.
 * Taint is really stored, in {@link LeyTaintState}.
 */
public class DefaultLeyFlow implements LeyFlowProvider
{
    @Override
    public double lineFlowAt(ServerLevel level, BlockPos pos)
    {
        return LeyLattice.get(level).map(lattice -> flowAt(lattice, pos.getX(), pos.getZ())).orElse(0D);
    }

    @Override
    public Optional<LeyEdge> lineNear(ServerLevel level, BlockPos pos, int maxDistance)
    {
        return LeyLattice.get(level).flatMap(lattice -> edgeNear(lattice, pos.getX(), pos.getZ(), maxDistance));
    }

    @Override
    public double claim(ServerLevel level, BlockPos claimant, double amount)
    {
        return Math.max(0, Math.min(amount, lineFlowAt(level, claimant)));
    }

    @Override
    public float taint(ServerLevel level, long nodeId)
    {
        return LeyTaintState.get(level).taint(nodeId);
    }

    @Override
    public void addTaint(ServerLevel level, long nodeId, float amount)
    {
        LeyTaintState.get(level).add(nodeId, amount);
    }

    @Override
    public TaintStage stage(ServerLevel level, long nodeId)
    {
        return TaintStage.of(taint(level, nodeId));
    }

    static double flowAt(LeyLattice lattice, int x, int z)
    {
        var node = lattice.nearestNode(x, z);
        if (node.isPresent() && node.get().distanceSq(x, z) <= (double) FoundationBalance.NODE_REACH * FoundationBalance.NODE_REACH)
            return node.get().power();

        return edgeNear(lattice, x, z, FoundationBalance.LINE_REACH)
                .flatMap(edge -> lattice.node(edge.from()))
                .map(upstream -> (double) upstream.power())
                .orElse(0D);
    }

    static Optional<LeyEdge> edgeNear(LeyLattice lattice, int x, int z, int maxDistance)
    {
        return lattice.nearestEdge(x, z).filter(edge -> edge.distanceSq(x, z) <= (double) maxDistance * maxDistance);
    }
}
