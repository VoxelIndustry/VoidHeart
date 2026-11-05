package net.voxelindustry.voidheart.common.world.dungeon;

/**
 * What a cell of a dungeon is made of. Each theme's palette says which blocks that means.
 */
public enum DungeonMaterial
{
    UNSET,
    // Holds the space of a structure template, which is placed apart from the grid
    TEMPLATE,
    AIR,

    POCKET_WALL,
    ROCK,
    ROCK_NATIVE,
    VEIN,
    VEIN_WRITHING,
    VEIN_BLOSSOM,
    VEIN_MAW,

    WALL,
    WALL_TRIM,
    PANEL,
    MONOLITH(true),
    FLOOR,
    FLOOR_TRIM,
    CEILING,
    COLUMN,
    STAIRS(true),
    SLAB,
    FENCE(true),
    WINDOW(true),
    RUNNER,

    LANTERN,
    CANDLE,
    GLOW,
    // Invisible, as a lamp on every wall would read as too well kept
    FILL_LIGHT,
    CAMPFIRE,
    CHAIN,

    FLUID,
    POOL_BED,

    METAL_BLOCK,
    METAL_CUT,
    METAL_STAIRS(true),
    METAL_SLAB,
    METAL_WALL(true),

    SOIL,
    SUBSOIL,
    PLANKS,
    FRAME,
    ROOF_STAIRS(true),
    ROOF_SLAB,
    WOOD_FENCE(true),
    DOOR(true),

    LOG,
    LEAVES,
    ROOTS,
    HANGING,
    PLANT,
    TALL_PLANT,
    CAP,
    CAP_GLOW,
    CAP_GILL,
    STEM,

    BOOKSHELF,
    LECTERN,
    FURNACE,
    FORGE,
    ANVIL,
    CAULDRON,
    BARREL,
    HAY,
    COBWEB,
    BELL,
    PIPE,
    PIPE_THIN,
    GRATE(true),
    CHEST,
    NEST,
    // Where players leave from, always within sight of where they arrive
    RETURN_RIFT,

    CORAL_BLOCK,
    CORAL_GROWTH,
    GLOW_ALGAE,
    BARNACLE,
    ANEMONE,
    KELP,
    KELP_TIP,
    DRIFT_ALGAE,
    DRIFT_ALGAE_TIP,
    WHALE_BONE,
    VENT_CHIMNEY,
    SMOKER_VENT,

    AZURIUM_SMALL,
    AZURIUM_MEDIUM,
    AZURIUM_LARGE,
    RUBRUM_SMALL,
    RUBRUM_MEDIUM,
    RUBRUM_LARGE,
    MEMORIAM_SMALL,
    MEMORIAM_MEDIUM,
    MEMORIAM_LARGE;

    private final boolean connects;

    DungeonMaterial()
    {
        this(false);
    }

    DungeonMaterial(boolean connects)
    {
        this.connects = connects;
    }

    // Its shape depends on its neighbours, which only a post-processing pass can settle across chunks
    public boolean connects()
    {
        return connects;
    }

    public boolean isPassable()
    {
        return switch (this)
        {
            case UNSET, AIR, FILL_LIGHT, FLUID, RUNNER, CANDLE, LANTERN, CHAIN, PLANT, TALL_PLANT, HANGING, COBWEB, RETURN_RIFT,
                 CORAL_GROWTH, GLOW_ALGAE, BARNACLE, ANEMONE, KELP, KELP_TIP, DRIFT_ALGAE, DRIFT_ALGAE_TIP -> true;
            default -> false;
        };
    }

    public boolean isOpen()
    {
        return this == UNSET || this == AIR || this == FILL_LIGHT;
    }

    public boolean isAnchor()
    {
        return switch (this)
        {
            case ROCK, ROCK_NATIVE, VEIN, VEIN_WRITHING, WALL, PANEL, FLOOR, CEILING, WALL_TRIM, COLUMN, SOIL, SUBSOIL, POOL_BED, CORAL_BLOCK, WHALE_BONE, VENT_CHIMNEY -> true;
            default -> false;
        };
    }

    public boolean isCube()
    {
        return switch (this)
        {
            case STAIRS, SLAB, FENCE, WINDOW, RUNNER, LANTERN, CANDLE, FILL_LIGHT, CAMPFIRE, CHAIN, METAL_STAIRS, METAL_SLAB, METAL_WALL, ROOF_STAIRS, ROOF_SLAB,
                 WOOD_FENCE, DOOR, HANGING, PLANT, TALL_PLANT, LECTERN, ANVIL, CAULDRON, COBWEB, BELL, PIPE_THIN, GRATE, CHEST, NEST, FLUID,
                 CORAL_GROWTH, GLOW_ALGAE, BARNACLE, ANEMONE, KELP, KELP_TIP, DRIFT_ALGAE, DRIFT_ALGAE_TIP,
                 AZURIUM_SMALL, AZURIUM_MEDIUM, AZURIUM_LARGE, RUBRUM_SMALL, RUBRUM_MEDIUM, RUBRUM_LARGE, MEMORIAM_SMALL, MEMORIAM_MEDIUM, MEMORIAM_LARGE -> false;
            default -> true;
        };
    }

    public boolean isCrystal()
    {
        return ordinal() >= AZURIUM_SMALL.ordinal();
    }

    public static DungeonMaterial crystal(DungeonCrystal crystal, int size)
    {
        return values()[AZURIUM_SMALL.ordinal() + crystal.ordinal() * 3 + size];
    }

    public DungeonCrystal crystalKind()
    {
        return DungeonCrystal.values()[(ordinal() - AZURIUM_SMALL.ordinal()) / 3];
    }
}
