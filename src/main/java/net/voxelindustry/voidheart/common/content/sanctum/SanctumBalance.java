package net.voxelindustry.voidheart.common.content.sanctum;

/**
 * Every number of the sanctum's fight, in one place to tune. All guesses for now, set above the Underway's defences, for players in netherite
 * with Protection: the hits that matter go through armour, as plain damage barely dents it.
 */
public final class SanctumBalance
{
    private SanctumBalance()
    {
    }

    // The Vile Flesh Mass
    public static final double MASS_HEALTH = 560;
    public static final double MASS_ARMOR = 12;
    public static final double MASS_TOUGHNESS = 6;
    /** How near a player must come for it to wake, and how far it keeps fighting. */
    public static final double MASS_WAKE_RANGE = 22;
    public static final double MASS_RANGE = 34;
    /** Ticks between two of its own moves, whichever they are. */
    public static final int MASS_PAUSE_TICKS = 24;
    /** Below this share of health it hurries: tentacles come faster. */
    public static final float MASS_ENRAGED_BELOW = 0.5F;

    // Tentacle eruptions, under every player in reach and one more near the mass
    public static final int ERUPT_INTERVAL = 110;
    public static final int ERUPT_INTERVAL_ENRAGED = 70;
    public static final int ERUPT_MAX = 3;
    public static final int ERUPT_WARN_TICKS = 24;
    public static final float ERUPT_DAMAGE = 12;
    public static final double ERUPT_RADIUS = 1.15;
    public static final double ERUPT_LIFT = 0.85;

    // The sweep of the tentacles round it
    public static final double SWEEP_RANGE = 6;
    public static final int SWEEP_WINDUP = 16;
    public static final int SWEEP_RECOVER = 14;
    public static final int SWEEP_COOLDOWN = 70;
    public static final float SWEEP_DAMAGE = 26;
    public static final double SWEEP_KNOCKBACK = 1.6;

    // The grab, dragging a player to its maw
    public static final double GRAB_MIN = 5;
    public static final double GRAB_RANGE = 14;
    public static final int GRAB_COOLDOWN = 180;
    public static final int GRAB_REACH_TICKS = 8;
    public static final int GRAB_MAX_TICKS = 110;
    public static final double GRAB_PULL = 0.22;
    /** What a tentacle holding someone takes before it lets go. */
    public static final float GRAB_TENTACLE_HEALTH = 14;
    public static final float BITE_DAMAGE = 22;
    /** From the edge of its body. */
    public static final double BITE_REACH = 1.4;

    // Bile spat at whoever keeps away
    public static final double SPIT_MIN = 7;
    public static final int SPIT_WINDUP = 12;
    public static final int SPIT_COOLDOWN = 64;
    public static final float BILE_HIT_DAMAGE = 7;
    public static final double BILE_SPEED = 0.9;
    public static final int POOL_TICKS = 110;
    public static final double POOL_RADIUS = 1.7;
    public static final int POOL_INTERVAL = 10;
    public static final float POOL_DAMAGE = 2.5F;
    /** Durability each piece of armour loses with every bite of the pool. */
    public static final int POOL_ARMOR_WEAR = 3;

    // Lesser shoggoths
    public static final double SHOGGOTH_HEALTH = 44;
    public static final double SHOGGOTH_DAMAGE = 11;
    public static final double SHOGGOTH_SPEED = 0.27;
    public static final double SHOGGOTH_ARMOR = 6;
    public static final double SHOGGOTH_SMALL_HEALTH = 18;
    public static final double SHOGGOTH_SMALL_DAMAGE = 6;
    public static final double SHOGGOTH_SMALL_SPEED = 0.33;
    public static final float SHOGGOTH_SMALL_SCALE = 0.6F;
    public static final double LASH_REACH = 3.2;
    public static final int LASH_WINDUP = 7;
    public static final int LASH_COOLDOWN = 26;
    /** Below this share of health a shoggoth goes back to feed the mass, if it is near. */
    public static final float FEED_BELOW = 0.4F;
    public static final double FEED_RANGE = 40;
    /** What feeding gives back to the mass: a flat amount, and so many times what the shoggoth had left. */
    public static final float FEED_HEAL = 24;
    public static final float FEED_HEAL_PER_HEALTH = 3;
}
