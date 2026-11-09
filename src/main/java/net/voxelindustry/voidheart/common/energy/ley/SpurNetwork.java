package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The spurs of one level and the machines drawing on them. Each tick a spur claims from its line what its machines asked for, and every machine on it then
 * gets the same share of its need, so a short line weakens them all together. The line decides between spurs, upstream first.
 * Pure, so it can be tested without a level: the line comes in through {@link Line}.
 */
public class SpurNetwork
{
    /**
     * The lattice as a spur sees it.
     */
    public interface Line
    {
        /**
         * Returns what was granted, between 0 and amount.
         */
        double claim(BlockPos root, SpurTap tap, double amount);

        double flow(BlockPos root, SpurTap tap);
    }

    private record Demand(double need, long tick)
    {
    }

    private static final class Ledger
    {
        private final Map<BlockPos, Demand> demands = new HashMap<>();
        private float  share = Float.NaN;
        private double claimed;
        private double granted;
    }

    private final int supplyRadius;
    private final int demandTicks;

    private       SpurPlan                plan    = SpurPlan.EMPTY;
    private final Map<BlockPos, Ledger>   ledgers = new HashMap<>();

    public SpurNetwork(int supplyRadius, int demandTicks)
    {
        this.supplyRadius = supplyRadius;
        this.demandTicks = demandTicks;
    }

    public SpurPlan plan()
    {
        return plan;
    }

    /**
     * Ledgers of spurs that still exist carry over, minus machines that now draw on another spur.
     */
    public void replan(SpurPlan plan)
    {
        this.plan = plan;
        ledgers.keySet().removeIf(root -> plan.tap(root) == null);
        ledgers.forEach((root, ledger) -> ledger.demands.keySet().removeIf(consumer -> !root.equals(rootFor(consumer))));
    }

    /**
     * The connected stone a machine at pos draws through: the nearest one within the supply radius.
     */
    public @Nullable BlockPos stoneFor(BlockPos consumer)
    {
        BlockPos best = null;
        double bestSq = (double) supplyRadius * supplyRadius;
        for (var link : plan.links())
        {
            double distanceSq = link.stone().distSqr(consumer);
            if (distanceSq <= bestSq)
            {
                best = link.stone();
                bestSq = distanceSq;
            }
        }
        return best;
    }

    public @Nullable BlockPos rootFor(BlockPos consumer)
    {
        var stone = stoneFor(consumer);
        return stone == null ? null : plan.link(stone).root();
    }

    /**
     * The fraction of need met at pos this tick, and the need recorded for the spur's next claim. Until a spur has claimed for anyone, it offers what its line
     * carries, so a machine isn't left dark for its first tick.
     */
    public float supply(BlockPos consumer, double need, long tick, Line line)
    {
        if (!(need > 0))
            return 1;
        var root = rootFor(consumer);
        if (root == null)
            return 0;

        var ledger = ledgers.computeIfAbsent(root, key -> new Ledger());
        ledger.demands.put(consumer.immutable(), new Demand(need, tick));
        if (!Float.isNaN(ledger.share))
            return ledger.share;
        return (float) Mth.clamp(line.flow(root, plan.tap(root)) / need, 0, 1);
    }

    /**
     * Claims for every spur what its machines asked for within the last few ticks, and sets the share they get next.
     */
    public void tick(long tick, Line line)
    {
        ledgers.forEach((root, ledger) ->
        {
            ledger.demands.values().removeIf(demand -> tick - demand.tick() >= demandTicks);
            double demand = 0;
            for (var entry : ledger.demands.values())
                demand += entry.need();

            if (demand <= 0)
            {
                ledger.share = Float.NaN;
                ledger.claimed = 0;
                ledger.granted = 0;
                return;
            }
            double granted = Mth.clamp(line.claim(root, plan.tap(root), demand), 0, demand);
            ledger.claimed = demand;
            ledger.granted = granted;
            ledger.share = (float) (granted / demand);
        });
    }

    /**
     * What the spur rooted at root asked for and was granted on its last claim.
     */
    public double claimed(BlockPos root)
    {
        var ledger = ledgers.get(root);
        return ledger == null ? 0 : ledger.claimed;
    }

    public double granted(BlockPos root)
    {
        var ledger = ledgers.get(root);
        return ledger == null ? 0 : ledger.granted;
    }

    public int consumers(BlockPos root)
    {
        var ledger = ledgers.get(root);
        return ledger == null ? 0 : ledger.demands.size();
    }
}
