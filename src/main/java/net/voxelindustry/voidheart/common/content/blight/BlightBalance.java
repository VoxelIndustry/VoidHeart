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
    /** A hit must be charged at least this much to puncture. */
    public static final float RAPIER_CHARGE         = 0.9F;

    public static final float UNSEAMING_DAMAGE      = 24;
    /** Share of the target's maximum health added to the Unseaming. */
    public static final float UNSEAMING_HEALTH_SHARE = 0.12F;
    /** How long the target stays folded out of the world before it snaps back and takes the Unseaming. */
    public static final int   UNSEAMING_FOLD_TICKS  = 10;

    private BlightBalance()
    {
    }

    public static float unseamingDamage(float maxHealth)
    {
        return UNSEAMING_DAMAGE + UNSEAMING_HEALTH_SHARE * maxHealth;
    }
}
