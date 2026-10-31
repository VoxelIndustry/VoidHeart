package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;

import java.util.BitSet;
import java.util.Collection;

/**
 * Table blocks of one structure as cells of their bounding rectangle, all at the same height.
 */
public record OculusFootprint(int y, int minX, int minZ, int width, int depth, BitSet cells)
{
    public static OculusFootprint of(Collection<BlockPos> blocks)
    {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var pos : blocks)
        {
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int width = maxX - minX + 1;
        var cells = new BitSet();
        for (var pos : blocks)
            cells.set(pos.getX() - minX + (pos.getZ() - minZ) * width);
        return new OculusFootprint(blocks.iterator().next().getY(), minX, minZ, width, maxZ - minZ + 1, cells);
    }

    public boolean contains(int x, int z)
    {
        int cellX = x - minX;
        int cellZ = z - minZ;
        return cellX >= 0 && cellX < width && cellZ >= 0 && cellZ < depth && cells.get(cellX + cellZ * width);
    }

    public int size()
    {
        return cells.cardinality();
    }

    /**
     * The block standing for the whole structure: the first cell of the westmost column, northmost.
     */
    public BlockPos anchor()
    {
        for (int cellZ = 0; cellZ < depth; cellZ++)
        {
            if (cells.get(cellZ * width))
                return new BlockPos(minX, y, minZ + cellZ);
        }
        throw new IllegalStateException("Empty footprint");
    }

    public double centerX()
    {
        return minX + width / 2D;
    }

    public double centerZ()
    {
        return minZ + depth / 2D;
    }
}
