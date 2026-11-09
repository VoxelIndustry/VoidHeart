package net.voxelindustry.voidheart.common.energy.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Routes void energy over pylons with no loss, each pylon passing no more than what is left of its cap this tick. Pure, so it is unit tested.
 * <p>
 * Pylons are numbered from 0, and an arc joins two of them both ways. Every supply and demand sits inside the fields of one or more pylons.
 * <ul>
 *     <li>Demands are served in the order given, each as fully as the caps allow. A later demand may move an earlier one onto another path, but the
 *     earlier one always keeps all it got.</li>
 *     <li>A demand takes energy in transit from sources before stored energy from holders.</li>
 *     <li>Holders being charged come after every machine and only take energy in transit, so holders never charge each other.</li>
 * </ul>
 */
public final class GridRouter
{
    /**
     * @param stored true for a holder's stored energy, false for a source's energy in transit
     */
    public record Supply(long amount, int[] pylons, boolean stored)
    {
    }

    /**
     * @param fromStored true for a machine, which stored energy may serve, false for a holder being charged
     */
    public record Demand(long amount, int[] pylons, boolean fromStored)
    {
    }

    /**
     * @param taken      per supply
     * @param delivered  per demand
     * @param throughput per pylon
     * @param arcFlow    per arc, positive from its first pylon to its second
     * @param fed        per demand, what each of its pylons handed it, in the order of its pylons
     */
    public record Result(long[] taken, long[] delivered, long[] throughput, long[] arcFlow, long[][] fed)
    {
        public long totalDelivered()
        {
            return Arrays.stream(delivered).sum();
        }

        public long totalTaken()
        {
            return Arrays.stream(taken).sum();
        }
    }

    private static final long INFINITE = Long.MAX_VALUE / 4;

    private GridRouter()
    {
    }

    /**
     * @param capLeft what each pylon can still pass this tick, lowered by what this routing passes through it
     * @param arcs    pairs of pylon numbers
     */
    public static Result route(long[] capLeft, int[][] arcs, List<Supply> supplies, List<Demand> demands)
    {
        var result = new Result(new long[supplies.size()], new long[demands.size()], new long[capLeft.length], new long[arcs.length],
                new long[demands.size()][]);
        for (int i = 0; i < demands.size(); i++)
            result.fed()[i] = new long[demands.get(i).pylons().length];

        var machines = new ArrayList<Integer>();
        var charging = new ArrayList<Integer>();
        for (int i = 0; i < demands.size(); i++)
            (demands.get(i).fromStored() ? machines : charging).add(i);

        var supplyLeft = new long[supplies.size()];
        for (int i = 0; i < supplies.size(); i++)
            supplyLeft[i] = Math.max(0, supplies.get(i).amount());

        pass(capLeft, arcs, supplies, supplyLeft, true, demands, machines, result);
        if (!charging.isEmpty())
            pass(capLeft, arcs, supplies, supplyLeft, false, demands, charging, result);
        return result;
    }

    /**
     * One max flow, demand after demand, over a single residual graph so later demands can reroute earlier ones.
     */
    private static void pass(long[] capLeft, int[][] arcs, List<Supply> supplies, long[] supplyLeft, boolean useStored,
                             List<Demand> demands, List<Integer> order, Result result)
    {
        int pylons = capLeft.length;
        int nodes = 2 + 2 * pylons + supplies.size() + order.size();
        var graph = new Graph(nodes);
        int source = 0;
        int sink = 1;

        var pylonEdges = new int[pylons];
        for (int i = 0; i < pylons; i++)
            pylonEdges[i] = graph.add(in(i), out(i), Math.max(0, capLeft[i]));

        var arcEdges = new int[arcs.length][];
        for (int i = 0; i < arcs.length; i++)
        {
            int a = arcs[i][0];
            int b = arcs[i][1];
            if (a == b || !valid(a, pylons) || !valid(b, pylons))
                continue;
            arcEdges[i] = new int[] { graph.add(out(a), in(b), INFINITE), graph.add(out(b), in(a), INFINITE) };
        }

        var supplyEdges = new int[supplies.size()];
        for (int i = 0; i < supplies.size(); i++)
        {
            var supply = supplies.get(i);
            int node = 2 + 2 * pylons + i;
            boolean usable = useStored || !supply.stored();
            supplyEdges[i] = graph.add(source, node, usable && !supply.stored() ? supplyLeft[i] : 0);
            for (int pylon : supply.pylons())
                if (valid(pylon, pylons))
                    graph.add(node, in(pylon), INFINITE);
        }

        var demandEdges = new int[order.size()];
        var fedEdges = new int[order.size()][];
        for (int i = 0; i < order.size(); i++)
        {
            var demand = demands.get(order.get(i));
            int node = 2 + 2 * pylons + supplies.size() + i;
            fedEdges[i] = new int[demand.pylons().length];
            for (int j = 0; j < demand.pylons().length; j++)
            {
                int pylon = demand.pylons()[j];
                fedEdges[i][j] = valid(pylon, pylons) ? graph.add(out(pylon), node, INFINITE) : -1;
            }
            demandEdges[i] = graph.add(node, sink, 0);
        }

        for (int i = 0; i < order.size(); i++)
        {
            var demand = demands.get(order.get(i));
            if (demand.amount() <= 0)
                continue;

            graph.setCapacity(demandEdges[i], demand.amount());
            graph.maxFlow(source, sink);
            if (useStored)
            {
                for (int s = 0; s < supplies.size(); s++)
                    if (supplies.get(s).stored())
                        graph.setCapacity(supplyEdges[s], supplyLeft[s]);
                graph.maxFlow(source, sink);
                for (int s = 0; s < supplies.size(); s++)
                    if (supplies.get(s).stored())
                        graph.setCapacity(supplyEdges[s], graph.flow(supplyEdges[s]));
            }
            graph.setCapacity(demandEdges[i], graph.flow(demandEdges[i]));
        }

        for (int i = 0; i < supplies.size(); i++)
        {
            long taken = graph.flow(supplyEdges[i]);
            supplyLeft[i] -= taken;
            result.taken()[i] += taken;
        }
        for (int i = 0; i < order.size(); i++)
        {
            int demand = order.get(i);
            result.delivered()[demand] += graph.flow(demandEdges[i]);
            for (int j = 0; j < fedEdges[i].length; j++)
                if (fedEdges[i][j] >= 0)
                    result.fed()[demand][j] += graph.flow(fedEdges[i][j]);
        }
        for (int i = 0; i < pylons; i++)
        {
            long passed = graph.flow(pylonEdges[i]);
            capLeft[i] -= passed;
            result.throughput()[i] += passed;
        }
        for (int i = 0; i < arcs.length; i++)
            if (arcEdges[i] != null)
                result.arcFlow()[i] += graph.flow(arcEdges[i][0]) - graph.flow(arcEdges[i][1]);
    }

    private static boolean valid(int pylon, int pylons)
    {
        return pylon >= 0 && pylon < pylons;
    }

    private static int in(int pylon)
    {
        return 2 + 2 * pylon;
    }

    private static int out(int pylon)
    {
        return 3 + 2 * pylon;
    }

    /**
     * Residual graph with paired edges, the reverse of edge e being e ^ 1. Edmonds-Karp, so paths are shortest first and the result is deterministic.
     */
    private static final class Graph
    {
        private final int[] head;
        private int[]  next     = new int[64];
        private int[]  target   = new int[64];
        private long[] capacity = new long[64];
        private long[] residual = new long[64];
        private int    edges;

        private final int[]     previous;
        private final int[]     queue;

        Graph(int nodes)
        {
            head = new int[nodes];
            Arrays.fill(head, -1);
            previous = new int[nodes];
            queue = new int[nodes];
        }

        int add(int from, int to, long cap)
        {
            if (edges + 2 > next.length)
            {
                int size = next.length * 2;
                next = Arrays.copyOf(next, size);
                target = Arrays.copyOf(target, size);
                capacity = Arrays.copyOf(capacity, size);
                residual = Arrays.copyOf(residual, size);
            }
            int edge = edges;
            link(edge, from, to, cap);
            link(edge + 1, to, from, 0);
            edges += 2;
            return edge;
        }

        private void link(int edge, int from, int to, long cap)
        {
            target[edge] = to;
            capacity[edge] = cap;
            residual[edge] = cap;
            next[edge] = head[from];
            head[from] = edge;
        }

        long flow(int edge)
        {
            return capacity[edge] - residual[edge];
        }

        /**
         * Never below the flow already through the edge.
         */
        void setCapacity(int edge, long cap)
        {
            long value = Math.max(cap, flow(edge));
            residual[edge] += value - capacity[edge];
            capacity[edge] = value;
        }

        void maxFlow(int source, int sink)
        {
            while (true)
            {
                Arrays.fill(previous, -1);
                int read = 0;
                int write = 0;
                queue[write++] = source;
                boolean found = false;
                while (read < write && !found)
                {
                    int node = queue[read++];
                    for (int edge = head[node]; edge != -1; edge = next[edge])
                    {
                        int to = target[edge];
                        if (residual[edge] <= 0 || to == source || previous[to] != -1)
                            continue;
                        previous[to] = edge;
                        if (to == sink)
                        {
                            found = true;
                            break;
                        }
                        queue[write++] = to;
                    }
                }
                if (!found)
                    return;

                long bottleneck = INFINITE;
                for (int node = sink; node != source; node = target[previous[node] ^ 1])
                    bottleneck = Math.min(bottleneck, residual[previous[node]]);
                for (int node = sink; node != source; node = target[previous[node] ^ 1])
                {
                    residual[previous[node]] -= bottleneck;
                    residual[previous[node] ^ 1] += bottleneck;
                }
            }
        }
    }
}
