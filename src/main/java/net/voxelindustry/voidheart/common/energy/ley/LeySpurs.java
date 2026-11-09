package net.voxelindustry.voidheart.common.energy.ley;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.ley.Ley;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spurs of each level, built from the saved standing stones. It is the real {@link net.voxelindustry.voidheart.common.energy.LeyPowerProvider}: machines
 * ask it for ley during the tick, and at the end of the tick each spur claims from its line what was asked.
 */
public final class LeySpurs
{
    private static final int DISPLAY_TICKS = 20;

    private static final Map<ServerLevel, LeySpurs> BY_LEVEL = new WeakHashMap<>();

    private final ServerLevel                       level;
    private final SpurNetwork                       network = new SpurNetwork(LeyStoneBalance.STONE_SUPPLY_RADIUS, LeyStoneBalance.DEMAND_TICKS);
    private final Map<BlockPos, Optional<SpurTap>>  taps    = new HashMap<>();
    private final Map<BlockPos, Float>              flows   = new HashMap<>();
    private final Map<BlockPos, Float>              taints  = new HashMap<>();
    private final Set<BlockPos>                     added   = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos>                     removed = ConcurrentHashMap.newKeySet();
    private final SpurNetwork.Line                  line;
    private volatile boolean                        dirty   = true;

    private LeySpurs(ServerLevel level)
    {
        this.level = level;
        line = new SpurNetwork.Line()
        {
            @Override
            public double claim(BlockPos root, SpurTap tap, double amount)
            {
                return Ley.claim(level, tap.claimant(root.getY()), amount);
            }

            @Override
            public double flow(BlockPos root, SpurTap tap)
            {
                return Ley.lineFlowAt(level, tap.claimant(root.getY()));
            }
        };
    }

    public static void register()
    {
        LeyPower.install(LeySpurs::supply);
        ServerTickEvents.END_LEVEL_TICK.register(level ->
        {
            var spurs = existing(level);
            if (spurs != null)
                spurs.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            synchronized (BY_LEVEL)
            {
                BY_LEVEL.clear();
            }
        });
    }

    public static LeySpurs get(ServerLevel level)
    {
        synchronized (BY_LEVEL)
        {
            return BY_LEVEL.computeIfAbsent(level, LeySpurs::new);
        }
    }

    private static @Nullable LeySpurs existing(ServerLevel level)
    {
        synchronized (BY_LEVEL)
        {
            return BY_LEVEL.get(level);
        }
    }

    static float supply(ServerLevel level, BlockPos pos, double need)
    {
        var spurs = get(level);
        if (spurs.dirty)
            spurs.replan();
        return spurs.network.supply(pos, need, level.getGameTime(), spurs.line);
    }

    /**
     * Called whenever a stone's tile joins the level, placed or loaded, so a stone the saved list missed still joins.
     */
    public void stoneLoaded(BlockPos stone)
    {
        added.add(stone.immutable());
        removed.remove(stone);
        dirty = true;
    }

    /**
     * Called only when a stone is really removed, never when its chunk unloads.
     */
    public void stoneRemoved(BlockPos stone)
    {
        removed.add(stone.immutable());
        added.remove(stone);
        dirty = true;
    }

    private void tick()
    {
        if (dirty)
            replan();
        network.tick(level.getGameTime(), line);
        if (level.getGameTime() % DISPLAY_TICKS == 0)
            refresh();
    }

    private void replan()
    {
        dirty = false;
        var state = LeyStoneState.get(level);
        added.removeIf(stone ->
        {
            state.add(stone);
            return true;
        });
        removed.removeIf(stone ->
        {
            state.remove(stone);
            taps.remove(stone);
            return true;
        });

        // Something that skipped the removal hooks, like a structure placed without side effects, can leave a stale entry in a loaded chunk
        for (var stone : Set.copyOf(state.stones()))
        {
            if (level.isLoaded(stone) && !isLowerStone(stone))
            {
                state.remove(stone);
                taps.remove(stone);
            }
        }
        for (var stone : state.stones())
            taps.computeIfAbsent(stone, this::findTap);
        taps.keySet().retainAll(state.stones());

        network.replan(SpurPlan.of(taps, LeyStoneBalance.STONE_REACH));
        refresh();
    }

    private boolean isLowerStone(BlockPos stone)
    {
        var state = level.getBlockState(stone);
        return state.getBlock() instanceof LeyStoneBlock && state.getValue(LeyStoneBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private Optional<SpurTap> findTap(BlockPos stone)
    {
        return Ley.lineNear(level, stone, LeyStoneBalance.STONE_REACH).map(edge -> SpurTap.of(edge, stone.getX() + 0.5, stone.getZ() + 0.5));
    }

    /**
     * Reads each spur's line again, for the tides and taint, and shows it on the loaded stones.
     */
    private void refresh()
    {
        var plan = network.plan();
        flows.clear();
        taints.clear();
        for (var root : plan.roots())
        {
            var tap = plan.tap(root);
            flows.put(root, (float) line.flow(root, tap));
            taints.put(root, Ley.taint(level, tap.fromNode()));
        }

        for (var stone : taps.keySet())
        {
            if (!level.isLoaded(stone) || !(level.getBlockEntity(stone) instanceof LeyStoneTile tile))
                continue;
            var link = plan.link(stone);
            if (link == null)
                tile.showDark();
            else
                tile.showSpur(link, plan.tap(link.root()), flows.get(link.root()), taints.get(link.root()), (float) plan.length(link.root()));
        }
    }

    public SpurPlan plan()
    {
        return network.plan();
    }

    public SpurNetwork network()
    {
        return network;
    }

    /**
     * The taint of the line feeding a machine at pos, from 0 to 100. Machines fed by no spur see none.
     */
    public float taintAt(BlockPos pos)
    {
        var root = network.rootFor(pos);
        return root == null ? 0 : taints.getOrDefault(root, 0F);
    }

    public float flow(BlockPos root)
    {
        return flows.getOrDefault(root, 0F);
    }
}
