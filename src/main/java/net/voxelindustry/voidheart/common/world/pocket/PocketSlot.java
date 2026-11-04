package net.voxelindustry.voidheart.common.world.pocket;

// What a cell of a pocket is for. Each variant's palette decides which block fills each slot.
public enum PocketSlot
{
    AIR,
    FLUID,
    FROZEN_FLUID,
    WALL,
    ROCK,
    STRATA,
    TOPSOIL,
    TOPSOIL_ALT,
    SUBSOIL,
    SHORE,
    BED,
    BED_ALT,
    CEILING,
    SKIN,
    CONE,
    PEDESTAL;

    private static final PocketSlot[] VALUES = values();

    public boolean isOpen()
    {
        return this == AIR || this == FLUID;
    }

    // The pocket wall stays bare, and a frozen surface could melt from under what grows on it.
    public boolean isAnchor()
    {
        return !isOpen() && this != WALL && this != FROZEN_FLUID;
    }

    static PocketSlot of(int ordinal)
    {
        return VALUES[ordinal];
    }
}
