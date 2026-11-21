package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * The pylons of a level that hold a bundle, and the arcs between them. Pure: line of sight comes in as a predicate.
 */
public final class GridLayout
{
    /**
     * @param bundle the azurium bundle's block, the center of the field and the end of every arc
     * @param clear  whether the bundle grew Clear, so its arcs to other Clear bundles pass through blocks
     * @param scoured whether the bundle was scoured, so its field holds back the seep
     */
    public record Pylon(BlockPos pos, BlockPos bundle, int radius, long cap, boolean clear, boolean scoured)
    {
        public Pylon(BlockPos pos, BlockPos bundle, int radius, long cap)
        {
            this(pos, bundle, radius, cap, false, false);
        }

        public Pylon(BlockPos pos, BlockPos bundle, int radius, long cap, boolean clear)
        {
            this(pos, bundle, radius, cap, clear, false);
        }

        public boolean covers(BlockPos target)
        {
            return bundle.distSqr(target) <= (double) radius * radius;
        }
    }

    public static final GridLayout EMPTY = new GridLayout(List.of(), new int[0][]);

    private final List<Pylon>         pylons;
    private final int[][]             arcs;
    private final Map<BlockPos, Integer> indexByPos = new HashMap<>();
    private final List<List<Integer>> arcsByPylon   = new ArrayList<>();

    public GridLayout(List<Pylon> pylons, int[][] arcs)
    {
        this.pylons = List.copyOf(pylons);
        this.arcs = arcs;
        for (int i = 0; i < pylons.size(); i++)
        {
            indexByPos.put(pylons.get(i).pos(), i);
            arcsByPylon.add(new ArrayList<>());
        }
        for (int i = 0; i < arcs.length; i++)
        {
            arcsByPylon.get(arcs[i][0]).add(i);
            arcsByPylon.get(arcs[i][1]).add(i);
        }
    }

    /**
     * Two pylons arc when their bundles are within {@link VoidGridBalance#linkReach} of each other and in sight. Two Clear bundles need no sight.
     */
    public static GridLayout link(List<Pylon> pylons, BiPredicate<Pylon, Pylon> inSight)
    {
        var arcs = new ArrayList<int[]>();
        for (int a = 0; a < pylons.size(); a++)
        {
            for (int b = a + 1; b < pylons.size(); b++)
            {
                var first = pylons.get(a);
                var second = pylons.get(b);
                int reach = VoidGridBalance.linkReach(first.radius(), second.radius());
                if (first.bundle().distSqr(second.bundle()) <= (double) reach * reach && ((first.clear() && second.clear()) || inSight.test(first, second)))
                    arcs.add(new int[] { a, b });
            }
        }
        return new GridLayout(pylons, arcs.toArray(new int[0][]));
    }

    public List<Pylon> pylons()
    {
        return pylons;
    }

    public int[][] arcs()
    {
        return arcs;
    }

    public int indexOf(BlockPos pylon)
    {
        return indexByPos.getOrDefault(pylon, -1);
    }

    /**
     * Arcs touching a pylon, as indices into {@link #arcs()}.
     */
    public List<Integer> arcsOf(int pylon)
    {
        return arcsByPylon.get(pylon);
    }

    /**
     * Every pylon whose field covers the position, in ascending order.
     */
    public int[] covering(BlockPos pos)
    {
        int count = 0;
        var found = new int[pylons.size()];
        for (int i = 0; i < pylons.size(); i++)
            if (pylons.get(i).covers(pos))
                found[count++] = i;
        return count == found.length ? found : Arrays.copyOf(found, count);
    }

    public long[] caps()
    {
        var caps = new long[pylons.size()];
        for (int i = 0; i < caps.length; i++)
            caps[i] = pylons.get(i).cap();
        return caps;
    }
}
