package net.voxelindustry.voidheart.common.content.kleinstar;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A way for a Klein Star around what stands between it and where it means to burst. The shortest way through open blocks is searched, in a box a
 * few blocks wider than the two ends, then pulled taut: every point that can be skipped in a straight line is dropped, so what is left is only the
 * corners it has to turn. {@link KleinStarPath} rounds those into curves.
 * <p>
 * What the search remembers grows with the blocks it looks at, never with the size of the box, so two ends far apart cost no more than two close
 * ones: it gives up after as many blocks either way.
 */
public final class KleinStarRoute
{
    // How far past its two ends the search may wander, and how many blocks it may look at before it gives up
    private static final int  MARGIN      = 7;
    private static final int  MAX_VISITED = 12_000;
    private static final long NOWHERE     = Long.MIN_VALUE;

    /**
     * What a star can fly through.
     */
    public interface Space
    {
        boolean open(int x, int y, int z);
    }

    /**
     * Whether a star can go straight from one point to another.
     */
    public interface Sight
    {
        boolean clear(Vec3 from, Vec3 to);
    }

    private record Node(long cell, double reach) implements Comparable<Node>
    {
        @Override
        public int compareTo(Node other)
        {
            return Double.compare(reach, other.reach);
        }
    }

    private KleinStarRoute()
    {
    }

    /**
     * @return the corners to turn on the way, from the start to the end included and so never fewer than two points, or null when there is no way
     * within reach
     */
    public static @Nullable List<Vec3> find(Space space, Sight sight, Vec3 from, Vec3 to)
    {
        int startX = floor(from.x), startY = floor(from.y), startZ = floor(from.z);
        int goalX = floor(to.x), goalY = floor(to.y), goalZ = floor(to.z);
        // Both ends in one block: there is nothing to go around, so either the straight line between them is open or nothing is
        if (startX == goalX && startY == goalY && startZ == goalZ)
            return sight.clear(from, to) ? List.of(from, to) : null;

        int minX = Math.min(startX, goalX) - MARGIN, minY = Math.min(startY, goalY) - MARGIN, minZ = Math.min(startZ, goalZ) - MARGIN;
        int maxX = Math.max(startX, goalX) + MARGIN, maxY = Math.max(startY, goalY) + MARGIN, maxZ = Math.max(startZ, goalZ) + MARGIN;
        long start = BlockPos.asLong(startX, startY, startZ);
        long goal = BlockPos.asLong(goalX, goalY, goalZ);

        var cost = new Long2DoubleOpenHashMap();
        cost.defaultReturnValue(Double.MAX_VALUE);
        var cameFrom = new Long2LongOpenHashMap();
        cameFrom.defaultReturnValue(NOWHERE);
        cost.put(start, 0);

        var frontier = new PriorityQueue<Node>();
        frontier.add(new Node(start, 0));
        int visited = 0;
        boolean found = false;
        while (!frontier.isEmpty() && visited++ < MAX_VISITED)
        {
            var node = frontier.poll();
            long cell = node.cell();
            if (cell == goal)
            {
                found = true;
                break;
            }
            int x = BlockPos.getX(cell), y = BlockPos.getY(cell), z = BlockPos.getZ(cell);
            double reached = cost.get(cell);
            // Reached again by a shorter way since it was queued
            if (node.reach() > reached + distance(x, y, z, goalX, goalY, goalZ) + 1e-9)
                continue;

            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        if (dx == 0 && dy == 0 && dz == 0)
                            continue;
                        int nx = x + dx, ny = y + dy, nz = z + dz;
                        if (nx < minX || ny < minY || nz < minZ || nx > maxX || ny > maxY || nz > maxZ)
                            continue;
                        long next = BlockPos.asLong(nx, ny, nz);
                        double stepped = reached + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (stepped >= cost.get(next))
                            continue;
                        // The ends are where they are, open or not. Between them, no corner is cut: every block a step brushes past must be open
                        if (!passable(space, x, y, z, dx, dy, dz, next == goal))
                            continue;
                        cost.put(next, stepped);
                        cameFrom.put(next, cell);
                        frontier.add(new Node(next, stepped + distance(nx, ny, nz, goalX, goalY, goalZ)));
                    }
        }
        if (!found)
            return null;

        var cells = new ArrayList<Vec3>();
        for (long cell = goal; cell != NOWHERE; cell = cameFrom.get(cell))
            cells.add(new Vec3(BlockPos.getX(cell) + 0.5, BlockPos.getY(cell) + 0.5, BlockPos.getZ(cell) + 0.5));
        var points = new ArrayList<Vec3>();
        for (int i = cells.size() - 1; i >= 0; i--)
            points.add(cells.get(i));
        points.set(0, from);
        points.set(points.size() - 1, to);
        return taut(points, sight);
    }

    /**
     * @return whether a step from one block to a neighbor is open: the neighbor itself, unless it is the end, and every block the step brushes past
     */
    static boolean passable(Space space, int x, int y, int z, int dx, int dy, int dz, boolean toGoal)
    {
        for (int ox = 0; ox <= Math.abs(dx); ox++)
            for (int oy = 0; oy <= Math.abs(dy); oy++)
                for (int oz = 0; oz <= Math.abs(dz); oz++)
                {
                    if (ox == 0 && oy == 0 && oz == 0)
                        continue;
                    boolean isEnd = ox == Math.abs(dx) && oy == Math.abs(dy) && oz == Math.abs(dz);
                    if (isEnd && toGoal)
                        continue;
                    if (!space.open(x + ox * dx, y + oy * dy, z + oz * dz))
                        return false;
                }
        return true;
    }

    /**
     * Drops every point that a straight line can skip.
     */
    private static List<Vec3> taut(List<Vec3> points, Sight sight)
    {
        var corners = new ArrayList<Vec3>();
        corners.add(points.getFirst());
        int at = 0;
        while (at < points.size() - 1)
        {
            int next = at + 1;
            for (int far = points.size() - 1; far > at + 1; far--)
                if (sight.clear(points.get(at), points.get(far)))
                {
                    next = far;
                    break;
                }
            corners.add(points.get(next));
            at = next;
        }
        return corners;
    }

    private static double distance(int x, int y, int z, int toX, int toY, int toZ)
    {
        double dx = toX - x, dy = toY - y, dz = toZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double value)
    {
        return (int) Math.floor(value);
    }
}
