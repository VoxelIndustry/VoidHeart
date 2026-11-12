package net.voxelindustry.voidheart.common.world.underway;

import java.util.Arrays;

/** A region of the Underway being drawn in materials, solid rock until hollowed, with the props standing in its air. Writes outside it are ignored. */
public final class UnderwayCanvas
{
    private static final byte ROCK = (byte) UnderwayMaterial.ROCK.ordinal();

    public final int width;
    public final int depth;
    private final byte[] cells;
    private final short[] props;
    private int minX;
    private int minZ;

    public UnderwayCanvas(int width, int depth)
    {
        this.width = width;
        this.depth = depth;
        cells = new byte[width * depth * UnderwayLevels.HEIGHT];
        props = new short[cells.length];
    }

    public void reset(int minX, int minZ)
    {
        this.minX = minX;
        this.minZ = minZ;
        Arrays.fill(cells, ROCK);
        Arrays.fill(props, (short) 0);
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
        return ((y - UnderwayLevels.MIN_Y) * depth + (z - minZ)) * width + (x - minX);
    }

    private boolean isOutside(int x, int y, int z)
    {
        return x < minX || x > maxX() || z < minZ || z > maxZ() || y < UnderwayLevels.MIN_Y || y > UnderwayLevels.TOP_Y;
    }

    public UnderwayMaterial get(int x, int y, int z)
    {
        return isOutside(x, y, z) ? UnderwayMaterial.ROCK : UnderwayMaterial.of(cells[index(x, y, z)]);
    }

    /** Anything solid written over a prop takes its place for good: the prop does not come back if the place is later opened again. */
    public void set(int x, int y, int z, UnderwayMaterial material)
    {
        if (isOutside(x, y, z))
            return;
        int index = index(x, y, z);
        cells[index] = (byte) material.ordinal();
        if (material != UnderwayMaterial.AIR)
            props[index] = 0;
    }

    /** Stands a prop in the air, or on a rail it then replaces. Anywhere else it is left out. */
    public void setProp(int x, int y, int z, UnderwayProp prop, int facing)
    {
        if (isOutside(x, y, z))
            return;
        int index = index(x, y, z);
        var material = UnderwayMaterial.of(cells[index]);
        if (material != UnderwayMaterial.AIR && !material.isRail())
            return;
        cells[index] = (byte) UnderwayMaterial.AIR.ordinal();
        props[index] = (short) (1 + prop.ordinal() * 4 + facing);
    }

    /** @return the prop standing there, or null */
    public UnderwayProp propAt(int x, int y, int z)
    {
        if (isOutside(x, y, z))
            return null;
        int prop = props[index(x, y, z)];
        return prop == 0 || cells[index(x, y, z)] != UnderwayMaterial.AIR.ordinal() ? null : UnderwayProp.of((prop - 1) / 4);
    }

    /** @return the compass direction the prop there faces */
    public int propFacing(int x, int y, int z)
    {
        return isOutside(x, y, z) ? 0 : (props[index(x, y, z)] - 1) & 3;
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, UnderwayMaterial material)
    {
        replace(x0, y0, z0, x1, y1, z1, null, material);
    }

    /**
     * @param from the only material to overwrite, or null to overwrite everything
     */
    public void replace(int x0, int y0, int z0, int x1, int y1, int z1, UnderwayMaterial from, UnderwayMaterial to)
    {
        int fromX = Math.max(x0, minX);
        int toX = Math.min(x1, maxX());
        int fromZ = Math.max(z0, minZ);
        int toZ = Math.min(z1, maxZ());
        int fromY = Math.max(y0, UnderwayLevels.MIN_Y);
        int toY = Math.min(y1, UnderwayLevels.TOP_Y);
        byte value = (byte) to.ordinal();
        for (int y = fromY; y <= toY; y++)
        {
            for (int z = fromZ; z <= toZ; z++)
            {
                for (int at = index(fromX, y, z), end = at + toX - fromX; at <= end; at++)
                {
                    if (from == null || cells[at] == from.ordinal())
                    {
                        cells[at] = value;
                        if (to != UnderwayMaterial.AIR)
                            props[at] = 0;
                    }
                }
            }
        }
    }
}
