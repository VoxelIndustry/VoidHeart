package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;

/**
 * Every cell of a dungeon or of a room being built, in coordinates local to its corner. Writes outside its bounds are ignored.
 */
public class DungeonGrid
{
    public final int width;
    public final int height;
    public final int depth;
    private final short[] cells;

    public DungeonGrid(int width, int height, int depth)
    {
        this.width = width;
        this.height = height;
        this.depth = depth;
        cells = new short[width * height * depth];
    }

    public boolean isInside(int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
    }

    private int index(int x, int y, int z)
    {
        return (y * depth + z) * width + x;
    }

    public short cell(int x, int y, int z)
    {
        return cells[index(x, y, z)];
    }

    public DungeonMaterial material(int x, int y, int z)
    {
        return isInside(x, y, z) ? DungeonCell.material(cell(x, y, z)) : DungeonMaterial.UNSET;
    }

    public void setCell(int x, int y, int z, short cell)
    {
        if (isInside(x, y, z))
            cells[index(x, y, z)] = cell;
    }

    public void set(int x, int y, int z, DungeonMaterial material)
    {
        setCell(x, y, z, DungeonCell.of(material));
    }

    public void set(int x, int y, int z, DungeonMaterial material, Direction facing)
    {
        setCell(x, y, z, DungeonCell.of(material, facing));
    }

    public void set(int x, int y, int z, DungeonMaterial material, Direction facing, boolean flipped)
    {
        setCell(x, y, z, DungeonCell.of(material, facing, flipped));
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, DungeonMaterial material)
    {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
            {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                    set(x, y, z, material);
            }
        }
    }
}
