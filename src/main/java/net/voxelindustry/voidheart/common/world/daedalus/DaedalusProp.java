package net.voxelindustry.voidheart.common.world.daedalus;

/** The furniture made for the Daedalus. Generation stores a prop and its facing in one canvas byte, so there can be at most {@link DaedalusCanvas#MAX_PROPS}. */
public enum DaedalusProp
{
    BENCH,
    PLINTH,
    BUST,
    URN,
    CANDELABRUM,
    CHANDELIER,
    FRAME,
    DESK,
    LEDGERS,
    GRATE,
    VALVE,
    GROWTH,
    COT;

    private static final DaedalusProp[] VALUES = values();

    public static DaedalusProp of(int ordinal)
    {
        return VALUES[ordinal];
    }

    public String id()
    {
        return "daedalus_" + name().toLowerCase();
    }

    public boolean hangsOnWall()
    {
        return this == FRAME || this == VALVE;
    }
}
