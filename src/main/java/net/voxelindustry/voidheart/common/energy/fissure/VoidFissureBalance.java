package net.voxelindustry.voidheart.common.energy.fissure;

/**
 * Numbers for W9 Void Fissure, from the W9 rows of docs/plans/energy-numbers.md (N0). Rows N0 marks as guesses are still meant to be tuned from play.
 */
public final class VoidFissureBalance
{
    public static final int MAX_WIDTH = 5;

    /**
     * VE/t offered at widths 1 to 5. Sim: width 5 can't run an advanced base.
     */
    static final long[] OUTPUT = { 16, 32, 48, 64, 88 };

    /**
     * Seep radius in blocks at widths 1 to 5. Guess.
     */
    static final float[] SEEP_RADIUS = { 6, 10, 16, 24, 32 };

    /**
     * Blocks a minute the seep spreads until it reaches its width's radius. Guess.
     */
    public static final float SEEP_GROWTH_PER_MINUTE = 1;

    /**
     * Inside the seep, every rise of corruption is multiplied by 1 + this × strength. Guess.
     */
    public static final float CORRUPTION_BOOST = 1.5F;

    /**
     * Points a minute the seep raises corruption on its own at strength 1, before modifiers. The seep's own multiplier then applies, so a player at the
     * heart of a seep gains 1 point a minute, and wards slow it. Guess, from the user's ruling for a slow rise.
     */
    public static final float SEEP_RISE_PER_MINUTE = 1 / (1 + CORRUPTION_BOOST);

    /**
     * How far from a ley line a fissure still taints the line's node. Sim: half of all bases are within 47 blocks of a line.
     */
    public static final int LINE_REACH = 48;

    /**
     * Taint a minute per width on a fissure sitting on the line, falling linearly to 0 at {@link #LINE_REACH}. Guess.
     */
    public static final float TAINT_PER_WIDTH = 0.15F;

    /**
     * One creature tries to come through every this many seconds divided by the width. Guess.
     */
    public static final float SPAWN_SECONDS = 90;

    /**
     * At most this many creatures per width alive inside the seep. Guess.
     */
    public static final int ALIVE_PER_WIDTH = 2;

    /**
     * Contravoid to close a fissure, per width. A wound of size 4 costs 8,000. Guess.
     */
    public static final int CLOSE_COST_PER_WIDTH = 2000;

    /**
     * The size of the wound a failed reactor leaves, and of one opened by command with no size: it seeps like a width 4 fissure. Guess.
     */
    public static final float WOUND_SIZE = 4;

    /**
     * Azurium bundle sizes (1 small, 2 medium, 3 large) that widen a fissure to widths 2 to 5. Guess, from the user's ruling: small, medium, then large
     * for the later widths.
     */
    static final int[] WIDEN_BUNDLE = { 1, 1, 2, 3 };

    /**
     * Ticks a widening takes to tear open, and a closing to draw shut, from energy-looks.md.
     */
    public static final int TEAR_TICKS  = 20;
    public static final int CLOSE_TICKS = 40;

    private VoidFissureBalance()
    {
    }
}
