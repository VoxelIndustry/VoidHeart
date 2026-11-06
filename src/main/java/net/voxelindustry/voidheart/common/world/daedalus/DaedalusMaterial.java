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
    ALTAR,
    PILLAR,
    SHRINE_LAMP,
    MONOLITH_ALONE,
    MONOLITH_BASE,
    MONOLITH_SHAFT,
    MONOLITH_TIP,
    ELDRITCH,
    OBSIDIAN,
    COBWEB,
    COPPER,
    COPPER_SLAB,
    VENT,
    PIPE_X,
    PIPE_Y,
    PIPE_Z,
    PROP;

    private static final DaedalusMaterial[] VALUES = values();

    /**
     * @param ordinal a canvas byte read unsigned, which stands for a prop from {@link DaedalusCanvas#PROP_BASE} up
     */
    public static DaedalusMaterial of(int ordinal)
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

    /**
     * @return the block of a broken monolith standing {@code height} blocks tall, counted from 0 at its foot
     */
    public static DaedalusMaterial monolith(int index, int height)
    {
        if (height == 1)
            return MONOLITH_ALONE;
        if (index == 0)
            return MONOLITH_BASE;
        return index == height - 1 ? MONOLITH_TIP : MONOLITH_SHAFT;
    }

    public boolean isMonolith()
    {
        return ordinal() >= MONOLITH_ALONE.ordinal() && ordinal() <= MONOLITH_TIP.ordinal();
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
