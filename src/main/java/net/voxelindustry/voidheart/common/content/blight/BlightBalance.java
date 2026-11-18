package net.voxelindustry.voidheart.common.content.blight;

/**
 * Numbers of the void blights, guesses to be tuned from play.
 */
public final class BlightBalance
{
    /** The rapier hits for this on top of the player's 1, a little below a netherite sword. */
    public static final float RAPIER_DAMAGE         = 5;
    /** Added to the player's 4 attacks a second, for 2 a second. */
    public static final float RAPIER_SPEED          = -2;
    public static final float RAPIER_REACH          = 0.5F;

    public static final float UNSEAMING_DAMAGE      = 24;
    /** Share of the target's maximum health added to the Unseaming. */
    public static final float UNSEAMING_HEALTH_SHARE = 0.12F;
    /** How long the target stays folded out of the world before it snaps back and takes the Unseaming. */
    public static final int   UNSEAMING_FOLD_TICKS  = 10;

    /** The scythe hits for this on top of the player's 1, slower than a sword. */
    public static final float SCYTHE_DAMAGE         = 9;
    /** Added to the player's 4 attacks a second, for 1 a second. */
    public static final float SCYTHE_SPEED          = -3;
    public static final float SCYTHE_REACH          = 0.5F;

    /** A slain mob must have had at least this much health to rise. */
    public static final float WRAITH_SOURCE_HEALTH  = 10;
    public static final float WRAITH_HEALTH         = 20;
    public static final float WRAITH_DAMAGE         = 5;
    public static final int   WRAITH_ATTACK_TICKS   = 20;
    /** How long a wraith fights before it sinks back, and how many one wielder keeps. */
    public static final int   WRAITH_LIFE_TICKS     = 400;
    public static final int   WRAITH_MAX            = 3;
    /** Blocks a tick it glides at, and how far from its wielder it looks for what to fight. */
    public static final double WRAITH_SPEED         = 0.32;
    public static final double WRAITH_REACH         = 24;
    /** The ink spreading under the corpse before the wraith comes up, its rise out of the ground, and its sinking back. */
    public static final int   WRAITH_STAIN_TICKS    = 5;
    public static final int   WRAITH_RISE_TICKS     = 15;
    public static final int   WRAITH_SINK_TICKS     = 15;

    private BlightBalance()
    {
    }

    public static float unseamingDamage(float maxHealth)
    {
        return UNSEAMING_DAMAGE + UNSEAMING_HEALTH_SHARE * maxHealth;
    }
}
