package net.voxelindustry.voidheart.common.energy.storage;

/**
 * W2's numbers, copied from docs/plans/energy-numbers.md (N0). Amounts are void energy, rates are per tick.
 */
public final class RubrumBalance
{
    public static final long SMALL_RUBRUM_CAPACITY  = 250_000;
    public static final long MEDIUM_RUBRUM_CAPACITY = 1_000_000;
    public static final long LARGE_RUBRUM_CAPACITY  = 4_000_000;

    public static final long SMALL_AZURIUM_RATE  = 64;
    public static final long MEDIUM_AZURIUM_RATE = 160;
    public static final long LARGE_AZURIUM_RATE  = 320;

    /** Guess: share of capacity a construct must stay above for its bud to grow. */
    public static final float BUD_THRESHOLD = 0.9F;
    /** Guess: minutes spent above the threshold to grow one bud. */
    public static final int   BUD_MINUTES   = 120;
    public static final long  BUD_TICKS     = BUD_MINUTES * 60L * 20L;
    /** Guess: energy spent when the bud becomes a bundle. */
    public static final long  BUD_COST      = 250_000;

    /** Guess. */
    public static final long CELL_CAPACITY    = 400_000;
    /** Guess: how fast a cell socket charges or discharges its cell. */
    public static final long CELL_SOCKET_RATE = 64;

    private RubrumBalance()
    {
    }
}
