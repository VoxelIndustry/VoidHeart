package net.voxelindustry.voidheart.common.world.mausoleum;

/** What a mausoleum is built of, before the placer turns it into blocks. Plans store the ordinal in a byte. */
public enum MausoleumMaterial
{
    AIR,
    FOUNDATION,
    WALL,
    WALL_CRACKED,
    GLYPHS,
    PANEL,
    TRIM,
    CORNICE,
    COLUMN,
    BAND,
    CAPITAL,
    FLOOR,
    INLAY,
    GILT,
    CEILING,
    DAIS,
    FALSE_DOOR;

    private static final MausoleumMaterial[] VALUES = values();

    public static MausoleumMaterial of(int ordinal)
    {
        return VALUES[ordinal];
    }
}
