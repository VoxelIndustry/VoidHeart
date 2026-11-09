package net.voxelindustry.voidheart.common.ley.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;

/**
 * The one way ley is turned into something else. Using ley as ley never taints, so stones claim through {@link Ley#claim};
 * a spike converts through here, and its node takes taint from the share of its flow converted on it and its lines.
 */
public final class LeyConversion
{
    private LeyConversion()
    {
    }

    /**
     * Claims ley like {@link Ley#claim}, upstream first, and counts what was granted as converted on the claimant's node,
     * or on the upstream node of the line it stands on. Call it every tick while converting.
     *
     * @return the ley granted this tick
     */
    public static double convert(ServerLevel level, BlockPos claimant, double amount)
    {
        double granted = Ley.claim(level, claimant, amount);
        if (granted > 0)
        {
            LeyTopology.get(level)
                    .map(topology -> topology.attachment(claimant.getX(), claimant.getZ()))
                    .filter(attachment -> !attachment.isNone())
                    .ifPresent(attachment -> LeyTaint.recordConversion(attachment.sourceNode(), granted));
        }
        return granted;
    }
}
