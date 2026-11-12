package net.voxelindustry.voidheart.common.world.underway;

/** What a generated position of the Underway is made of, before blocks are chosen. */
public enum UnderwayMaterial
{
    /** The ground nobody reaches. */
    ROCK,
    AIR,
    /** Concrete that cannot be broken: every wall, floor and ceiling against the rock. */
    SHELL,
    FLOOR,
    PLATFORM,
    BALLAST,
    STEEL,
    COLUMN,
    GRATING,
    STAIRS_NORTH,
    STAIRS_EAST,
    STAIRS_SOUTH,
    STAIRS_WEST,
    TRUNK_RAIL_X,
    TRUNK_RAIL_Z,
    FEEDER_RAIL_X,
    FEEDER_RAIL_Z,
    /** An armoured door a hand can open. */
    DOOR,
    /** A blast gate, which ends a sector. */
    GATE,
    EMBRASURE,
    LAMP,
    EMERGENCY_LAMP,
    RUBBLE,
    WATER,
    VOID,
    /** Armour painted with warning stripes, around what moves. */
    HAZARD;

    private static final UnderwayMaterial[] VALUES = values();

    public static UnderwayMaterial of(int ordinal)
    {
        return VALUES[ordinal];
    }

    public static UnderwayMaterial stairs(int climbDirection)
    {
        return VALUES[STAIRS_NORTH.ordinal() + climbDirection];
    }

    /** @return whether a player walks through it */
    public boolean isOpen()
    {
        return this == AIR || this == DOOR || this == WATER || isRail();
    }

    public boolean isRail()
    {
        return this == TRUNK_RAIL_X || this == TRUNK_RAIL_Z || this == FEEDER_RAIL_X || this == FEEDER_RAIL_Z;
    }
}
