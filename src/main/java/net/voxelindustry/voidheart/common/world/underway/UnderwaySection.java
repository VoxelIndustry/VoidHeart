package net.voxelindustry.voidheart.common.world.underway;

/** A part of a fort with its own power and its own security: one of its three levels. */
public enum UnderwaySection
{
    ENTRY,
    LIVING,
    RAIL;

    /** How far under a level's floor its section still reaches, which covers the pits of the entry gallery. */
    private static final int UNDER_FLOOR = 5;
    private static final UnderwaySection[] VALUES = values();

    public static UnderwaySection of(int ordinal)
    {
        return VALUES[ordinal];
    }

    /** @return the section a height of the Underway belongs to */
    public static UnderwaySection at(int y)
    {
        if (y >= UnderwayLevels.ENTRY_FLOOR - UNDER_FLOOR)
            return ENTRY;
        return y >= UnderwayLevels.LIVING_FLOOR - UNDER_FLOOR ? LIVING : RAIL;
    }

    /** @return the block walked on in most of it */
    public int floorY()
    {
        return switch (this)
        {
            case ENTRY -> UnderwayLevels.ENTRY_FLOOR;
            case LIVING -> UnderwayLevels.LIVING_FLOOR;
            case RAIL -> UnderwayLevels.PLATFORM_FLOOR;
        };
    }
}
