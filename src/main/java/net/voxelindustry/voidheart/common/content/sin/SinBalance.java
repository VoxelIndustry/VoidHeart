package net.voxelindustry.voidheart.common.content.sin;

/**
 * Every number of the sin temperaments. Each pair goes from a novice, at no proficiency, to a devoted wielder, at full proficiency. All are guesses to tune from play.
 */
public final class SinBalance
{
    /**
     * Proficiency points of full devotion. A hit of a full-health zombie in the overworld gives about one point.
     */
    public static final float DEVOTED_POINTS = 100_000;
    public static final int   STAGES         = 5;
    /**
     * Proficiency fraction at which each stage starts, stage 1 first.
     */
    public static final float[] STAGE_STARTS = {0, 0.08F, 0.25F, 0.5F, 0.8F};
    /**
     * Each point gained in a sin takes this much from every other sin.
     */
    public static final float FADE_RATIO = 0.5F;
    /**
     * A stage below which a player isn't devoted enough to resent fighting with another sin.
     */
    public static final int   RESENT_STAGE = 3;

    // Who teaches: only hostile mobs fighting back, more the stronger they are
    public static final float WEIGHT_HEALTH_UNIT = 20;
    public static final float MIN_WEIGHT         = 0.5F;
    public static final float MAX_WEIGHT         = 8;
    public static final float DUNGEON_WEIGHT     = 2;
    public static final float DAEDALUS_WEIGHT    = 3;

    // Pride: unbroken stacks of damage
    public static final int   PRIDE_STACK_TICKS        = 40;
    public static final float PRIDE_STACK_BONUS_NOVICE = 0.04F;
    public static final float PRIDE_STACK_BONUS_DEVOTED = 0.08F;
    public static final int   PRIDE_MAX_STACKS_NOVICE  = 5;
    public static final int   PRIDE_MAX_STACKS_DEVOTED = 8;
    public static final int   PRIDE_WEAKNESS_LEVEL_NOVICE  = 0;
    public static final int   PRIDE_WEAKNESS_LEVEL_DEVOTED = 1;
    public static final int   PRIDE_WEAKNESS_TICKS_NOVICE  = 40;
    public static final int   PRIDE_WEAKNESS_TICKS_DEVOTED = 100;
    public static final int   PRIDE_TEACHING_STACKS    = 3;
    public static final float PRIDE_POINTS             = 1;

    // Wrath: damage with missing health, healing cut while the blade burns
    public static final float WRATH_BONUS_NOVICE      = 0.3F;
    public static final float WRATH_BONUS_DEVOTED     = 1;
    /**
     * Health at which the bonus is full, one heart.
     */
    public static final float WRATH_FULL_AT_HEALTH    = 2;
    public static final float WRATH_BURNS_BELOW       = 0.75F;
    public static final float WRATH_HEAL_CUT_NOVICE   = 0.2F;
    public static final float WRATH_HEAL_CUT_DEVOTED  = 0.5F;
    public static final float WRATH_TEACHES_BELOW     = 0.5F;
    public static final float WRATH_POINTS            = 1;

    // Gluttony: hits heal, and eat food then health
    public static final float GLUTTONY_HEAL_NOVICE    = 0.05F;
    public static final float GLUTTONY_HEAL_DEVOTED   = 0.15F;
    /**
     * Food eaten by each hit, in saturation then hunger points, or in health once starved.
     */
    public static final float GLUTTONY_COST_NOVICE    = 0.5F;
    public static final float GLUTTONY_COST_DEVOTED   = 1.5F;
    public static final float GLUTTONY_POINTS_PER_HEALTH = 1.5F;

    // Sloth: a charged blow from standing still, slower steps while held
    public static final int   SLOTH_CHARGE_TICKS_NOVICE  = 20;
    public static final int   SLOTH_CHARGE_TICKS_DEVOTED = 10;
    public static final float SLOTH_BONUS_NOVICE         = 0.4F;
    public static final float SLOTH_BONUS_DEVOTED        = 1.2F;
    public static final int   SLOTH_STAGGER_TICKS_NOVICE  = 20;
    public static final int   SLOTH_STAGGER_TICKS_DEVOTED = 50;
    public static final float SLOTH_SLOW_NOVICE          = 0.05F;
    public static final float SLOTH_SLOW_DEVOTED         = 0.15F;
    /**
     * Horizontal movement per tick below which the wielder counts as standing still.
     */
    public static final double SLOTH_STILL_DISTANCE      = 0.01;
    /**
     * Charge lost for each tick of moving.
     */
    public static final int   SLOTH_DRAIN_PER_TICK       = 2;
    public static final float SLOTH_POINTS               = 2;

    // Greed: steal a boon, repay its opposite
    public static final int   GREED_COOLDOWN_NOVICE  = 100;
    public static final int   GREED_COOLDOWN_DEVOTED = 40;
    public static final int   GREED_MAX_STOLEN_TICKS = 600;
    public static final float GREED_ABSORPTION_NOVICE  = 4;
    public static final float GREED_ABSORPTION_DEVOTED = 10;
    public static final int   GREED_ABSORPTION_TICKS = 600;
    /**
     * The debt's length, as a share of what was stolen.
     */
    public static final float GREED_DEBT_NOVICE      = 0.5F;
    public static final float GREED_DEBT_DEVOTED     = 1.5F;
    public static final float GREED_POINTS           = 6;

    private SinBalance()
    {
    }

    public static float lerp(float novice, float devoted, float devotion)
    {
        return novice + (devoted - novice) * devotion;
    }

    public static int lerp(int novice, int devoted, float devotion)
    {
        return Math.round(novice + (devoted - novice) * devotion);
    }
}
