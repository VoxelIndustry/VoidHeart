package net.voxelindustry.voidheart.common.content.salt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a use of salt lays, worked out on the floor plan alone: a fresh 3x3 on bare ground, or one ring around a patch already laid, nearest the click
 * first and never past the size cap. The world decides afterwards which of these cells actually have a floor.
 */
public final class SaltPatch
{
    public static final int CELLS_PER_SALT = 9;
    public static final int MAX_SIZE = 15;

    public record Cell(int x, int z)
    {
        long distanceSqr(Cell other)
        {
            long dx = x - other.x;
            long dz = z - other.z;
            return dx * dx + dz * dz;
        }
    }

    private SaltPatch()
    {
    }

    /**
     * @return the 3x3 around the center, middle first then nearest out
     */
    public static List<Cell> fresh(Cell center)
    {
        var cells = new ArrayList<Cell>();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
                cells.add(new Cell(center.x + dx, center.z + dz));
        }
        cells.sort(byDistanceTo(center));
        return cells;
    }

    /**
     * @return the cells one step outside the patch, on every side the size cap still leaves room for, nearest the click first
     */
    public static List<Cell> ring(Collection<Cell> patch, Cell click)
    {
        if (patch.isEmpty())
            return List.of();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (var cell : patch)
        {
            minX = Math.min(minX, cell.x);
            maxX = Math.max(maxX, cell.x);
            minZ = Math.min(minZ, cell.z);
            maxZ = Math.max(maxZ, cell.z);
        }
        int[] xs = growth(minX, maxX, click.x);
        int[] zs = growth(minZ, maxZ, click.z);

        Set<Cell> inside = new HashSet<>(patch);
        Set<Cell> ring = new LinkedHashSet<>();
        for (var cell : patch)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    var next = new Cell(cell.x + dx, cell.z + dz);
                    if (next.x >= xs[0] && next.x <= xs[1] && next.z >= zs[0] && next.z <= zs[1] && !inside.contains(next))
                        ring.add(next);
                }
            }
        }
        var ordered = new ArrayList<>(ring);
        ordered.sort(byDistanceTo(click));
        return ordered;
    }

    /**
     * @return how many salt a number of new cells costs
     */
    public static int cost(int cells)
    {
        return (cells + CELLS_PER_SALT - 1) / CELLS_PER_SALT;
    }

    /**
     * The range a patch may span along one axis after growing: both sides while it fits, only the click's side when one more is all that fits.
     */
    private static int[] growth(int min, int max, int click)
    {
        int size = max - min + 1;
        if (size + 2 <= MAX_SIZE)
            return new int[] { min - 1, max + 1 };
        if (size + 1 <= MAX_SIZE)
            return 2 * click < min + max ? new int[] { min - 1, max } : new int[] { min, max + 1 };
        return new int[] { min, max };
    }

    private static Comparator<Cell> byDistanceTo(Cell to)
    {
        return Comparator.<Cell>comparingLong(cell -> cell.distanceSqr(to)).thenComparingInt(Cell::x).thenComparingInt(Cell::z);
    }
}
