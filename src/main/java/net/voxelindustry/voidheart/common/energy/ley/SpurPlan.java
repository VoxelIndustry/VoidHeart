package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How standing stones join into spurs. A stone within reach of a line taps it and roots a spur. Every other stone chains to a connected stone within reach,
 * the one fewest hops from a tap and then the nearest, so each spur is a tree leading from its line toward a base. Pure, so it can be tested without a level.
 */
public final class SpurPlan
{
    public static final SpurPlan EMPTY = new SpurPlan(Map.of(), Map.of(), Map.of());

    /**
     * One connected stone.
     *
     * @param parent    the stone it chains to, or null for the stone tapping the line
     * @param pathStart how far along the spur, from the tap, its seam begins
     * @param pathEnd   how far along the spur the stone itself stands
     */
    public record Link(BlockPos stone, BlockPos root, @Nullable BlockPos parent, int hops, double pathStart, double pathEnd)
    {
        public boolean isRoot()
        {
            return parent == null;
        }
    }

    private final Map<BlockPos, Link>    links;
    private final Map<BlockPos, SpurTap> taps;
    private final Map<BlockPos, Double>  lengths;

    private SpurPlan(Map<BlockPos, Link> links, Map<BlockPos, SpurTap> taps, Map<BlockPos, Double> lengths)
    {
        this.links = links;
        this.taps = taps;
        this.lengths = lengths;
    }

    /**
     * @param stones every stone, with the tap it would make if it stands within reach of a line
     */
    public static SpurPlan of(Map<BlockPos, Optional<SpurTap>> stones, int reach)
    {
        var links = new HashMap<BlockPos, Link>();
        var taps = new HashMap<BlockPos, SpurTap>();
        var lengths = new HashMap<BlockPos, Double>();

        var frontier = new ArrayList<Link>();
        for (var stone : sorted(stones.keySet()))
        {
            var tap = stones.get(stone);
            if (tap == null || tap.isEmpty())
                continue;
            var link = new Link(stone, stone, null, 0, 0, tap.get().distanceTo(stone));
            links.put(stone, link);
            taps.put(stone, tap.get());
            frontier.add(link);
        }

        var waiting = new ArrayList<>(sorted(stones.keySet()));
        waiting.removeIf(links::containsKey);
        double reachSq = (double) reach * reach;
        while (!frontier.isEmpty() && !waiting.isEmpty())
        {
            var next = new ArrayList<Link>();
            for (var iterator = waiting.iterator(); iterator.hasNext(); )
            {
                var stone = iterator.next();
                Link best = null;
                double bestSq = Double.MAX_VALUE;
                for (var candidate : frontier)
                {
                    double distanceSq = horizontalSq(stone, candidate.stone());
                    if (distanceSq <= reachSq && distanceSq < bestSq)
                    {
                        best = candidate;
                        bestSq = distanceSq;
                    }
                }
                if (best == null)
                    continue;

                var link = new Link(stone, best.root(), best.stone(), best.hops() + 1, best.pathEnd(), best.pathEnd() + Math.sqrt(bestSq));
                links.put(stone, link);
                next.add(link);
                iterator.remove();
            }
            frontier = next;
        }

        for (var link : links.values())
            lengths.merge(link.root(), link.pathEnd(), Math::max);
        return new SpurPlan(Collections.unmodifiableMap(links), Collections.unmodifiableMap(taps), Collections.unmodifiableMap(lengths));
    }

    private static List<BlockPos> sorted(Collection<BlockPos> stones)
    {
        var list = new ArrayList<>(stones);
        list.sort(Comparator.naturalOrder());
        return list;
    }

    static double horizontalSq(BlockPos a, BlockPos b)
    {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public @Nullable Link link(BlockPos stone)
    {
        return links.get(stone);
    }

    public boolean isConnected(BlockPos stone)
    {
        return links.containsKey(stone);
    }

    public Collection<Link> links()
    {
        return links.values();
    }

    public Set<BlockPos> roots()
    {
        return taps.keySet();
    }

    public @Nullable SpurTap tap(BlockPos root)
    {
        return taps.get(root);
    }

    /**
     * How far the spur's farthest stone stands from the tap, along the spur.
     */
    public double length(BlockPos root)
    {
        return lengths.getOrDefault(root, 0D);
    }
}
