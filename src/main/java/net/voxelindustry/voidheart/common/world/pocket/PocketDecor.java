package net.voxelindustry.voidheart.common.world.pocket;

/**
 * Something growing in a pocket. Coordinates are relative to the heart, and amount is a length or count depending on the kind.
 */
public record PocketDecor(int x, int y, int z, Kind kind, int amount)
{
    public enum Kind
    {
        TREE,
        SMALL_PLANT,
        TALL_PLANT,
        FLOWER,
        TALL_FLOWER,
        BUSH,
        CARPET,
        DRIPLEAF,
        CAVE_VINE,
        HANGING_ROOTS,
        SPORE_BLOSSOM,
        CEILING_LIGHT,
        FILL_LIGHT,
        GLOW_LICHEN,
        SEAGRASS,
        KELP,
        SEA_PICKLE,
        CORAL,
        LILY_PAD,
        STALAGMITE,
        STALACTITE,
        EMBER_VENT,
        SPRING
    }
}
