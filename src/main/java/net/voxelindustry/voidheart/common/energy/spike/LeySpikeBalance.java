package net.voxelindustry.voidheart.common.energy.spike;

/**
 * W8 numbers, from docs/plans/energy-numbers.md (N0). Rows N0 marks sim are balanced against the taint curve, the others are guesses to tune from play.
 */
public final class LeySpikeBalance
{
    /**
     * Void energy per tick for each ley converted (sim).
     */
    public static final int VE_PER_LEY = 8;
    /**
     * The most ley one spike converts, so 96 VE/t: one spike never runs an advanced base (sim).
     */
    public static final double MAX_LEY = 12;
    /**
     * The claim settings, as shares of the current flow where the spike stands. A share rather than a fixed amount, since taint cuts flow and a fixed
     * claim would run away (N0, risk 4) (sim).
     */
    public static final double[] SHARES = {0.25, 0.5, 0.75, 1};
    /**
     * New spikes bleed lightly, which never taints.
     */
    public static final int DEFAULT_SETTING = 0;
    /**
     * Contravoid per tick for each ley converted on a node at taint 100, scaled by taint / 100 (sim).
     */
    public static final double CONTRAVOID_PER_LEY = 0.085;
    /**
     * How far Contravoid reaches collectors around the spike (guess, a little beyond Suffering's 6).
     */
    public static final int CONTRAVOID_RANGE = 8;
    /**
     * Contravoid is handed to collectors once a second rather than each tick (guess).
     */
    public static final int EMIT_TICKS = 20;

    private LeySpikeBalance()
    {
    }
}
