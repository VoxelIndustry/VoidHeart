package net.voxelindustry.voidheart.common.ley.taint;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;

/**
 * Taint over time: gains from conversion and from anything calling {@code Ley.addTaint}, the share passed downstream,
 * slowing by salt, and healing that never stops. Runs once per {@link LeyFlowBalance#WINDOW_TICKS} on the overworld.
 */
public final class LeyTaint
{
    private static final double WINDOW_MINUTES = LeyFlowBalance.WINDOW_TICKS / 1200D;

    /**
     * Ley converted per node this window, summed over ticks.
     */
    private static final Long2DoubleOpenHashMap CONVERTED = new Long2DoubleOpenHashMap();
    /**
     * Taint added directly this window, already applied to its node and waiting to pass downstream.
     */
    private static final Long2DoubleOpenHashMap DIRECT_GAINS = new Long2DoubleOpenHashMap();
    private static final Long2LongOpenHashMap SALT_CHECKED_AT = new Long2LongOpenHashMap();
    private static final LongOpenHashSet SALTED = new LongOpenHashSet();

    private LeyTaint()
    {
    }

    public static float taint(ServerLevel level, long nodeId)
    {
        return LeyTaintState.get(level).taint(nodeId);
    }

    public static TaintStage stage(ServerLevel level, long nodeId)
    {
        return LeyTaintMath.stage(taint(level, nodeId));
    }

    /**
     * A gain is slowed by salt near the node, applied now, and passed downstream at the end of the window.
     * A negative amount heals and stays on the node.
     */
    public static void add(ServerLevel level, long nodeId, float amount)
    {
        if (!(amount > 0))
        {
            if (amount < 0)
                LeyTaintState.get(level).add(nodeId, amount);
            return;
        }

        float gain = (float) (amount * LeyTaintMath.saltFactor(isSalted(level, nodeId)));
        LeyTaintState.get(level).add(nodeId, gain);
        DIRECT_GAINS.addTo(nodeId, gain);
    }

    public static void set(ServerLevel level, long nodeId, float taint)
    {
        LeyTaintState.get(level).set(nodeId, taint);
    }

    /**
     * Converting ley taints its node once a window, by the share of the node's flow converted on it and its lines.
     */
    public static void recordConversion(long nodeId, double ley)
    {
        if (ley > 0)
            CONVERTED.addTo(nodeId, ley);
    }

    public static void tick(ServerLevel overworld)
    {
        if (overworld.getGameTime() % LeyFlowBalance.WINDOW_TICKS != 0)
            return;

        var topology = LeyTopology.get(overworld);
        if (topology.isPresent())
            window(overworld, topology.get());
        LatticeLeyFlow.pruneClaims(overworld.getGameTime());
        LeyTaintSync.send(overworld);
    }

    private static void window(ServerLevel level, LeyTopology topology)
    {
        var state = LeyTaintState.get(level);
        var gains = new Long2DoubleOpenHashMap();
        var sources = new Long2DoubleOpenHashMap();

        for (var entry : CONVERTED.long2DoubleEntrySet())
        {
            long node = entry.getLongKey();
            double flow = LatticeLeyFlow.nodeFlow(level, node);
            double share = flow > 0 ? entry.getDoubleValue() / LeyFlowBalance.WINDOW_TICKS / flow : 1;
            double gain = LeyTaintMath.gainPerMinute(share) * LeyTaintMath.saltFactor(isSalted(level, node));
            if (gain > 0)
            {
                gains.addTo(node, gain);
                sources.addTo(node, gain);
            }
        }
        DIRECT_GAINS.long2DoubleEntrySet().forEach(entry -> sources.addTo(entry.getLongKey(), entry.getDoubleValue() / WINDOW_MINUTES));

        for (var source : sources.long2DoubleEntrySet())
        {
            LeyTaintMath.spread(source.getLongKey(), source.getDoubleValue(), topology::downstream, node -> isSalted(level, node))
                    .long2DoubleEntrySet()
                    .forEach(reached -> gains.addTo(reached.getLongKey(), reached.getDoubleValue()));
        }

        var nodes = new LongOpenHashSet(state.tainted());
        nodes.addAll(gains.keySet());
        for (long node : nodes)
            state.set(node, LeyTaintMath.step(state.taint(node), gains.get(node), WINDOW_MINUTES));

        CONVERTED.clear();
        DIRECT_GAINS.clear();
    }

    /**
     * Whether salt lies within {@link LeyFlowBalance#SALT_RANGE} blocks of the node, looked for again every
     * {@link LeyFlowBalance#SALT_CHECK_TICKS}. Unloaded ground keeps what was last seen there.
     */
    public static boolean isSalted(ServerLevel level, long nodeId)
    {
        long now = level.getGameTime();
        long checkedAt = SALT_CHECKED_AT.getOrDefault(nodeId, Long.MIN_VALUE);
        if (checkedAt != Long.MIN_VALUE && now - checkedAt < LeyFlowBalance.SALT_CHECK_TICKS && now >= checkedAt)
            return SALTED.contains(nodeId);

        var node = LeyTopology.get(level).flatMap(topology -> topology.node(nodeId));
        if (node.isEmpty() || !level.isLoaded(new BlockPos(node.get().x(), 0, node.get().z())))
            return SALTED.contains(nodeId);

        boolean salted = scanForSalt(level, node.get().x(), node.get().z());
        SALT_CHECKED_AT.put(nodeId, now);
        if (salted)
            SALTED.add(nodeId);
        else
            SALTED.remove(nodeId);
        return salted;
    }

    private static boolean scanForSalt(ServerLevel level, int nodeX, int nodeZ)
    {
        int range = LeyFlowBalance.SALT_RANGE;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nodeX, nodeZ);
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++)
        {
            for (int dz = -range; dz <= range; dz++)
            {
                if (dx * dx + dz * dz > range * range || !level.isLoaded(pos.set(nodeX + dx, ground, nodeZ + dz)))
                    continue;
                for (int y = ground - LeyFlowBalance.SALT_HEIGHT; y <= ground + LeyFlowBalance.SALT_HEIGHT; y++)
                {
                    if (SaltWard.isSalt(level, pos.set(nodeX + dx, y, nodeZ + dz)))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Salt laid or swept takes effect at the next check; this forgets every check, for commands and tests.
     */
    public static void forgetSalt()
    {
        SALT_CHECKED_AT.clear();
        SALTED.clear();
    }

    public static void clear()
    {
        CONVERTED.clear();
        DIRECT_GAINS.clear();
        forgetSalt();
    }
}
