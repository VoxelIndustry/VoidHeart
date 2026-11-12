package net.voxelindustry.voidheart.common.world.underway;

/** The state of the trunk tunnel between two neighbouring districts. */
public enum UnderwayLink
{
    NONE,
    OPEN,
    /** Caved in, and passed by a detour through service galleries. */
    COLLAPSED,
    /** Shut by a blast gate, which ends a sector. */
    GATED;

    public boolean canBeWalked()
    {
        return this == OPEN || this == COLLAPSED;
    }
}
