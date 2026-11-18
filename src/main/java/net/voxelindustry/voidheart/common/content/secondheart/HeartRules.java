package net.voxelindustry.voidheart.common.content.secondheart;

/**
 * The second heart's pure rules: what saves a player from lethal damage, how its life comes back, and the timeline of a dissolve and reform.
 */
public final class HeartRules
{
    /** Twenty minutes of the player's own online ticks. */
    public static final int RECHARGE_TICKS = 20 * 60 * 20;

    public static final int DISSOLVE_TICKS = 30;
    public static final int REFORM_TICKS   = 30;
    /** Unhurtable after the burst, so the player isn't killed again while standing up. */
    public static final int GRACE_TICKS    = 60;
    public static final int BURST_TICK     = DISSOLVE_TICKS + REFORM_TICKS;
    public static final int SEQUENCE_TICKS = BURST_TICK + GRACE_TICKS;

    public static final double BURST_RADIUS   = 6;
    /** Knockback strength right against the player, falling to nothing at the burst's edge, before knockback resistance. */
    public static final double BURST_STRENGTH = 2.4;
    public static final double BURST_MIN      = 0.4;
    public static final int    BURST_STAGGER  = 30;

    private HeartRules()
    {
    }

    public enum Verdict
    {
        /** Nothing saves the player. */
        DIE,
        /** A held Totem of Undying goes first, and the heart keeps its charge. */
        TOTEM,
        /** The heart fires. */
        HEART,
        /** Already dissolving or reforming, so the hit can't land. */
        HOLD
    }

    /**
     * @param genericKill    {@code /kill} and its kin, which always kill
     * @param totemWouldSave a held death protection item, and damage it can stop
     */
    public static Verdict onLethal(boolean owned, int recharge, boolean inSequence, boolean totemWouldSave, boolean genericKill)
    {
        if (genericKill)
            return Verdict.DIE;
        if (inSequence)
            return Verdict.HOLD;
        if (totemWouldSave)
            return Verdict.TOTEM;
        if (owned && recharge <= 0)
            return Verdict.HEART;
        return Verdict.DIE;
    }

    /**
     * @return the charge left after one online tick
     */
    public static int recharge(int left)
    {
        return Math.max(0, left - 1);
    }

    /**
     * @return whether this tick brings the life back, so the ready heartbeat plays once
     */
    public static boolean comesBack(int before)
    {
        return before == 1;
    }

    public enum Phase
    {
        DISSOLVE,
        REFORM,
        GRACE,
        DONE
    }

    public static Phase phase(int elapsed)
    {
        if (elapsed < DISSOLVE_TICKS)
            return Phase.DISSOLVE;
        if (elapsed < BURST_TICK)
            return Phase.REFORM;
        if (elapsed < SEQUENCE_TICKS)
            return Phase.GRACE;
        return Phase.DONE;
    }

    /**
     * @return how much of the body is gone, 0 whole and 1 gone, through the dissolve and back through the reform
     */
    public static float gone(float elapsed)
    {
        if (elapsed <= 0)
            return 0;
        if (elapsed < DISSOLVE_TICKS)
            return elapsed / DISSOLVE_TICKS;
        if (elapsed < BURST_TICK)
            return 1 - (elapsed - DISSOLVE_TICKS) / REFORM_TICKS;
        return 0;
    }

    /**
     * @return knockback strength for something this far from the reformed player, or 0 outside the burst
     */
    public static double burstStrength(double distance)
    {
        if (distance >= BURST_RADIUS)
            return 0;
        double t = 1 - Math.max(0, distance) / BURST_RADIUS;
        return BURST_MIN + (BURST_STRENGTH - BURST_MIN) * t;
    }
}
