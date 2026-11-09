package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.energy.IVoidHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One level's void grid between ticks: where energy waits, who asked for what, and what each pylon passed. Pure apart from block positions, so it is unit tested.
 * <ul>
 *     <li><b>Sources</b> offer, and what is accepted waits in transit at the source, up to what its pylons can pass in a tick. So a source runs only while
 *     something draws or a holder has room.</li>
 *     <li><b>Machines</b> draw. What a machine asked last tick is delivered ahead at the start of the next one, the longest-running machines first, so a
 *     steady draw keeps its power when a new machine starts. Anything beyond that is routed when it is asked.</li>
 *     <li><b>Holders</b> inside a field are filled from what is left in transit, and drained when transit isn't enough.</li>
 * </ul>
 * Energy only moves through pylons with cap left this tick, and is never lost on the way. What waits in transit or ahead at a machine is at most a tick's
 * worth, and isn't saved.
 */
public final class VoidGridLedger
{
    public interface Holders
    {
        @Nullable IVoidHolder at(BlockPos pos);
    }

    /**
     * What a pylon passed on average over the last display window.
     *
     * @param arcs per arc touching the pylon, the pylon at its other end and the flow toward it, negative when it flows in
     * @param fed  every machine or holder the pylon handed energy to, with how much per tick
     */
    public record PylonDisplay(long throughput, Map<BlockPos, Long> arcs, Map<BlockPos, Long> fed)
    {
        public static final PylonDisplay IDLE = new PylonDisplay(0, Map.of(), Map.of());
    }

    // Larger than any real amount, small enough that sums never overflow
    private static final long LIMIT = 1L << 40;

    private static final class Consumer
    {
        long asked;
        long lastAsked;
        long since;
        long lastAskTick;
    }

    private GridLayout layout = GridLayout.EMPTY;
    private Map<BlockPos, int[]> holderPylons = Map.of();
    private Holders holders = pos -> null;

    private long   tick;
    private long[] capLeft = new long[0];

    private final Map<BlockPos, Long>     transit   = new HashMap<>();
    private final Map<BlockPos, Long>     ready     = new HashMap<>();
    private final Map<BlockPos, Consumer> consumers = new HashMap<>();
    private final Map<BlockPos, long[]>   holderUse = new HashMap<>();

    private long[]                    throughputSum = new long[0];
    private long[]                    arcSum        = new long[0];
    private List<Map<BlockPos, Long>> fedSum        = List.of();
    private int                       displayTicks;

    /**
     * @param holderPylons every holder inside a field, with the pylons covering it
     */
    public void setLayout(GridLayout layout, Map<BlockPos, int[]> holderPylons, Holders holders)
    {
        boolean same = layout.pylons().equals(this.layout.pylons()) && Arrays.deepEquals(layout.arcs(), this.layout.arcs());
        this.layout = layout;
        this.holderPylons = Map.copyOf(holderPylons);
        this.holders = holders;
        capLeft = layout.caps();
        // The periodic rebuild mostly finds the same layout, and keeps the display window running through it
        if (same)
            return;
        throughputSum = new long[layout.pylons().size()];
        arcSum = new long[layout.arcs().length];
        fedSum = new ArrayList<>();
        for (int i = 0; i < layout.pylons().size(); i++)
            fedSum.add(new HashMap<>());
        displayTicks = 0;
    }

    public GridLayout layout()
    {
        return layout;
    }

    /**
     * Called before anything in the level ticks: caps refill, then last tick's asks are delivered ahead and holders are charged.
     */
    public void startTick(long tick)
    {
        this.tick = tick;
        capLeft = layout.caps();
        holderUse.clear();
        displayTicks++;

        var iterator = consumers.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var consumer = entry.getValue();
            consumer.lastAsked = consumer.asked;
            consumer.asked = 0;
            if (tick - consumer.lastAskTick > VoidGridBalance.READY_EXPIRY_TICKS)
            {
                // Whatever waited for a machine that stopped goes back into the grid from where it stands
                long left = ready.getOrDefault(entry.getKey(), 0L);
                if (left > 0)
                    transit.merge(entry.getKey(), left, Long::sum);
                ready.remove(entry.getKey());
                iterator.remove();
            }
        }
        settle();
    }

    private void settle()
    {
        var waiting = new ArrayList<Map.Entry<BlockPos, Consumer>>();
        for (var entry : consumers.entrySet())
            if (entry.getValue().lastAsked > ready.getOrDefault(entry.getKey(), 0L))
                waiting.add(entry);
        waiting.sort(Comparator.<Map.Entry<BlockPos, Consumer>>comparingLong(entry -> entry.getValue().since)
                .thenComparingLong(entry -> entry.getKey().asLong()));

        var supplies = new Supplies();
        supplies.addTransit();
        supplies.addStored();

        var demands = new ArrayList<GridRouter.Demand>();
        var targets = new ArrayList<BlockPos>();
        for (var entry : waiting)
        {
            int[] pylons = layout.covering(entry.getKey());
            if (pylons.length == 0)
                continue;
            demands.add(new GridRouter.Demand(entry.getValue().lastAsked - ready.getOrDefault(entry.getKey(), 0L), pylons, true));
            targets.add(entry.getKey());
        }
        int machines = demands.size();
        for (var holder : sortedHolders())
        {
            var found = holders.at(holder.getKey());
            long room = found == null ? 0 : found.insert(Math.min(LIMIT, rateLeft(holder.getKey(), found, 1)), true);
            if (room <= 0)
                continue;
            demands.add(new GridRouter.Demand(room, holder.getValue(), false));
            targets.add(holder.getKey());
        }
        if (demands.isEmpty() || supplies.isEmpty())
            return;

        var result = GridRouter.route(capLeft, layout.arcs(), supplies.list, demands);
        supplies.take(result);
        for (int i = 0; i < demands.size(); i++)
        {
            long delivered = result.delivered()[i];
            if (delivered <= 0)
                continue;
            if (i < machines)
                ready.merge(targets.get(i), delivered, Long::sum);
            else
                insert(targets.get(i), delivered);
        }
        record(result, demands, targets);
    }

    /**
     * What could be drawn at pos now: what waits for it, and what could still be routed to it this tick.
     */
    public long available(BlockPos pos)
    {
        long waiting = ready.getOrDefault(pos, 0L);
        int[] pylons = layout.covering(pos);
        if (pylons.length == 0)
            return waiting;

        var supplies = new Supplies();
        supplies.addTransit();
        supplies.addStored();
        if (supplies.isEmpty())
            return waiting;
        var result = GridRouter.route(capLeft.clone(), layout.arcs(), supplies.list, List.of(new GridRouter.Demand(LIMIT, pylons, true)));
        return waiting + result.delivered()[0];
    }

    public long draw(BlockPos pos, long amount)
    {
        if (amount <= 0)
            return 0;

        int[] pylons = layout.covering(pos);
        if (pylons.length > 0)
        {
            var consumer = consumers.computeIfAbsent(pos.immutable(), key -> new Consumer());
            if (consumer.since == 0 || tick - consumer.lastAskTick > VoidGridBalance.STREAK_GRACE_TICKS)
                consumer.since = tick;
            consumer.lastAskTick = tick;
            consumer.asked = Math.min(LIMIT, consumer.asked + amount);
        }

        long waiting = ready.getOrDefault(pos, 0L);
        long got = Math.min(amount, waiting);
        if (got > 0)
        {
            if (waiting == got)
                ready.remove(pos);
            else
                ready.put(pos, waiting - got);
        }
        if (got >= amount || pylons.length == 0)
            return got;

        var supplies = new Supplies();
        supplies.addTransit();
        supplies.addStored();
        if (supplies.isEmpty())
            return got;

        var demand = List.of(new GridRouter.Demand(Math.min(LIMIT, amount - got), pylons, true));
        var result = GridRouter.route(capLeft, layout.arcs(), supplies.list, demand);
        supplies.take(result);
        record(result, demand, List.of(pos));
        return got + result.delivered()[0];
    }

    /**
     * Accepts into transit up to what the source's pylons could pass in a tick, less what still waits there.
     */
    public long offer(BlockPos pos, long amount)
    {
        if (amount <= 0)
            return 0;
        int[] pylons = layout.covering(pos);
        if (pylons.length == 0)
            return 0;

        long capacity = 0;
        for (int pylon : pylons)
            capacity += layout.pylons().get(pylon).cap();
        long waiting = transit.getOrDefault(pos, 0L);
        long accepted = Math.clamp(capacity - waiting, 0, amount);
        if (accepted > 0)
            transit.put(pos.immutable(), waiting + accepted);
        return accepted;
    }

    /**
     * Averages over the ticks since the last call, per pylon of the current layout, then starts a new window.
     */
    public List<PylonDisplay> takeDisplay()
    {
        var displays = new ArrayList<PylonDisplay>();
        int ticks = Math.max(1, displayTicks);
        var pylons = layout.pylons();
        for (int i = 0; i < pylons.size(); i++)
        {
            var arcs = new HashMap<BlockPos, Long>();
            for (int arc : layout.arcsOf(i))
            {
                var ends = layout.arcs()[arc];
                boolean first = ends[0] == i;
                var other = pylons.get(first ? ends[1] : ends[0]).pos();
                arcs.put(other, (first ? arcSum[arc] : -arcSum[arc]) / ticks);
            }
            var fed = new HashMap<BlockPos, Long>();
            fedSum.get(i).forEach((pos, sum) ->
            {
                long average = sum / ticks;
                if (average > 0)
                    fed.put(pos, average);
            });
            displays.add(new PylonDisplay(throughputSum[i] / ticks, arcs, fed));
        }

        Arrays.fill(throughputSum, 0);
        Arrays.fill(arcSum, 0);
        fedSum.forEach(Map::clear);
        displayTicks = 0;
        return displays;
    }

    public long transitAt(BlockPos pos)
    {
        return transit.getOrDefault(pos, 0L);
    }

    public long readyAt(BlockPos pos)
    {
        return ready.getOrDefault(pos, 0L);
    }

    public long capLeft(int pylon)
    {
        return capLeft[pylon];
    }

    public void clear()
    {
        transit.clear();
        ready.clear();
        consumers.clear();
        holderUse.clear();
    }

    private List<Map.Entry<BlockPos, int[]>> sortedHolders()
    {
        var sorted = new ArrayList<>(holderPylons.entrySet());
        sorted.sort(Comparator.comparingLong(entry -> entry.getKey().asLong()));
        return sorted;
    }

    /**
     * @param way 0 to extract, 1 to insert; a holder's rate holds per tick in each direction
     */
    private long rateLeft(BlockPos pos, IVoidHolder holder, int way)
    {
        var use = holderUse.get(pos);
        return Math.max(0, Math.min(LIMIT, holder.maxRate()) - (use == null ? 0 : use[way]));
    }

    private void insert(BlockPos pos, long amount)
    {
        var holder = holders.at(pos);
        if (holder == null)
            return;
        long inserted = holder.insert(amount, false);
        holderUse.computeIfAbsent(pos, key -> new long[2])[1] += inserted;
    }

    private void record(GridRouter.Result result, List<GridRouter.Demand> demands, List<BlockPos> targets)
    {
        for (int i = 0; i < throughputSum.length; i++)
            throughputSum[i] += result.throughput()[i];
        for (int i = 0; i < arcSum.length; i++)
            arcSum[i] += result.arcFlow()[i];
        for (int i = 0; i < demands.size(); i++)
        {
            var pylons = demands.get(i).pylons();
            for (int j = 0; j < pylons.length; j++)
                if (result.fed()[i][j] > 0)
                    fedSum.get(pylons[j]).merge(targets.get(i), result.fed()[i][j], Long::sum);
        }
    }

    /**
     * Transit first, then holders, each with the pylons covering it.
     */
    private final class Supplies
    {
        final List<GridRouter.Supply> list      = new ArrayList<>();
        final List<BlockPos>          positions = new ArrayList<>();

        void addTransit()
        {
            var sorted = new ArrayList<>(transit.entrySet());
            sorted.sort(Comparator.comparingLong(entry -> entry.getKey().asLong()));
            for (var entry : sorted)
            {
                int[] pylons = layout.covering(entry.getKey());
                if (pylons.length == 0 || entry.getValue() <= 0)
                    continue;
                list.add(new GridRouter.Supply(entry.getValue(), pylons, false));
                positions.add(entry.getKey());
            }
        }

        void addStored()
        {
            for (var entry : sortedHolders())
            {
                var holder = holders.at(entry.getKey());
                if (holder == null)
                    continue;
                long stored = holder.extract(Math.min(LIMIT, rateLeft(entry.getKey(), holder, 0)), true);
                if (stored <= 0)
                    continue;
                list.add(new GridRouter.Supply(stored, entry.getValue(), true));
                positions.add(entry.getKey());
            }
        }

        boolean isEmpty()
        {
            return list.isEmpty();
        }

        void take(GridRouter.Result result)
        {
            for (int i = 0; i < list.size(); i++)
            {
                long taken = result.taken()[i];
                if (taken <= 0)
                    continue;
                var pos = positions.get(i);
                if (list.get(i).stored())
                {
                    var holder = holders.at(pos);
                    if (holder != null)
                        holderUse.computeIfAbsent(pos, key -> new long[2])[0] += holder.extract(taken, false);
                }
                else
                {
                    long left = transit.getOrDefault(pos, 0L) - taken;
                    if (left > 0)
                        transit.put(pos, left);
                    else
                        transit.remove(pos);
                }
            }
        }
    }
}
