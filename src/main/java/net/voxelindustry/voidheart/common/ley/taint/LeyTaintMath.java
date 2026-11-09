package net.voxelindustry.voidheart.common.ley.taint;

import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;

import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * Taint gain, healing, spread and stages, in taint per minute. Pure, so it is unit tested and the simulation in
 * docs/plans/energy-numbers.md can be checked against it.
 */
public final class LeyTaintMath
{
    private LeyTaintMath()
    {
    }

    public static TaintStage stage(float taint)
    {
        if (taint >= LeyFlowBalance.BREACHED_FROM)
            return TaintStage.BREACHED;
        if (taint >= LeyFlowBalance.FESTERING_FROM)
            return TaintStage.FESTERING;
        if (taint >= LeyFlowBalance.TAINTED_FROM)
            return TaintStage.TAINTED;
        return TaintStage.CLEAN;
    }

    /**
     * @param share the ley converted on a node and its lines over the node's current flow
     */
    public static double gainPerMinute(double share)
    {
        if (!(share > LeyFlowBalance.TAINT_LIGHT))
            return 0;
        double over = (Math.min(share, 1) - LeyFlowBalance.TAINT_LIGHT) / (1 - LeyFlowBalance.TAINT_LIGHT);
        return LeyFlowBalance.TAINT_GAIN_FULL * over * over;
    }

    public static double healPerMinute(float taint)
    {
        return taint > 0 ? LeyFlowBalance.TAINT_HEAL_FLOOR + taint / LeyFlowBalance.TAINT_HEAL_DIVISOR : 0;
    }

    public static double saltFactor(boolean salted)
    {
        return salted ? LeyFlowBalance.SALT_SLOW : 1;
    }

    /**
     * One step of a single node: gain and healing over the given minutes, clamped to 0..100. Healing never takes more than there is.
     */
    public static float step(float taint, double gainPerMinute, double minutes)
    {
        double next = taint + gainPerMinute * minutes;
        next -= Math.min(next, healPerMinute(taint) * minutes);
        return (float) Math.clamp(next, 0, LeyFlowBalance.MAX_TAINT);
    }

    /**
     * What a gain on one node passes downstream, hop by hop: each neighbour takes {@link LeyFlowBalance#TAINT_DOWNSTREAM} of
     * what reached the node before it, slowed by its own salt, until a hop gains under {@link LeyFlowBalance#SPREAD_STOP} per
     * minute or {@link LeyFlowBalance#SPREAD_MAX_HOPS} is reached. The lattice flows from high to low pressure, so it has no loops,
     * and a node reached along two paths takes both.
     *
     * @param gainPerMinute what the source node gained, after its own salt
     * @param downstream    the nodes each node's lines flow to
     * @param salted        whether salt slows a node's gain
     * @return the gain per minute of every node reached, without the source
     */
    public static Long2DoubleMap spread(long source, double gainPerMinute, LongFunction<long[]> downstream, LongPredicate salted)
    {
        var reached = new Long2DoubleLinkedOpenHashMap();
        var frontier = new Long2DoubleLinkedOpenHashMap();
        frontier.put(source, gainPerMinute);

        for (int hop = 1; hop <= LeyFlowBalance.SPREAD_MAX_HOPS && !frontier.isEmpty(); hop++)
        {
            var next = new Long2DoubleLinkedOpenHashMap();
            for (var entry : frontier.long2DoubleEntrySet())
            {
                for (long neighbour : downstream.apply(entry.getLongKey()))
                {
                    double gain = entry.getDoubleValue() * LeyFlowBalance.TAINT_DOWNSTREAM * saltFactor(salted.test(neighbour));
                    if (gain >= LeyFlowBalance.SPREAD_STOP)
                        next.addTo(neighbour, gain);
                }
            }
            next.long2DoubleEntrySet().forEach(entry -> reached.addTo(entry.getLongKey(), entry.getDoubleValue()));
            frontier = next;
        }
        return reached;
    }
}
