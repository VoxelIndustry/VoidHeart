package net.voxelindustry.voidheart.common.content.research;

/** What a repository can be seen doing. Only ever shown, the knowledge itself does not depend on it. */
public enum KnowledgeRepositoryStatus
{
    DORMANT,
    GATHERING,
    FULL,
    RESEARCHING,
    STARVED;

    private static final double DRY = 0.01;

    public static KnowledgeRepositoryStatus of(boolean bound, boolean researching, double knowledge, double cap)
    {
        if (!bound)
            return DORMANT;
        if (researching)
            return knowledge < DRY ? STARVED : RESEARCHING;
        return knowledge >= cap ? FULL : GATHERING;
    }

    public boolean drawsFromColumns()
    {
        return this == GATHERING || this == STARVED;
    }
}
