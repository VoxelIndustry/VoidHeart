package net.voxelindustry.voidheart.common.energy.pearl;

/**
 * Numbers owned by the Collapsing Rubrum Pearls. Rows from the user's brief are marked so, the rest are guesses to tune from play.
 */
public final class PearlBalance
{
    /**
     * The brief: hours a pearl takes alone, fully fed.
     */
    public static final int  ALONE_HOURS  = 4;
    public static final long GROWTH_TICKS = ALONE_HOURS * 60L * 60L * 20L;

    /**
     * The brief: up to this many pearls reinforce each other, and that many grow twice as fast, two hours each.
     */
    public static final int    MAX_CHOIR  = 6;
    public static final double FULL_BOOST = 2;

    /**
     * Guess: the farthest two pearls may hang apart, centre to centre, and still reinforce each other.
     */
    public static final double REACH = 4;

    /**
     * Guess: void energy a growing pearl draws a tick from the field around it, whatever its choir. Grouped pearls grow faster on the same draw, so
     * each costs half as much at six.
     */
    public static final long VOID_DRAW = 40;

    /**
     * Guess: Fiery a growing pearl takes in a tick. A collector gets 1,000 from a lava source, so a lone pearl drinks about 72 sources and one of six
     * about 36.
     */
    public static final double FIERY_PER_TICK = 0.25;

    /**
     * Guess: Fiery a pearl holds, about a minute's worth.
     */
    public static final int FIERY_TANK = 1_000;

    /**
     * Guess: pairs a Contravoid reactor cancels for each seed it throws off. At its fullest burn of 2 pairs a tick that is one an hour, and one in
     * four hours at its least.
     */
    public static final double SEED_PAIRS = 2 * 60 * 60 * 20;

    private PearlBalance()
    {
    }
}
