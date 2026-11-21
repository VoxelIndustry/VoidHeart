package net.voxelindustry.voidheart.common.energy.storage;

/**
 * W2's numbers, copied from docs/plans/energy-numbers.md (N0). Amounts are void energy, rates are per tick.
 */
public final class RubrumBalance
{
    public static final long SMALL_RUBRUM_CAPACITY  = 250_000;
    public static final long MEDIUM_RUBRUM_CAPACITY = 1_000_000;
    public static final long LARGE_RUBRUM_CAPACITY  = 4_000_000;

    /** Fouled rubrum holds this many times its size. */
    public static final long FOULED_CAPACITY_FACTOR = 2;

    /** A construct holding Fouled rubrum leaks a seep this far, this strong at its core, while over half full. Guesses. */
    public static final float FOULED_LEAK_RADIUS   = 4;
    public static final float FOULED_LEAK_STRENGTH = 0.3F;
    public static final float FOULED_LEAK_FROM     = 0.5F;

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
    /** Guess: how fast a socket wall charges or discharges each of its four cells. */
    public static final long SOCKET_WALL_RATE = CELL_SOCKET_RATE;

    /** Searing azurium gives out, and Still takes in, this many times its rate. */
    public static final long HABIT_RATE_FACTOR = 2;

    /** Guess: a Searing cell let go empties its whole charge over this many ticks, about 5 seconds. */
    public static final long SEARING_BURST_TICKS = 100;
    public static final long SEARING_BURST_RATE  = CELL_CAPACITY / SEARING_BURST_TICKS;

    /** Guesses: a Wrung cell drinks harm done to living things this near, this much per point of damage, banked and taken in at this rate. */
    public static final double WRUNG_RANGE      = 8;
    public static final long   WRUNG_PER_DAMAGE = 400;
    public static final long   WRUNG_RATE       = 64;
    /** The most harm a Wrung cell banks ahead of taking it in, about 10 seconds' worth. */
    public static final long   WRUNG_BANK       = WRUNG_RATE * 200;

    private RubrumBalance()
    {
    }
}
