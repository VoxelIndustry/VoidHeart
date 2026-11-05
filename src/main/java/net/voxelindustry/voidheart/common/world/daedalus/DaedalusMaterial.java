package net.voxelindustry.voidheart.common.world.daedalus;

/** What a generated position is made of, before a theme decides which blocks that means. */
public enum DaedalusMaterial
{
    AIR,
    OPENING,
    FILL,
    BOUND,
    FLOOR,
    FLOOR_TRIM,
    FLOOR_INLAY,
    CARPET,
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
    ARCH_WEST,
    PROP;

    private static final DaedalusMaterial[] VALUES = values();

    /**
     * @param ordinal a canvas byte, which stands for a prop from {@link DaedalusCanvas#PROP_BASE} up
     */
    public static DaedalusMaterial of(byte ordinal)
    {
        return ordinal >= DaedalusCanvas.PROP_BASE ? PROP : VALUES[ordinal];
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
        return this == AIR || this == OPENING || this == WATER || this == CHAIN || this == CARPET;
    }
}
