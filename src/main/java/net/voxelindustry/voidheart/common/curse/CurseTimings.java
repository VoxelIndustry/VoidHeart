package net.voxelindustry.voidheart.common.curse;

/**
 * How long each curse runs, shared so that the server and the afflicted client let go of it at the same moment.
 */
public final class CurseTimings
{
    public static final int ECHO_TICKS  = 90;
    public static final int OMEN_TICKS  = 300;
    public static final int VIGIL_TICKS = 260;

    private CurseTimings()
    {
    }

    public static int duration(CurseKind kind, long seed)
    {
        return switch (kind)
        {
            case ECHO -> ECHO_TICKS;
            case OMEN -> OMEN_TICKS;
            case VIGIL -> VIGIL_TICKS;
            case RAPTURE -> RaptureLift.totalTicks(seed);
        };
    }
}
