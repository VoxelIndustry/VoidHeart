package net.voxelindustry.voidheart.common.world.daedalus;

/** What a generated position is made of, before a theme decides which blocks that means. */
public enum DaedalusMaterial
{
    AIR,
    FILL,
    BOUND,
    FLOOR,
    FLOOR_TRIM,
    WALL,
    PANEL,
    TRIM,
    CEILING,
    COLUMN,
    LAMP,
    WATER,
    SHELF,
    FACADE,
    FACADE_TRIM,
    RAIL_X,
    RAIL_Z,
    RAIL_POST,
    CHAIN,
    SLAB,
    SLAB_TOP,
    STAIRS_NORTH,
    STAIRS_EAST,
    STAIRS_SOUTH,
    STAIRS_WEST,
    ARCH_NORTH,
    ARCH_EAST,
    ARCH_SOUTH,
    ARCH_WEST;

    private static final DaedalusMaterial[] VALUES = values();

    public static DaedalusMaterial of(byte ordinal)
    {
        return VALUES[ordinal];
    }

    public static DaedalusMaterial stairs(int climbDirection)
    {
        return VALUES[STAIRS_NORTH.ordinal() + climbDirection];
    }

    /** Upside down stairs, for the underside of arches. */
    public static DaedalusMaterial arch(int direction)
    {
        return VALUES[ARCH_NORTH.ordinal() + direction];
    }

    public boolean isStep()
    {
        return this == SLAB || (ordinal() >= STAIRS_NORTH.ordinal() && ordinal() <= STAIRS_WEST.ordinal());
    }

    public boolean isPassable()
    {
        return this == AIR || this == WATER || this == CHAIN;
    }
}
