package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A way for a Klein Star around what stands between it and where it means to burst. The shortest way through open blocks is searched, in a box a
 * few blocks wider than the two ends, then pulled taut: every point that can be skipped in a straight line is dropped, so what is left is only the
 * corners it has to turn. {@link KleinStarPath} rounds those into curves.
 */
public final class KleinStarRoute
{
    // How far past its two ends the search may wander, and how many blocks it may look at before it gives up
    private static final int MARGIN      = 7;
    private static final int MAX_VISITED = 12_000;

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

    private record Node(int index, double reach) implements Comparable<Node>
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
     * @return the corners to turn on the way, from the start to the end included, or null when there is no way within reach
     */
    public static @Nullable List<Vec3> find(Space space, Sight sight, Vec3 from, Vec3 to)
    {
        int minX = floor(Math.min(from.x, to.x)) - MARGIN, minY = floor(Math.min(from.y, to.y)) - MARGIN, minZ = floor(Math.min(from.z, to.z)) - MARGIN;
        int sizeX = floor(Math.max(from.x, to.x)) + MARGIN - minX + 1;
        int sizeY = floor(Math.max(from.y, to.y)) + MARGIN - minY + 1;
        int sizeZ = floor(Math.max(from.z, to.z)) + MARGIN - minZ + 1;

        int startX = floor(from.x) - minX, startY = floor(from.y) - minY, startZ = floor(from.z) - minZ;
        int goalX = floor(to.x) - minX, goalY = floor(to.y) - minY, goalZ = floor(to.z) - minZ;
        int start = (startX * sizeY + startY) * sizeZ + startZ;
        int goal = (goalX * sizeY + goalY) * sizeZ + goalZ;

        var cost = new double[sizeX * sizeY * sizeZ];
        var cameFrom = new int[cost.length];
        Arrays.fill(cost, Double.MAX_VALUE);
        Arrays.fill(cameFrom, -1);
        cost[start] = 0;

        var frontier = new PriorityQueue<Node>();
        frontier.add(new Node(start, 0));
        int visited = 0;
        boolean found = false;
        while (!frontier.isEmpty() && visited++ < MAX_VISITED)
        {
            var node = frontier.poll();
            int index = node.index();
            if (index == goal)
            {
                found = true;
                break;
            }
            int z = index % sizeZ, y = index / sizeZ % sizeY, x = index / sizeZ / sizeY;
            // Reached again by a shorter way since it was queued
            if (node.reach() > cost[index] + distance(x, y, z, goalX, goalY, goalZ) + 1e-9)
                continue;

            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        if (dx == 0 && dy == 0 && dz == 0)
                            continue;
                        int nx = x + dx, ny = y + dy, nz = z + dz;
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ)
                            continue;
                        int next = (nx * sizeY + ny) * sizeZ + nz;
                        double stepped = cost[index] + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (stepped >= cost[next])
                            continue;
                        // The ends are where they are, open or not. Between them, no corner is cut: every block a step brushes past must be open
                        if (!passable(space, minX + x, minY + y, minZ + z, dx, dy, dz, next == goal))
                            continue;
                        cost[next] = stepped;
                        cameFrom[next] = index;
                        frontier.add(new Node(next, stepped + distance(nx, ny, nz, goalX, goalY, goalZ)));
                    }
        }
        if (!found)
            return null;

        var cells = new ArrayList<Vec3>();
        for (int index = goal; index != -1; index = cameFrom[index])
        {
            int z = index % sizeZ, y = index / sizeZ % sizeY, x = index / sizeZ / sizeY;
            cells.add(new Vec3(minX + x + 0.5, minY + y + 0.5, minZ + z + 0.5));
        }
        var points = new ArrayList<Vec3>();
        for (int i = cells.size() - 1; i >= 0; i--)
            points.add(cells.get(i));
        points.set(0, from);
        points.set(points.size() - 1, to);
        return taut(points, sight);
    }

    private static boolean passable(Space space, int x, int y, int z, int dx, int dy, int dz, boolean toGoal)
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
        int dx = toX - x, dy = toY - y, dz = toZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double value)
    {
        return (int) Math.floor(value);
    }
}
