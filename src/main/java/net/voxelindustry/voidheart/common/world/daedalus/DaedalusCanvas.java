package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.Arrays;

/**
 * A region of the Daedalus being drawn in materials. Writes outside it are ignored, and nothing can overwrite the floor and ceiling layers.
 */
public final class DaedalusCanvas
{
    private static final byte AIR = (byte) DaedalusMaterial.AIR.ordinal();
    private static final byte BOUND = (byte) DaedalusMaterial.BOUND.ordinal();
    private static final int MIN_PAINTABLE_Y = 1;
    private static final int MAX_PAINTABLE_Y = DistrictPos.TOP_Y - 1;

    public final int width;
    public final int depth;
    private final byte[] cells;
    private int minX;
    private int minZ;

    public DaedalusCanvas(int width, int depth)
    {
        this.width = width;
        this.depth = depth;
        cells = new byte[width * depth * DistrictPos.HEIGHT];
    }

    public void reset(int minX, int minZ)
    {
        this.minX = minX;
        this.minZ = minZ;
        Arrays.fill(cells, AIR);
        int layer = width * depth;
        Arrays.fill(cells, 0, layer, BOUND);
        Arrays.fill(cells, layer * DistrictPos.TOP_Y, layer * DistrictPos.HEIGHT, BOUND);
    }

    public int minX()
    {
        return minX;
    }

    public int minZ()
    {
        return minZ;
    }

    public int maxX()
    {
        return minX + width - 1;
    }

    public int maxZ()
    {
        return minZ + depth - 1;
    }

    private int index(int x, int y, int z)
    {
        return (y * depth + (z - minZ)) * width + (x - minX);
    }

    public boolean overlaps(int x0, int z0, int x1, int z1)
    {
        return x0 <= maxX() && x1 >= minX && z0 <= maxZ() && z1 >= minZ;
    }

    public DaedalusMaterial get(int x, int y, int z)
    {
        if (x < minX || x > maxX() || z < minZ || z > maxZ() || y < 0 || y >= DistrictPos.HEIGHT)
            return DaedalusMaterial.AIR;
        return DaedalusMaterial.of(cells[index(x, y, z)]);
    }

    public void set(int x, int y, int z, DaedalusMaterial material)
    {
        if (x < minX || x > maxX() || z < minZ || z > maxZ() || y < MIN_PAINTABLE_Y || y > MAX_PAINTABLE_Y)
            return;
        cells[index(x, y, z)] = (byte) material.ordinal();
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, DaedalusMaterial material)
    {
        int fromX = Math.max(x0, minX);
        int toX = Math.min(x1, maxX());
        int fromZ = Math.max(z0, minZ);
        int toZ = Math.min(z1, maxZ());
        int fromY = Math.max(y0, MIN_PAINTABLE_Y);
        int toY = Math.min(y1, MAX_PAINTABLE_Y);
        if (fromX > toX || fromZ > toZ)
            return;

        byte value = (byte) material.ordinal();
        for (int y = fromY; y <= toY; y++)
        {
            for (int z = fromZ; z <= toZ; z++)
            {
                int start = index(fromX, y, z);
                Arrays.fill(cells, start, start + toX - fromX + 1, value);
            }
        }
    }

    /**
     * @return the raw materials, by {@code (y * depth + z) * width + x} local to this canvas
     */
    byte[] cells()
    {
        return cells;
    }
}
