package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Finds everywhere a player on foot gets to from a starting point, climbing one block at a time and dropping up to three. Props are taken as solid, though a player can climb over the low ones. */
final class UnderwayWalker
{
    private static final int MAX_DROP = 3;

    private final UnderwayCanvas canvas;
    private final Set<Long> reached = new HashSet<>();

    UnderwayWalker(UnderwayCanvas canvas, int startX, int startY, int startZ)
    {
        this.canvas = canvas;
        if (!canStand(startX, startY, startZ))
            throw new IllegalArgumentException("Nowhere to stand at " + startX + " " + startY + " " + startZ);

        var open = new ArrayDeque<int[]>();
        open.add(new int[] { startX, startY, startZ });
        reached.add(key(startX, startY, startZ));
        while (!open.isEmpty())
        {
            int[] at = open.poll();
            for (int swim = -1; swim <= 1; swim += 2)
            {
                if (canvas.get(at[0], at[1] + swim, at[2]) == UnderwayMaterial.WATER && canStand(at[0], at[1] + swim, at[2]) && reached.add(key(at[0], at[1] + swim, at[2])))
                    open.add(new int[] { at[0], at[1] + swim, at[2] });
            }
            for (int direction = 0; direction < Compass.COUNT; direction++)
            {
                int x = at[0] + Compass.dx(direction);
                int z = at[2] + Compass.dz(direction);
                for (int y = at[1] + 1; y >= at[1] - MAX_DROP; y--)
                {
                    if (!canStand(x, y, z))
                    {
                        if (y <= at[1] && !isClear(x, y + 1, z))
                            break;
                        continue;
                    }
                    if (reached.add(key(x, y, z)))
                        open.add(new int[] { x, y, z });
                    break;
                }
            }
        }
    }

    boolean reaches(int x, int y, int z)
    {
        return reached.contains(key(x, y, z));
    }

    private boolean canStand(int x, int y, int z)
    {
        var feet = canvas.get(x, y, z);
        if (canvas.propAt(x, y, z) != null || canvas.propAt(x, y + 1, z) != null)
            return false;
        return feet.isOpen() && canvas.get(x, y + 1, z).isOpen() && (feet == UnderwayMaterial.WATER || !canvas.get(x, y - 1, z).isOpen());
    }

    /** @return whether a body passes through this place on its way down to a lower landing */
    private boolean isClear(int x, int y, int z)
    {
        return canvas.get(x, y, z).isOpen() && canvas.propAt(x, y, z) == null;
    }

    private static long key(int x, int y, int z)
    {
        return ((long) x << 40) ^ ((long) (z & 0xFFFFF) << 20) ^ (y & 0xFFFFF);
    }
}
