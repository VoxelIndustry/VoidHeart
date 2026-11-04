package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.util.StringRepresentable;

/**
 * Dungeons are cubes aligned on chunks. Rooms and corridors sit on a lattice of 4 block cells, the module massive_pocket was built on.
 */
public enum DungeonSize implements StringRepresentable
{
    SMALL("small", 32),
    MEDIUM("medium", 64),
    LARGE("large", 128);

    public static final int CELL = 4;
    public static final int LATTICE_MARGIN = 4;

    private final String name;
    private final int edge;

    DungeonSize(String name, int edge)
    {
        this.name = name;
        this.edge = edge;
    }

    public int edge()
    {
        return edge;
    }

    public int cells()
    {
        return (edge - LATTICE_MARGIN * 2) / CELL;
    }

    public static int plane(int cell)
    {
        return LATTICE_MARGIN + cell * CELL;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
