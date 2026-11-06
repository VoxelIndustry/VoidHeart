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
    COT,
    ORRERY,
    LECTERN,
    CHAIR,
    HOURGLASS,
    CLOCK,
    MIRROR,
    PIPES,
    BASIN,
    CRATES,
    BRAZIER,
    CAGE,
    CANDLES,
    REMAINS,
    TABLE,
    BOOKS,
    SCONCE,
    TAPESTRY,
    CABINET,
    BARREL,
    TRUNK,
    PLANTER,
    CENSER,
    STATUE,
    SCROLLS,
    GEARS,
    GAUGE,
    CONSOLE,
    COIL,
    CONDENSER,
    FLYWHEEL,
    PISTON,
    AUTOMATON,
    LANTERN,
    WINCH,
    CELLS,
    SARCOPHAGUS,
    SARCOPHAGUS_FOOT,
    SARCOPHAGUS_OPEN;

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
        return this == FRAME || this == VALVE || this == CLOCK || this == MIRROR || this == PIPES || this == SCONCE || this == TAPESTRY || this == SCROLLS || this == GEARS || this == GAUGE || this == WINCH;
    }
}
