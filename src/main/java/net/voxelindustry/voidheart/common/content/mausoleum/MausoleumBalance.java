package net.voxelindustry.voidheart.common.content.mausoleum;

/**
 * Every number of the mausoleum's fight, in one place to tune. Set at the Underway's level or a little under it, below the sanctum, for players in
 * netherite: the rider is slow and turns slowly, so its sweeps are dodged by keeping to its flank, and its danger is in what comes while it is
 * stunned. All guesses until a playtest.
 */
public final class MausoleumBalance
{
    private MausoleumBalance()
    {
    }

    // The Dullahan
    /** Nine shares of 36: about 75 hits of an unenchanted netherite sword through its armour. */
    public static final double RIDER_HEALTH = 324;
    public static final double RIDER_ARMOR = 10;
    public static final double RIDER_TOUGHNESS = 4;
    public static final double RIDER_SPEED = 0.26;
    public static final double RIDER_STEP = 1.1;
    /** How near a player must come for it to wake, and how far it keeps fighting. */
    public static final double RIDER_WAKE_RANGE = 22;
    public static final double RIDER_RANGE = 44;
    /** Degrees it turns in a tick, and while winding up a sweep. */
    public static final float RIDER_TURN = 5;
    public static final float RIDER_TURN_WINDUP = 1.5F;

    // The sweep of its scythe
    public static final double SWEEP_START = 4.2;
    /** From the middle of its body, in blocks across the ground. */
    public static final double SWEEP_REACH = 5.2;
    public static final double SWEEP_REACH_UP = 3.4;
    /** The whole width of the arc in front of it, in degrees. */
    public static final float SWEEP_ARC = 170;
    public static final int SWEEP_WINDUP = 26;
    public static final int SWEEP_RECOVER = 18;
    public static final int SWEEP_COOLDOWN = 28;
    public static final float SWEEP_DAMAGE = 18;
    public static final double SWEEP_KNOCKBACK = 1.3;

    /** How long a flame leaving the crown holds it still, unless its wisp is put out sooner. */
    public static final int STUN_TICKS = 600;

    // Maleficent wisps
    public static final double WISP_HEALTH = 14;
    public static final float WISP_RAM_DAMAGE = 8;
    /** How long a ram leaves its victim burning. */
    public static final int WISP_FIRE_TICKS = 100;
    public static final int WISP_BIRTH_TICKS = 24;
    public static final double WISP_ORBIT_RADIUS = 4;
    public static final double WISP_ORBIT_HEIGHT = 1.4;
    /** Radians round its prey in a tick. */
    public static final double WISP_ORBIT_SPEED = 0.11;
    public static final int WISP_ORBIT_MIN = 46;
    public static final int WISP_ORBIT_SPREAD = 34;
    public static final int WISP_WINDUP = 12;
    public static final double WISP_CHARGE_SPEED = 0.85;
    public static final int WISP_CHARGE_TICKS = 20;
    public static final double WISP_RANGE = 48;
}
