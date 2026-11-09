package net.voxelindustry.voidheart.common.energy.reactor;

/**
 * Numbers owned by the Contravoid reactor, copied from docs/plans/energy-numbers.md (N0). A pair is one Contravoid cancelled against one essence.
 * Rows N0 doesn't give are marked as W10's own guesses.
 */
public final class ReactorBalance
{
    /**
     * Sim: void energy one pair gives with Fiery, scaled by the essence's factor below.
     */
    public static final double PAIR_ENERGY = 200;

    /**
     * Sim: factors on {@link #PAIR_ENERGY} for each essence cancelled against Contravoid.
     */
    public static final double STILLNESS_FACTOR = 0.8;
    public static final double FIERY_FACTOR     = 1.0;
    public static final double SUFFERING_FACTOR = 1.25;

    /**
     * Sim: most pairs burned a tick, so 400 VE/t with Fiery (320 Stillness, 500 Suffering), which needs 3 large pylons.
     */
    public static final double MAX_PAIRS = 2;

    /**
     * Sim: the least it burns while lit, as a share of {@link #MAX_PAIRS}, whatever the network takes.
     */
    public static final double MIN_BURN  = 0.25;
    public static final double MIN_PAIRS = MAX_PAIRS * MIN_BURN;

    /**
     * Guess: void energy drawn to light it.
     */
    public static final long IGNITION = 200_000;

    /**
     * W10's guess: most void energy asked a tick while lighting. The pylons covering the core usually limit it first.
     */
    public static final long IGNITION_DRAW = 2_000;

    /**
     * The plan: the warned countdown after a mistake, in ticks.
     */
    public static final int COUNTDOWN = 45 * 20;

    /**
     * From energy-looks.md: how long the light takes to collapse into the point once the countdown runs out, before the wound opens.
     */
    public static final int COLLAPSE = 10;

    /**
     * Guess: taint added at once to the nearest node on failure.
     */
    public static final float FAILURE_TAINT = 60;

    /**
     * W10's guess: what each intake holds.
     */
    public static final int INTAKE_TANK = 4_000;

    /**
     * W10's guess: what a dry intake must hold again before the warning ends, so a trickle doesn't flick it on and off every tick.
     */
    public static final int RECOVER_FUEL = 20;

    private ReactorBalance()
    {
    }
}
