package net.voxelindustry.voidheart.common.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;

/**
 * Lattice flow and taint for everything that uses ley. Using ley as ley never taints a node; only conversion calls {@link #addTaint}.
 */
public final class Ley
{
    private static LeyFlowProvider provider = new DefaultLeyFlow();

    private Ley()
    {
    }

    /**
     * Flow on the nearest line or node, 0 off the lattice or outside the overworld.
     */
    public static double lineFlowAt(ServerLevel level, BlockPos pos)
    {
        return Math.max(0, provider.lineFlowAt(level, pos));
    }

    public static Optional<LeyEdge> lineNear(ServerLevel level, BlockPos pos, int maxDistance)
    {
        return provider.lineNear(level, pos, maxDistance);
    }

    /**
     * Stones and spikes share a line, upstream first. Returns what was granted, never more than asked.
     */
    public static double claim(ServerLevel level, BlockPos claimant, double amount)
    {
        if (!(amount > 0))
            return 0;
        return Math.clamp(provider.claim(level, claimant, amount), 0, amount);
    }

    /**
     * Gives back what the claimant was granted, so claimants after it get that ley now rather than once the claim lapses.
     * A claim of nothing does nothing, so a claimant that probed with {@link #claim} and then drew nothing releases it here.
     */
    public static void release(ServerLevel level, BlockPos claimant)
    {
        provider.release(level, claimant);
    }

    /**
     * From 0 to 100.
     */
    public static float taint(ServerLevel level, long nodeId)
    {
        return provider.taint(level, nodeId);
    }

    /**
     * Amount may be negative, as a node heals.
     */
    public static void addTaint(ServerLevel level, long nodeId, float amount)
    {
        provider.addTaint(level, nodeId, amount);
    }

    public static TaintStage stage(ServerLevel level, long nodeId)
    {
        return provider.stage(level, nodeId);
    }

    public static void install(LeyFlowProvider provider)
    {
        Ley.provider = Objects.requireNonNull(provider);
    }

    public static LeyFlowProvider provider()
    {
        return provider;
    }
}
