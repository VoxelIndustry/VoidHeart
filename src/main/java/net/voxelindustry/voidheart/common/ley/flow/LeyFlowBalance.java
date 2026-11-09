package net.voxelindustry.voidheart.common.ley.flow;

/**
 * W3's numbers, from docs/plans/energy-numbers.md (N0, approved 2026-11-09). Taint rates there are per minute.
 * Values marked GUESS are N0 guesses to tune from play; values marked W3 aren't in N0 and were set here.
 */
public final class LeyFlowBalance
{
    /**
     * GUESS. Flow × (1 + TIDE × cos(2π × moon phase / 8)): +25% at full moon, −25% at new moon.
     */
    public static final double TIDE = 0.25;
    /**
     * GUESS. Flow multiplier on a node whose dormant Old Anchor was awakened.
     */
    public static final double AWAKENED = 1.5;
    /**
     * GUESS. Flow × (1 − TAINT_FLOW_CUT × taint / 100).
     */
    public static final double TAINT_FLOW_CUT = 0.5;

    /**
     * Sim. Gain = TAINT_GAIN_FULL × ((f − TAINT_LIGHT) / (1 − TAINT_LIGHT))² per minute, 0 when f ≤ TAINT_LIGHT,
     * where f is the ley converted on a node and its lines over the node's current flow.
     */
    public static final double TAINT_GAIN_FULL = 2.0;
    public static final double TAINT_LIGHT = 0.25;
    /**
     * Sim. Healing per minute is TAINT_HEAL_FLOOR + taint / TAINT_HEAL_DIVISOR, and never stops.
     */
    public static final double TAINT_HEAL_FLOOR = 0.05;
    public static final double TAINT_HEAL_DIVISOR = 120;
    /**
     * Sim. Each downstream neighbour gains this share of a node's gain, hop by hop, until a hop gains under SPREAD_STOP per minute.
     */
    public static final double TAINT_DOWNSTREAM = 0.4;
    public static final double SPREAD_STOP = 0.01;
    /**
     * W3. Caps the spread of a sudden gain, such as a reactor failure, which the per minute stop alone would carry far.
     * At the steadiest gain of 2 per minute the stop already ends the spread after 5 hops.
     */
    public static final int SPREAD_MAX_HOPS = 5;

    /**
     * GUESS. The taint at which each stage begins.
     */
    public static final float TAINTED_FROM = 20;
    public static final float FESTERING_FROM = 50;
    public static final float BREACHED_FROM = 80;
    public static final float MAX_TAINT = 100;

    /**
     * GUESS. Taint gains on a node are multiplied by SALT_SLOW while salt lies within SALT_RANGE blocks of it.
     */
    public static final double SALT_SLOW = 0.5;
    public static final int SALT_RANGE = 8;
    /**
     * W3. Salt is looked for this far above and below the ground at the node.
     */
    public static final int SALT_HEIGHT = 8;
    /**
     * W3. How long a node's salt check holds before the blocks are scanned again.
     */
    public static final int SALT_CHECK_TICKS = 600;

    /**
     * W3. Taint, healing and spread are applied once per window, and clients are told what changed.
     */
    public static final int WINDOW_TICKS = 20;
    /**
     * W3. A claim keeps its share this long after its last call, so claimants that claim less than every tick still hold their place.
     */
    public static final int CLAIM_HOLD_TICKS = 40;
    /**
     * W3. A change of taint smaller than this isn't sent to clients until it adds up.
     */
    public static final float SYNC_STEP = 0.5F;

    private LeyFlowBalance()
    {
    }
}
