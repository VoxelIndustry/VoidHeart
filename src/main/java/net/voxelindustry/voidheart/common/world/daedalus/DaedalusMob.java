package net.voxelindustry.voidheart.common.world.daedalus;

/**
 * What a district can hold, without touching entity types so that plans stay pure.
 * Clearance is in blocks: how far it needs free to each side of its own column, and how high.
 */
public enum DaedalusMob
{
    VOID_SKELETON(0, 2, 1),
    VOID_GHOUL(0, 2, 1),
    WRAITH(0, 2, 1),
    // Always a pair
    WRAITH_SCARAB(0, 1, 2),
    STALKING_WRAITH(0, 3, 1),
    FALLEN_BISHOP(0, 3, 1),
    MALIGNANT_BOULDER(1, 3, 1),
    // Always a pair, both placed together
    LEGIONARY(0, 3, 2),
    CENTURION(0, 3, 1),
    // A block wide, so it can stand in a single column where a tiny room has no more
    KERNACIAN_DRAGOON(1, 3, 1, Placing.FLOOR, 0),

    FALLEN_NOVICE(0, 2, 1),
    FALLEN_ADEPT(0, 2, 1),
    FALLEN_ELDER(0, 3, 1),
    CRAB_SMALL(0, 1, 3),
    CRAB_MEDIUM(0, 1, 1),
    CRAB_LARGE(1, 2, 1),
    BARNACLE(0, 1, 2),
    EYE_MAW(0, 1, 6, Placing.AIR),
    ARCANE_EYE(0, 1, 1, Placing.AIR),
    BARNACLE_NEST(0, 1, 1, Placing.SURFACE);

    public enum Placing
    {
        FLOOR,
        AIR,
        // A block set against a wall, floor or ceiling rather than a mob
        SURFACE
    }

    public final int halfWidth;
    public final int height;
    public final int groupSize;
    public final Placing placing;
    // The least it can make do with when no spot has its full clearance
    public final int tightHalfWidth;

    DaedalusMob(int halfWidth, int height, int groupSize)
    {
        this(halfWidth, height, groupSize, Placing.FLOOR);
    }

    DaedalusMob(int halfWidth, int height, int groupSize, Placing placing)
    {
        this(halfWidth, height, groupSize, placing, halfWidth);
    }

    DaedalusMob(int halfWidth, int height, int groupSize, Placing placing, int tightHalfWidth)
    {
        this.tightHalfWidth = tightHalfWidth;
        this.halfWidth = halfWidth;
        this.height = height;
        this.groupSize = groupSize;
        this.placing = placing;
    }

    public boolean isMob()
    {
        return placing != Placing.SURFACE;
    }
}
