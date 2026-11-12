package net.voxelindustry.voidheart.common.world.underway;

/** The heights of the Underway, all under the Daedalus' floor at 0. A floor is the block walked on. */
public final class UnderwayLevels
{
    public static final int MIN_Y = -64;
    public static final int TOP_Y = -1;
    public static final int HEIGHT = TOP_Y - MIN_Y + 1;

    /** The floor of a district's lowest tier, where a hatch lies. */
    public static final int HATCH_FLOOR = 1;
    public static final int ENTRY_FLOOR = -15;
    public static final int ENTRY_HEIGHT = 4;
    public static final int LIVING_FLOOR = -31;
    public static final int LIVING_HEIGHT = 5;
    public static final int PLATFORM_FLOOR = -47;
    public static final int RAIL_HEIGHT = 4;
    public static final int BALLAST = -49;
    public static final int TRUNK_HEIGHT = 9;
    public static final int TRUNK_TOP = BALLAST + TRUNK_HEIGHT;

    private UnderwayLevels()
    {
    }
}
