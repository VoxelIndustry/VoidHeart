package net.voxelindustry.voidheart.common.world.underway;

/** A piece of a fort's security that still works. */
public enum UnderwayUnitKind
{
    /** Watches from a wall and raises the alarm. */
    SENTINEL_EYE,
    /** A gun that drops out of a ceiling housing. */
    TURRET_CUPOLA,
    /** A gun behind a guard room's slit, covering the gallery in front of it. */
    EMBRASURE_GUN,
    /** A walking machine of copper and brass. */
    AUTOMATON,
    /** A beam at ankle height across a passage. */
    TRIPWIRE,
    /** A mortar that rises out of a platform's floor. */
    TURRET_WELL,
    /** A lamp that sweeps a hall and tells the rest of the section whom it caught. */
    SEARCHLIGHT,
    /** A walking machine behind a shield. */
    MANTLET,
    /** An armoured wagon on the forts' narrow track. */
    RAM_CART,
    /** A small crawler that bursts, kept folded in a duct. */
    SAPPER,
    /** A crawler that rebuilds wrecks. */
    FITTER;

    /** @return whether it stays where it was built, mounted on the fort itself */
    public boolean isMounted()
    {
        return switch (this)
        {
            case SENTINEL_EYE, TURRET_CUPOLA, EMBRASURE_GUN, TRIPWIRE, TURRET_WELL, SEARCHLIGHT -> true;
            default -> false;
        };
    }

    /** @return whether it needs the block over its own clear as well */
    public boolean isTall()
    {
        return this == AUTOMATON || this == MANTLET;
    }
}
