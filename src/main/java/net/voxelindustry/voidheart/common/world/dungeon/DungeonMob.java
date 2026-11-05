package net.voxelindustry.voidheart.common.world.dungeon;

/**
 * The mobs a dungeon can hold, without touching their entity types so that plans stay pure.
 * Clearance is in cells: how far it needs free to each side of its own, and how high.
 */
public enum DungeonMob
{
    VOID_SKELETON(Family.GENERIC, 1, 10, 0, 2, 1, false),
    VOID_GHOUL(Family.GENERIC, 1, 10, 0, 2, 1, false),
    WRAITH(Family.GENERIC, 2, 5, 0, 2, 1, false),

    CRAB_SMALL(Family.PEST, 1, 8, 0, 1, 3, true),
    CRAB_MEDIUM(Family.PEST, 1, 8, 0, 1, 1, true),
    CRAB_LARGE(Family.PEST, 1, 3, 1, 2, 1, false),
    BARNACLE(Family.PEST, 1, 5, 0, 1, 1, true),

    FALLEN_NOVICE(Family.FALLEN, 1, 14, 0, 2, 1, false),
    FALLEN_ADEPT(Family.FALLEN, 3, 6, 0, 2, 1, false),
    FALLEN_ELDER(Family.FALLEN, 4, 3, 1, 3, 1, false),

    EYE_MAW(Family.EYE, 1, 5, 0, 1, 6, false, true),
    ARCANE_EYE(Family.EYE, 2, 14, 0, 1, 1, false, true),
    LARGE_EYE_MAW(Family.EYE, 3, 3, 1, 3, 1, false, true);

    public enum Family
    {
        GENERIC,
        PEST,
        FALLEN,
        EYE;

        public boolean livesIn(DungeonTheme theme)
        {
            return switch (this)
            {
                case GENERIC -> theme != DungeonTheme.DEPTHS;
                case PEST -> theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS || theme == DungeonTheme.DEPTHS;
                case FALLEN -> theme == DungeonTheme.TEMPLE || theme == DungeonTheme.CATHEDRAL;
                case EYE -> theme == DungeonTheme.DEPTHS;
            };
        }
    }

    public final Family family;
    public final int minDifficulty;
    public final int weight;
    public final int halfWidth;
    public final int height;
    public final int groupSize;
    public final boolean clings;
    public final boolean flies;

    DungeonMob(Family family, int minDifficulty, int weight, int halfWidth, int height, int groupSize, boolean clings)
    {
        this(family, minDifficulty, weight, halfWidth, height, groupSize, clings, false);
    }

    DungeonMob(Family family, int minDifficulty, int weight, int halfWidth, int height, int groupSize, boolean clings, boolean flies)
    {
        this.family = family;
        this.minDifficulty = minDifficulty;
        this.weight = weight;
        this.halfWidth = halfWidth;
        this.height = height;
        this.groupSize = groupSize;
        this.clings = clings;
        this.flies = flies;
    }
}
