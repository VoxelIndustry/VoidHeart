package net.voxelindustry.voidheart.common.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Flow and taint on the overworld lattice. The real flow, with tides, awakening and the taint penalty, installs itself through {@link Ley#install}.
 */
public interface LeyFlowProvider
{
    /**
     * Flow on the nearest line or node, 0 off the lattice or outside the overworld.
     */
    double lineFlowAt(ServerLevel level, BlockPos pos);

    Optional<LeyEdge> lineNear(ServerLevel level, BlockPos pos, int maxDistance);

    /**
     * Stones and spikes share a line, upstream first. Returns what was granted.
     */
    double claim(ServerLevel level, BlockPos claimant, double amount);

    float taint(ServerLevel level, long nodeId);

    void addTaint(ServerLevel level, long nodeId, float amount);

    TaintStage stage(ServerLevel level, long nodeId);
}
