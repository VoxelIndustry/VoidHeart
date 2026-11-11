package net.voxelindustry.voidheart.common.content.kleinstar;

/**
 * Numbers of the Klein Star that both sides share. Damage, radius, cooldown and the Contravoid a cast burns are in the config. All guesses until played.
 */
public final class KleinStarBalance
{
    /** Ticks from nothing to an eighth of its size. */
    public static final int    APPEAR_TICKS     = 3;
    /** Ticks it hangs in place growing to the size of a block before it leaves. */
    public static final int    GROW_TICKS       = 40;
    public static final float  APPEAR_SIZE      = 0.125F;
    public static final float  FULL_SIZE        = 1;
    /** Blocks a tick along its way to burst: it leaves slowly, gains this much speed each tick, and tops out. */
    public static final double FLIGHT_START     = 0.5;
    public static final double FLIGHT_GAIN      = 0.3;
    public static final double FLIGHT_SPEED     = 2.6;
    /** Still flying after this long, it bursts where it is. */
    public static final int    MAX_FLIGHT_TICKS = 60;
    /** How far from where it grew it looks for something to burst on, and how far it flies along its caster's gaze when it finds nothing. */
    public static final double SEARCH_RANGE     = 24;
    /**
     * Where it grows, just past the end of the staff its caster holds: from their feet, to the side of the hand that holds it, up by this share of
     * their height, and ahead the way their body faces.
     */
    public static final double TIP_ASIDE        = 0.25;
    public static final double TIP_HEIGHT       = 1.34;
    public static final double TIP_AHEAD        = 0.35;
    /** A burst against a wall happens this far back from it. */
    public static final double BURST_BACKOFF    = 0.3;
    /** Share of the damage dealt at the very edge of the blast. */
    public static final float  EDGE_DAMAGE      = 0.5F;

    /** Contravoid a Klein Star holds. */
    public static final int    CAPACITY         = 4000;

    public static final int    RADIANT_TICKS    = 8 * 20;
    public static final int    RADIANT_INTERVAL = 20;
    public static final float  RADIANT_DAMAGE   = 2;

    /** How long lit blocks keep burning what comes near, and how near that is. */
    public static final int    COVER_TICKS      = 10 * 20;
    public static final double COVER_REACH      = 1;
    public static final int    COVER_INTERVAL   = 20;

    /** Players this close see the burst and what it leaves. */
    public static final double SEEN_RANGE       = 96;

    private KleinStarBalance()
    {
    }

    /**
     * @return how wide the star is, in blocks, this many ticks after it appeared
     */
    public static float size(float ticks)
    {
        if (ticks <= 0)
            return 0;
        if (ticks < APPEAR_TICKS)
            return APPEAR_SIZE * ticks / APPEAR_TICKS;
        if (ticks >= GROW_TICKS)
            return FULL_SIZE;
        float grown = (ticks - APPEAR_TICKS) / (GROW_TICKS - APPEAR_TICKS);
        return APPEAR_SIZE + (FULL_SIZE - APPEAR_SIZE) * grown;
    }

    /**
     * @return how many blocks it has flown this many ticks after it left
     */
    public static double flown(double ticks)
    {
        if (ticks <= 0)
            return 0;
        double gaining = (FLIGHT_SPEED - FLIGHT_START) / FLIGHT_GAIN;
        if (ticks <= gaining)
            return FLIGHT_START * ticks + FLIGHT_GAIN * ticks * ticks / 2;
        return FLIGHT_START * gaining + FLIGHT_GAIN * gaining * gaining / 2 + FLIGHT_SPEED * (ticks - gaining);
    }

    /**
     * @return the damage of a blast at this distance from its middle
     */
    public static float damageAt(float damage, double distance, double radius)
    {
        if (distance > radius)
            return 0;
        return (float) (damage * (1 - (1 - EDGE_DAMAGE) * Math.max(distance, 0) / radius));
    }
}
