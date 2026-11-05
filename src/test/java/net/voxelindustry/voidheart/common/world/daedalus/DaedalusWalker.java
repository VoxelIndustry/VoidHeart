package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * Walks a painted canvas the way a player would: two blocks of headroom, solid ground, one step up where there is room to jump or stairs to climb, and short drops. Water is never ground, and window sills are not a way through.
 */
final class DaedalusWalker
{
    private static final int MAX_DROP = 3;

    private final DaedalusCanvas canvas;
    private final BitSet reached;

    DaedalusWalker(DaedalusCanvas canvas)
    {
        this.canvas = canvas;
        reached = new BitSet(canvas.width * canvas.depth * DistrictPos.HEIGHT);
    }

    private int key(int x, int y, int z)
    {
        return (y * canvas.depth + (z - canvas.minZ())) * canvas.width + (x - canvas.minX());
    }

    private boolean isInside(int x, int z)
    {
        return x >= canvas.minX() && x <= canvas.maxX() && z >= canvas.minZ() && z <= canvas.maxZ();
    }

    boolean canStand(int x, int y, int z)
    {
        if (!isInside(x, z) || y < 1 || y >= DistrictPos.TOP_Y - 1)
            return false;
        var feet = canvas.get(x, y, z);
        return feet.isPassable() && feet != DaedalusMaterial.OPENING && canvas.get(x, y + 1, z).isPassable() && !canvas.get(x, y - 1, z).isPassable();
    }

    void walkFrom(int startX, int startY, int startZ)
    {
        if (!canStand(startX, startY, startZ))
            throw new IllegalArgumentException("Cannot stand at the start " + startX + " " + startY + " " + startZ + " on " + canvas.get(startX, startY - 1, startZ));

        var queue = new ArrayDeque<int[]>();
        reached.set(key(startX, startY, startZ));
        queue.add(new int[] { startX, startY, startZ });
        while (!queue.isEmpty())
        {
            int[] at = queue.poll();
            for (int direction = 0; direction < Compass.COUNT; direction++)
                step(at[0], at[1], at[2], at[0] + Compass.dx(direction), at[2] + Compass.dz(direction), queue);
        }
    }

    private void step(int x, int y, int z, int toX, int toZ, ArrayDeque<int[]> queue)
    {
        if (!isInside(toX, toZ))
            return;

        if (canvas.get(x, y + 2, z).isPassable() || canvas.get(toX, y, toZ).isStep())
            visit(toX, y + 1, toZ, queue);
        visit(toX, y, toZ, queue);
        for (int drop = 1; drop <= MAX_DROP; drop++)
        {
            if (!canvas.get(toX, y - drop + 1, toZ).isPassable() || !canvas.get(toX, y - drop + 2, toZ).isPassable())
                return;
            visit(toX, y - drop, toZ, queue);
        }
    }

    private void visit(int x, int y, int z, ArrayDeque<int[]> queue)
    {
        if (!canStand(x, y, z))
            return;
        int key = key(x, y, z);
        if (reached.get(key))
            return;
        reached.set(key);
        queue.add(new int[] { x, y, z });
    }

    boolean hasReached(int x, int y, int z)
    {
        return isInside(x, z) && y >= 0 && y < DistrictPos.HEIGHT && reached.get(key(x, y, z));
    }

    boolean hasReachedWithin(int x0, int y0, int z0, int x1, int y1, int z1)
    {
        for (int y = y0; y <= y1; y++)
        {
            for (int z = z0; z <= z1; z++)
            {
                for (int x = x0; x <= x1; x++)
                {
                    if (hasReached(x, y, z))
                        return true;
                }
            }
        }
        return false;
    }
}
