package net.voxelindustry.voidheart.common.world.underway;

/** How the centuries left a part of the Underway. */
public enum UnderwayRuin
{
    DRY,
    FLOODED,
    COLLAPSED,
    /** A single station the void broke into. */
    BREACHED,
    SEALED;

    /** @return whether every trunk tunnel leaving it is gated, to hold its water or its seal */
    public boolean isShutIn()
    {
        return this == FLOODED || this == SEALED;
    }
}
