package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Routes essence across the relays linked together, a route's worth from each source every route. The sources move in turn, fullest first, a
 * store's stock counting as much as a collector's. Each shares its route among every machine it reaches with room; a collector then sends what it
 * has left down the shortest path of relays to a store with room. So a full store feeds machines before a collector that has less, and a nearly full
 * collector is drained before it stops collecting.
 * <p>
 * A relay carries a single aspect at a time, claimed by the first essence that flows through it each route, so a fuller source of another aspect
 * takes the shared relays it uses and the others wait for a free path. Nothing claims a relay without moving essence through it.
 * <p>
 * Collectors a Shatter Forge needs more essence from than they hold are served before any other: the forge draws the
 * shortfall from the storages behind them, and their relays show it flowing back toward the collector.
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

    /**
     * Essence of one aspect a collector or a store can send this route.
     *
     * @param linked whether a relay links the source
     */
    private record Source(BlockPos pos, EssenceType aspect, float priority, @Nullable RavenousCollectorTile collector, @Nullable IEssenceReceiver store,
                          Predicate<RavenousRelayTile> linked)
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
        var fed = new LinkedHashMap<BlockPos, RavenousCollectorTile>();
        for (var relay : relays.values())
        {
            flowing.addAll(relay.flowSources());
            for (var pos : relay.linkedCollectors())
            {
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RavenousCollectorTile collector) || collector.storage().isUnattuned())
                    continue;
                if (collector.isFed())
                    fed.put(pos, collector);
                else if (collector.storage().amount() > 0)
                    collectors.put(pos, collector);
            }
        }

        var flows = new HashMap<BlockPos, Flow>();
        relays.keySet().forEach(pos -> flows.put(pos, new Flow()));
        BiPredicate<BlockPos, EssenceType> flowCarries = (pos, aspect) -> canCarry(flows.get(pos), aspect);

        // Only shown, the forge moves the essence itself once its craft completes
        for (var collector : fed.values())
        {
            var aspect = collector.storage().type();
            var path = findPath(level, relays, flowCarries, collector.getBlockPos(), aspect, receiver -> receiver.amountOf(aspect) > 0);
            if (path == null)
                continue;

            for (var i = 0; i < path.relays().size(); i++)
            {
                var flow = flows.get(path.relays().get(i).getBlockPos());
                flow.aspect = aspect;
                addOnce(flow.targets, i == 0 ? collector.getBlockPos() : path.relays().get(i - 1).getBlockPos());
                if (i == path.relays().size() - 1)
                    addOnce(flow.sources, path.receiverPos());
            }
        }

        // Fullest first, a store's stock as much as a collector's, and a relay is claimed only by essence that flows through it
        for (var source : sources(level, relays, collectors.values(), flowing))
        {
            var aspect = source.aspect();
            if (source.store() instanceof IEssenceReceiver store)
            {
                feedMachines(level, relays, flows, flowCarries, source, Math.min(TRANSFER_AMOUNT, store.amountOf(aspect)), amount -> store.extract(aspect, amount));
                continue;
            }
            if (!(source.collector() instanceof RavenousCollectorTile collector))
                continue;

            // A collector feeds machines, and fills a store with whatever of its route it has left
            var collectorPos = source.pos();
            var budget = Math.min(TRANSFER_AMOUNT, collector.storage().amount());
            var sent = feedMachines(level, relays, flows, flowCarries, source, budget, amount -> collector.storage().drain(aspect, amount));

            var left = budget - sent;
            var path = left > 0 ? findPath(level, relays, flowCarries, collectorPos, aspect, receiver -> receiver.isStore() && receiver.spaceFor(aspect) > 0) : null;
            if (path != null)
            {
                var amount = path.receiver().receive(aspect, Math.min(left, path.receiver().spaceFor(aspect)));
                if (amount > 0)
                {
                    collector.storage().drain(aspect, amount);
                    sent += amount;
                    showFlow(flows, path, collectorPos, aspect);
                }
            }

            if (sent > 0)
            {
                collector.setChanged();
                collector.sync();
            }
        }

        relays.forEach((pos, relay) ->
        {
            var flow = flows.get(pos);
            relay.updateFlow(flow.aspect, flow.sources, flow.targets);
        });
    }

    /**
     * Shares up to a route's worth of one aspect from a source among every machine it reaches with room for it, through relays free or already
     * carrying that aspect, and shows it flowing to each.
     *
     * @param take draws an amount out of the source
     * @return amount sent
     */
    private static int feedMachines(Level level, Map<BlockPos, RavenousRelayTile> relays, Map<BlockPos, Flow> flows,
                                    BiPredicate<BlockPos, EssenceType> carries, Source source, int budget, IntUnaryOperator take)
    {
        if (budget <= 0)
            return 0;
        var aspect = source.aspect();
        var sourcePos = source.pos();
        var paths = machinePaths(level, relays, carries, sourcePos, source.linked(), aspect);
        if (paths.isEmpty())
            return 0;

        var spaces = new int[paths.size()];
        for (var i = 0; i < spaces.length; i++)
            spaces[i] = paths.get(i).receiver().spaceFor(aspect);
        var shares = RelayShares.split(budget, spaces);

        var sent = 0;
        for (var i = 0; i < shares.length; i++)
        {
            if (shares[i] <= 0)
                continue;
            var path = paths.get(i);
            var received = path.receiver().receive(aspect, shares[i]);
            if (received <= 0)
                continue;
            take.applyAsInt(received);
            sent += received;
            showFlow(flows, path, sourcePos, aspect);
        }
        return sent;
    }

    /**
     * Paths to every machine a source reaches with room for the aspect, nearest first, each machine once.
     */
    private static List<Path> machinePaths(Level level, Map<BlockPos, RavenousRelayTile> relays, BiPredicate<BlockPos, EssenceType> carries,
                                           BlockPos sourcePos, Predicate<RavenousRelayTile> linkedToSource, EssenceType aspect)
    {
        var paths = new ArrayList<Path>();
        var found = new HashSet<BlockPos>();
        var parents = new HashMap<BlockPos, BlockPos>();
        var queue = new ArrayDeque<RavenousRelayTile>();
        relays.entrySet().stream()
                .filter(entry -> linkedToSource.test(entry.getValue()) && carries.test(entry.getKey(), aspect))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry ->
                {
                    parents.put(entry.getKey(), entry.getKey());
                    queue.add(entry.getValue());
                });

        while (!queue.isEmpty())
        {
            var relay = queue.poll();
            for (var receiverPos : relay.linkedReceivers())
            {
                if (receiverPos.equals(sourcePos) || found.contains(receiverPos) || !level.isLoaded(receiverPos)
                        || !(level.getBlockEntity(receiverPos) instanceof IEssenceReceiver receiver) || receiver.isStore() || receiver.spaceFor(aspect) <= 0)
                    continue;
                found.add(receiverPos);
                paths.add(new Path(unwind(relays, parents, relay.getBlockPos()), receiverPos, receiver));
            }

            for (var next : relay.linkedRelays())
            {
                if (parents.containsKey(next) || !relays.containsKey(next) || !carries.test(next, aspect))
                    continue;
                parents.put(next, relay.getBlockPos());
                queue.add(relays.get(next));
            }
        }
        return paths;
    }

    /**
     * Every collector with essence to send and every store's stock of each aspect, fullest first. A source already flowing keeps a bonus, so two of
     * different aspects near the same fill don't take turns every route.
     */
    private static List<Source> sources(Level level, Map<BlockPos, RavenousRelayTile> relays, Iterable<RavenousCollectorTile> collectors,
                                        Set<BlockPos> flowing)
    {
        var sources = new ArrayList<Source>();
        for (var collector : collectors)
        {
            var pos = collector.getBlockPos();
            sources.add(new Source(pos, collector.storage().type(), priority(collector, flowing.contains(pos)), collector, null,
                    relay -> relay.linkedCollectors().contains(pos)));
        }
        for (var entry : stores(level, relays).entrySet())
        {
            var pos = entry.getKey();
            var store = entry.getValue();
            for (var aspect : (Iterable<EssenceType>) EssenceRegistry.ESSENCE_REGISTRY.stream()::iterator)
            {
                var amount = store.amountOf(aspect);
                if (aspect == EssenceRegistry.EMPTY || amount <= 0)
                    continue;
                var fill = amount / (float) (amount + Math.max(0, store.spaceFor(aspect)));
                sources.add(new Source(pos, aspect, fill + (flowing.contains(pos) ? FLOWING_BONUS : 0), null, store,
                        relay -> relay.linkedReceivers().contains(pos)));
            }
        }
        sources.sort(Comparator.comparingDouble(Source::priority).reversed());
        return sources;
    }

    /**
     * Stores linked to any relay of the network, in a stable order.
     */
    private static Map<BlockPos, IEssenceReceiver> stores(Level level, Map<BlockPos, RavenousRelayTile> relays)
    {
        var stores = new TreeMap<BlockPos, IEssenceReceiver>();
        for (var relay : relays.values())
        {
            for (var pos : relay.linkedReceivers())
            {
                if (!stores.containsKey(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof IEssenceReceiver receiver && receiver.isStore())
                    stores.put(pos, receiver);
            }
        }
        return stores;
    }

    /**
     * Shows essence flowing from a source down a path of relays into its receiver.
     */
    private static void showFlow(Map<BlockPos, Flow> flows, Path path, BlockPos sourcePos, EssenceType aspect)
    {
        for (var i = 0; i < path.relays().size(); i++)
        {
            var flow = flows.get(path.relays().get(i).getBlockPos());
            flow.aspect = aspect;
            addOnce(flow.targets, i + 1 < path.relays().size() ? path.relays().get(i + 1).getBlockPos() : path.receiverPos());
            if (i == 0)
                addOnce(flow.sources, sourcePos);
        }
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
     * Shortest path of relays able to carry the aspect, from one linked to the collector to one linked to a matching
     * storage.
     */
    @Nullable
    private static Path findPath(Level level, Map<BlockPos, RavenousRelayTile> relays, BiPredicate<BlockPos, EssenceType> carries,
                                 BlockPos collectorPos, EssenceType aspect, Predicate<IEssenceReceiver> matches)
    {
        var parents = new HashMap<BlockPos, BlockPos>();
        var queue = new ArrayDeque<RavenousRelayTile>();
        relays.forEach((pos, relay) ->
        {
            if (relay.linkedCollectors().contains(collectorPos) && carries.test(pos, aspect))
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
                        && matches.test(receiver))
                    return new Path(unwind(relays, parents, relay.getBlockPos()), receiverPos, receiver);
            }

            for (var next : relay.linkedRelays())
            {
                if (parents.containsKey(next) || !relays.containsKey(next) || !carries.test(next, aspect))
                    continue;
                parents.put(next, relay.getBlockPos());
                queue.add(relays.get(next));
            }
        }
        return null;
    }

    /**
     * Storages holding the aspect, reachable through the relays linked to a collector. Nearer storages come first.
     */
    public static Map<BlockPos, IEssenceReceiver> suppliers(Level level, BlockPos collectorPos, EssenceType aspect)
    {
        return suppliersFrom(level, collectorPos, relay -> relay.linkedCollectors().contains(collectorPos), aspect);
    }

    /**
     * Storages holding the aspect, reachable through the relays linked to a receiver, the receiver itself left out. Nearer storages come first.
     */
    public static Map<BlockPos, IEssenceReceiver> suppliersOfReceiver(Level level, BlockPos receiverPos, EssenceType aspect)
    {
        var suppliers = suppliersFrom(level, receiverPos, relay -> relay.linkedReceivers().contains(receiverPos), aspect);
        suppliers.remove(receiverPos);
        return suppliers;
    }

    private static Map<BlockPos, IEssenceReceiver> suppliersFrom(Level level, BlockPos origin, Predicate<RavenousRelayTile> linkedToOrigin, EssenceType aspect)
    {
        var suppliers = new LinkedHashMap<BlockPos, IEssenceReceiver>();
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<RavenousRelayTile>();
        forEachTileInRange(level, origin, RavenousRelayTile.RANGE, tile ->
        {
            if (tile instanceof RavenousRelayTile relay && linkedToOrigin.test(relay) && visited.add(relay.getBlockPos()))
                queue.add(relay);
        });

        while (!queue.isEmpty())
        {
            var relay = queue.poll();
            for (var pos : relay.linkedReceivers())
            {
                if (!suppliers.containsKey(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof IEssenceReceiver receiver
                        && receiver.amountOf(aspect) > 0)
                    suppliers.put(pos, receiver);
            }
            for (var pos : relay.linkedRelays())
            {
                if (visited.add(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RavenousRelayTile next)
                    queue.add(next);
            }
        }
        return suppliers;
    }

    /**
     * Visits the block entities of loaded chunks within range of a block center.
     */
    public static void forEachTileInRange(Level level, BlockPos origin, int range, Consumer<BlockEntity> action)
    {
        var center = Vec3.atCenterOf(origin);
        for (var chunkX = (origin.getX() - range) >> 4; chunkX <= (origin.getX() + range) >> 4; chunkX++)
        {
            for (var chunkZ = (origin.getZ() - range) >> 4; chunkZ <= (origin.getZ() + range) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;

                for (var tile : chunk.getBlockEntities().values())
                {
                    if (!tile.isRemoved() && Vec3.atCenterOf(tile.getBlockPos()).distanceTo(center) <= range)
                        action.accept(tile);
                }
            }
        }
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
