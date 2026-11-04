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
    FALLEN_ELDER(Family.FALLEN, 4, 3, 1, 3, 1, false);

    public enum Family
    {
        GENERIC,
        PEST,
        FALLEN;

        public boolean livesIn(DungeonTheme theme)
        {
            return switch (this)
            {
                case GENERIC -> true;
                case PEST -> theme == DungeonTheme.FOREST || theme == DungeonTheme.MYCOSIS;
                case FALLEN -> theme == DungeonTheme.TEMPLE || theme == DungeonTheme.CATHEDRAL;
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

    DungeonMob(Family family, int minDifficulty, int weight, int halfWidth, int height, int groupSize, boolean clings)
    {
        this.family = family;
        this.minDifficulty = minDifficulty;
        this.weight = weight;
        this.halfWidth = halfWidth;
        this.height = height;
        this.groupSize = groupSize;
        this.clings = clings;
    }
}
