package net.voxelindustry.voidheart.common.world.sanctum;

/** What a sanctum is built of, before the placer turns it into blocks. Plans store the ordinal in a byte. */
public enum SanctumMaterial
{
    AIR,
    FOUNDATION,
    WALL,
    WALL_CRACKED,
    PANEL,
    TRIM,
    COLUMN,
    PLINTH,
    CAPITAL,
    FLOOR,
    INLAY,
    GILT,
    CEILING,
    COFFER,
    GATE_FRAME,
    FLESH,
    FLESH_BRUISED,
    WRITHING,
    WRITHING_MAW,
    WRITHING_BLOSSOM;

    private static final SanctumMaterial[] VALUES = values();

    public static SanctumMaterial of(int ordinal)
    {
        return VALUES[ordinal];
    }

    public boolean solid()
    {
        return this != AIR;
    }

    /** Whether the flesh may take it over: the dressed parts of a column hold out longer. */
    public boolean architectural()
    {
        return this == COLUMN || this == PLINTH || this == CAPITAL || this == GILT || this == GATE_FRAME;
    }
}
