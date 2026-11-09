package net.voxelindustry.voidheart.common.energy.machine;

/**
 * How well a machine's draw was met over the last second.
 */
public enum MachinePower
{
    /** Drawing nothing, because it has nothing to do. */
    IDLE,
    POWERED,
    /** Getting some of what it needs, so it runs slower. */
    WEAK,
    /** Getting nothing, so it pauses and dims. */
    UNPOWERED;

    public static MachinePower of(long wanted, long got)
    {
        if (wanted <= 0)
            return IDLE;
        if (got >= wanted)
            return POWERED;
        return got <= 0 ? UNPOWERED : WEAK;
    }

    public static MachinePower byOrdinal(int ordinal)
    {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
    }

    public boolean isStarved()
    {
        return this == WEAK || this == UNPOWERED;
    }
}
