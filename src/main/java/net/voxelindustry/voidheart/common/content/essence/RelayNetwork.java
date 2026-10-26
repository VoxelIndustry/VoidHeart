package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes essence across the relays linked together. Collectors are served fullest first: each claims a path of relays
 * free or already carrying its aspect, down to a storage with room for it. A relay carries a single aspect at a time,
 * so a fuller collector of another aspect takes the shared relays over.
 */
public final class RelayNetwork
{
    public static final int ROUTE_INTERVAL_TICKS = 10;
    public static final int TRANSFER_AMOUNT      = 250;

    /**
     * Fill bonus of collectors already flowing, so two collectors of different aspects near the same fill do not
     * take turns every route.
     */
    private static final float FLOWING_BONUS = 0.1F;

    private RelayNetwork()
    {
    }

    private record Path(List<RavenousRelayTile> relays, BlockPos receiverPos, IEssenceReceiver receiver)
    {
    }

    private static class Flow
    {
        EssenceType aspect = EssenceRegistry.EMPTY;
        final List<BlockPos> sources = new ArrayList<>();
        final List<BlockPos> targets = new ArrayList<>();
    }

    /**
     * Only the relay with the lowest position routes, once for its whole network.
     */
    static void route(RavenousRelayTile caller)
    {
        var level = caller.getLevel();
        var relays = gather(level, caller);
        var leader = relays.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
        if (!leader.equals(caller.getBlockPos()))
            return;

        var flowing = new HashSet<BlockPos>();
        var collectors = new LinkedHashMap<BlockPos, RavenousCollectorTile>();
        for (var relay : relays.values())
        {
            flowing.addAll(relay.flowSources());
            for (var pos : relay.linkedCollectors())
            {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RavenousCollectorTile collector
                        && !collector.storage().isUnattuned() && collector.storage().amount() > 0)
                    collectors.put(pos, collector);
            }
        }

        var byPriority = new ArrayList<>(collectors.values());
        byPriority.sort(Comparator.comparingDouble((RavenousCollectorTile collector) ->
                priority(collector, flowing.contains(collector.getBlockPos()))).reversed());

        var flows = new HashMap<BlockPos, Flow>();
        relays.keySet().forEach(pos -> flows.put(pos, new Flow()));

        for (var collector : byPriority)
        {
            var aspect = collector.storage().type();
            var path = findPath(level, relays, flows, collector.getBlockPos(), aspect);
            if (path == null)
                continue;

            var amount = Math.min(TRANSFER_AMOUNT, Math.min(collector.storage().amount(), path.receiver().spaceFor(aspect)));
            amount = path.receiver().receive(aspect, amount);
            if (amount <= 0)
                continue;

            collector.storage().drain(aspect, amount);
            collector.setChanged();
            collector.sync();

            for (var i = 0; i < path.relays().size(); i++)
            {
                var flow = flows.get(path.relays().get(i).getBlockPos());
                flow.aspect = aspect;
                addOnce(flow.targets, i + 1 < path.relays().size() ? path.relays().get(i + 1).getBlockPos() : path.receiverPos());
                if (i == 0)
                    addOnce(flow.sources, collector.getBlockPos());
            }
        }

        relays.forEach((pos, relay) ->
        {
            var flow = flows.get(pos);
            relay.updateFlow(flow.aspect, flow.sources, flow.targets);
        });
    }

    private static float priority(RavenousCollectorTile collector, boolean flowing)
    {
        var storage = collector.storage();
        return storage.amount() / (float) storage.maximumAmount() + (flowing ? FLOWING_BONUS : 0);
    }

    /**
     * Relays reachable from the caller, keyed by position.
     */
    private static Map<BlockPos, RavenousRelayTile> gather(Level level, RavenousRelayTile caller)
    {
        var relays = new HashMap<BlockPos, RavenousRelayTile>();
        var queue = new ArrayDeque<RavenousRelayTile>();
        relays.put(caller.getBlockPos(), caller);
        queue.add(caller);

        while (!queue.isEmpty())
        {
            for (var pos : queue.poll().linkedRelays())
            {
                if (relays.containsKey(pos) || !level.isLoaded(pos))
                    continue;
                if (level.getBlockEntity(pos) instanceof RavenousRelayTile relay && !relay.isRemoved())
                {
                    relays.put(pos, relay);
                    queue.add(relay);
                }
            }
        }
        return relays;
    }

    /**
     * Shortest path of relays able to carry the aspect, from one linked to the collector to one linked to a storage
     * with room for it.
     */
    @Nullable
    private static Path findPath(Level level, Map<BlockPos, RavenousRelayTile> relays, Map<BlockPos, Flow> flows,
                                 BlockPos collectorPos, EssenceType aspect)
    {
        var parents = new HashMap<BlockPos, BlockPos>();
        var queue = new ArrayDeque<RavenousRelayTile>();
        relays.forEach((pos, relay) ->
        {
            if (relay.linkedCollectors().contains(collectorPos) && canCarry(flows.get(pos), aspect))
            {
                parents.put(pos, pos);
                queue.add(relay);
            }
        });

        while (!queue.isEmpty())
        {
            var relay = queue.poll();
            for (var receiverPos : relay.linkedReceivers())
            {
                if (level.isLoaded(receiverPos) && level.getBlockEntity(receiverPos) instanceof IEssenceReceiver receiver
                        && receiver.spaceFor(aspect) > 0)
                    return new Path(unwind(relays, parents, relay.getBlockPos()), receiverPos, receiver);
            }

            for (var next : relay.linkedRelays())
            {
                if (parents.containsKey(next) || !relays.containsKey(next) || !canCarry(flows.get(next), aspect))
                    continue;
                parents.put(next, relay.getBlockPos());
                queue.add(relays.get(next));
            }
        }
        return null;
    }

    private static boolean canCarry(Flow flow, EssenceType aspect)
    {
        return flow.aspect == EssenceRegistry.EMPTY || flow.aspect == aspect;
    }

    private static List<RavenousRelayTile> unwind(Map<BlockPos, RavenousRelayTile> relays, Map<BlockPos, BlockPos> parents, BlockPos last)
    {
        var path = new ArrayList<RavenousRelayTile>();
        var current = last;
        while (true)
        {
            path.addFirst(relays.get(current));
            var parent = parents.get(current);
            if (parent.equals(current))
                return path;
            current = parent;
        }
    }

    private static void addOnce(List<BlockPos> list, BlockPos pos)
    {
        if (!list.contains(pos))
            list.add(pos);
    }
}
