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

    /** How long right click must be held before letting go throws the spear, as a trident's, and the wait after a throw before the next. */
    public static final int   SPEAR_CHARGE_TICKS    = 10;
    public static final int   SPEAR_COOLDOWN_TICKS  = 120;
    /** The thrown spear's speed in blocks a tick, and what it hits for when it strikes a mob. */
    public static final float SPEAR_THROW_SPEED     = 2.5F;
    public static final float SPEAR_THROW_DAMAGE    = 8;
    /** A throw that hits nothing opens its hole wherever it is after this long. */
    public static final int   SPEAR_FLIGHT_TICKS    = 40;
    /** Blocks a tick it flies back to its thrower at once the hole has closed. */
    public static final double SPEAR_RETURN_SPEED   = 0.9;

    /** The hole's radius at full size, and how far off the surface the spear struck its middle hangs, so it stands clear of it. */
    public static final double HOLE_RADIUS          = 1;
    public static final double HOLE_STANDOFF        = 0.9;
    /** Its whole life, opening, and closing at the end of it. */
    public static final int   HOLE_TICKS            = 60;
    public static final int   HOLE_OPEN_TICKS       = 4;
    public static final int   HOLE_CLOSE_TICKS      = 4;
    /** It crushes what is inside this often, for this much, through armor. */
    public static final int   HOLE_CRUSH_EVERY      = 10;
    public static final float HOLE_CRUSH_DAMAGE     = 4;
    /** How much it pinches at each crush, and how long it takes to open back out. */
    public static final float HOLE_PINCH            = 0.15F;
    public static final float HOLE_PINCH_TICKS      = 4;
    /** How far it reaches, the speed it gives a mob at the edge of that reach, twice that at its middle, and how much of a mob's own speed is kept. */
    public static final double HOLE_PULL_RANGE      = 6;
    public static final double HOLE_PULL            = 0.22;
    public static final double HOLE_DRAG            = 0.5;
    /** How much of its speed a mob inside the hole keeps, so it stays caught. */
    public static final double HOLE_HOLD            = 0.3;

    private BlightBalance()
    {
    }

    public static float unseamingDamage(float maxHealth)
    {
        return UNSEAMING_DAMAGE + UNSEAMING_HEALTH_SHARE * maxHealth;
    }
}
