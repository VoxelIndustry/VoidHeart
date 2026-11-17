package net.voxelindustry.voidheart.common.content.sin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which blocks a Ravenous tool's bite eats, in rings eaten one per tick, nearest first: along an ore vein, the blocks of the same kind connected to the
 * broken one, and anywhere else a square on the face hit. Both sides plan with it, so the preview shows what the bite takes.
 */
public final class BitePlan
{
    /**
     * The eighteen neighbours sharing a face or an edge.
     */
    private static final List<BlockPos> NEIGHBOURS = neighbours();

    private BitePlan()
    {
    }

    private static List<BlockPos> neighbours()
    {
        var list = new ArrayList<BlockPos>();
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                for (int z = -1; z <= 1; z++)
                {
                    var touching = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (touching == 1 || touching == 2)
                        list.add(new BlockPos(x, y, z));
                }
            }
        }
        return List.copyOf(list);
    }

    /**
     * @param origin  the block the player broke, never part of the bite
     * @param edible  whether a position holds a block the bite may eat
     * @param count   how many blocks the bite eats at most
     * @param reach   how far from the origin, on any axis, the bite may go
     * @return rings of positions, the first touching the origin, each sorted nearest first
     */
    public static List<List<BlockPos>> vein(BlockPos origin, Predicate<BlockPos> edible, int count, int reach)
    {
        var rings = new ArrayList<List<BlockPos>>();
        if (count <= 0)
            return rings;

        var seen = new HashSet<BlockPos>();
        seen.add(origin);
        var frontier = new ArrayDeque<BlockPos>();
        frontier.add(origin);
        var planned = 0;

        while (!frontier.isEmpty() && planned < count)
        {
            var ring = new ArrayList<BlockPos>();
            for (var from : frontier)
            {
                for (var offset : NEIGHBOURS)
                {
                    var pos = from.offset(offset);
                    if (!seen.add(pos) || !within(origin, pos, reach) || !edible.test(pos))
                        continue;
                    ring.add(pos);
                }
            }
            if (ring.isEmpty())
                break;

            ring.sort(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(origin)).thenComparingLong(BlockPos::asLong));
            if (ring.size() > count - planned)
                ring = new ArrayList<>(ring.subList(0, count - planned));
            planned += ring.size();
            rings.add(List.copyOf(ring));

            frontier.clear();
            frontier.addAll(ring);
        }
        return rings;
    }

    /**
     * A square on the face the player hit, centred on the broken block and going straight into it, so walls and floors come out flat.
     *
     * @param face    the face hit, pointing back at the player
     * @param radius  how far the square reaches each side of the broken block
     * @param depth   how many layers deep it goes, the broken block's layer first
     * @param lowestY the lowest y the bite may take, so a side swing never digs into the floor
     * @return rings of positions, the n-th holding the blocks n steps from the origin on the farthest axis
     */
    public static List<List<BlockPos>> square(BlockPos origin, Direction face, int radius, int depth, int lowestY, Predicate<BlockPos> edible)
    {
        var into = face.getOpposite();
        var axes = new ArrayList<Direction>();
        for (var direction : Direction.values())
        {
            if (direction.getAxis() != face.getAxis() && direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                axes.add(direction);
        }
        var first = axes.get(0);
        var second = axes.get(1);

        var rings = new ArrayList<List<BlockPos>>();
        for (int ring = 0; ring <= Math.max(radius, depth - 1); ring++)
            rings.add(new ArrayList<>());

        for (int layer = 0; layer < depth; layer++)
        {
            for (int u = -radius; u <= radius; u++)
            {
                for (int v = -radius; v <= radius; v++)
                {
                    if (layer == 0 && u == 0 && v == 0)
                        continue;
                    var pos = origin.relative(into, layer).relative(first, u).relative(second, v);
                    if (pos.getY() < lowestY || !edible.test(pos))
                        continue;
                    rings.get(Math.max(layer, Math.max(Math.abs(u), Math.abs(v)))).add(pos);
                }
            }
        }

        rings.removeIf(List::isEmpty);
        var sorted = new ArrayList<List<BlockPos>>();
        for (var ring : rings)
        {
            ring.sort(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(origin)).thenComparingLong(BlockPos::asLong));
            sorted.add(List.copyOf(ring));
        }
        return sorted;
    }

    private static boolean within(BlockPos origin, BlockPos pos, int reach)
    {
        return Math.abs(pos.getX() - origin.getX()) <= reach && Math.abs(pos.getY() - origin.getY()) <= reach && Math.abs(pos.getZ() - origin.getZ()) <= reach;
    }
}
