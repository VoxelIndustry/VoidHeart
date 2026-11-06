package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.util.StringRepresentable;

/** The feel of a district. The order is part of world generation, so add new themes at the end and never reorder them. */
public enum DaedalusTheme implements StringRepresentable
{
    ARCHIVE("archive", 0xC9A36A, new Layout(3, 5, 6, 14, 28, 1, 0.42F, 2)),
    WARREN("warren", 0x9A8F86, new Layout(3, 3, 4, 1, 6, 9, 0.55F, 4)),
    GALLERY("gallery", 0xE3C15B, new Layout(5, 5, 6, 18, 24, 1, 0.48F, 2)),
    CISTERN("cistern", 0x6FA9A8, new Layout(3, 4, 6, 8, 18, 3, 0.42F, 2)),
    STAIRWELL("stairwell", 0xC8CDD8, new Layout(3, 4, 6, 6, 40, 2, 0.40F, 2)),
    WRITHING("writhing", 0xA45BC9, new Layout(3, 4, 6, 2, 12, 7, 0.45F, 3));

    private static final long THEME_SALT = 0x7468656D65L;
    private static final DaedalusTheme[] VALUES = values();

    private final String name;
    private final int color;
    private final Layout layout;

    DaedalusTheme(String name, int color, Layout layout)
    {
        this.name = name;
        this.color = color;
        this.layout = layout;
    }

    /** Comes from the position alone, because biomes follow the theme and biome sources are never told the world seed. */
    public static DaedalusTheme at(DistrictPos pos)
    {
        return VALUES[DaedalusHash.pick(DaedalusHash.mix(0, pos.x(), pos.z(), THEME_SALT), VALUES.length)];
    }

    public int color()
    {
        return color;
    }

    Layout layout()
    {
        return layout;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }

    /** Sizes are in blocks. Routing costs compare with 4 for each cell walked, and density is the share of the lattice to carve. */
    record Layout(int corridorWidth, int corridorHeight, int roomHeight, int turnCost, int stairCost, int jitter, float density, int lampEvery)
    {
    }
}
