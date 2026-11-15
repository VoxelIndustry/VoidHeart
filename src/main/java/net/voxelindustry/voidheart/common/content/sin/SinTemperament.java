package net.voxelindustry.voidheart.common.content.sin;

import static net.voxelindustry.voidheart.common.content.sin.SinBalance.*;

/**
 * What each sin gives and takes at a devotion, from 0 for a novice to 1 for a devoted wielder. Pure, so the game hooks in {@link SinCombat} stay thin.
 */
public final class SinTemperament
{
    private SinTemperament()
    {
    }

    /**
     * How much a fight teaches: stronger foes teach more, and more again in the dungeons and the Daedalus.
     */
    public static float teachingWeight(float maxHealth, float placeWeight)
    {
        return Math.clamp(maxHealth / WEIGHT_HEALTH_UNIT, MIN_WEIGHT, MAX_WEIGHT) * placeWeight;
    }

    // Pride

    public static int prideMaxStacks(float devotion)
    {
        return lerp(PRIDE_MAX_STACKS_NOVICE, PRIDE_MAX_STACKS_DEVOTED, devotion);
    }

    public static int prideStacks(long ticksUnhurt, float devotion)
    {
        if (ticksUnhurt <= 0)
            return 0;
        return (int) Math.min(prideMaxStacks(devotion), ticksUnhurt / PRIDE_STACK_TICKS);
    }

    /**
     * @return the damage multiplier
     */
    public static float prideMultiplier(int stacks, float devotion)
    {
        return 1 + stacks * lerp(PRIDE_STACK_BONUS_NOVICE, PRIDE_STACK_BONUS_DEVOTED, devotion);
    }

    public static int prideWeaknessLevel(float devotion)
    {
        return lerp(PRIDE_WEAKNESS_LEVEL_NOVICE, PRIDE_WEAKNESS_LEVEL_DEVOTED, devotion);
    }

    public static int prideWeaknessTicks(float devotion)
    {
        return lerp(PRIDE_WEAKNESS_TICKS_NOVICE, PRIDE_WEAKNESS_TICKS_DEVOTED, devotion);
    }

    // Wrath

    /**
     * @return 0 at full health, 1 at one heart or less
     */
    public static float wrathMissing(float health, float maxHealth)
    {
        if (maxHealth <= WRATH_FULL_AT_HEALTH)
            return 0;
        return Math.clamp((maxHealth - health) / (maxHealth - WRATH_FULL_AT_HEALTH), 0, 1);
    }

    public static float wrathMultiplier(float health, float maxHealth, float devotion)
    {
        return 1 + wrathMissing(health, maxHealth) * lerp(WRATH_BONUS_NOVICE, WRATH_BONUS_DEVOTED, devotion);
    }

    public static boolean wrathBurns(float health, float maxHealth)
    {
        return health < maxHealth * WRATH_BURNS_BELOW;
    }

    /**
     * @return the share of healing kept while the blade is held
     */
    public static float wrathHealingKept(float health, float maxHealth, float devotion)
    {
        if (!wrathBurns(health, maxHealth))
            return 1;
        return 1 - lerp(WRATH_HEAL_CUT_NOVICE, WRATH_HEAL_CUT_DEVOTED, devotion);
    }

    public static boolean wrathTeaches(float health, float maxHealth)
    {
        return health < maxHealth * WRATH_TEACHES_BELOW;
    }

    // Gluttony

    public static float gluttonyHeal(float dealt, float devotion)
    {
        return Math.max(0, dealt) * lerp(GLUTTONY_HEAL_NOVICE, GLUTTONY_HEAL_DEVOTED, devotion);
    }

    public static float gluttonyCost(float devotion)
    {
        return lerp(GLUTTONY_COST_NOVICE, GLUTTONY_COST_DEVOTED, devotion);
    }

    /**
     * What a hit eats: saturation first, then whole hunger points, then health once starved.
     *
     * @param food the hunger bar, in points
     */
    public static Meal gluttonyEat(float saturation, int food, float cost)
    {
        var fromSaturation = Math.min(saturation, cost);
        var left = cost - fromSaturation;

        int fromFood = Math.min(food, (int) Math.ceil(left));
        left = Math.max(0, left - fromFood);

        return new Meal(saturation - fromSaturation, food - fromFood, left);
    }

    /**
     * @param healthCost health taken once the food ran out
     */
    public record Meal(float saturation, int food, float healthCost)
    {
    }

    // Sloth

    public static int slothChargeTicks(float devotion)
    {
        return lerp(SLOTH_CHARGE_TICKS_NOVICE, SLOTH_CHARGE_TICKS_DEVOTED, devotion);
    }

    /**
     * Standing still adds a tick of charge, moving drains it.
     */
    public static int slothCharge(int charge, boolean still, float devotion)
    {
        if (still)
            return Math.min(slothChargeTicks(devotion), charge + 1);
        return Math.max(0, charge - SLOTH_DRAIN_PER_TICK);
    }

    public static boolean slothCharged(int charge, float devotion)
    {
        return charge >= slothChargeTicks(devotion);
    }

    public static float slothMultiplier(float devotion)
    {
        return 1 + lerp(SLOTH_BONUS_NOVICE, SLOTH_BONUS_DEVOTED, devotion);
    }

    public static int slothStaggerTicks(float devotion)
    {
        return lerp(SLOTH_STAGGER_TICKS_NOVICE, SLOTH_STAGGER_TICKS_DEVOTED, devotion);
    }

    /**
     * @return the share of movement speed lost while held
     */
    public static float slothSlow(float devotion)
    {
        return lerp(SLOTH_SLOW_NOVICE, SLOTH_SLOW_DEVOTED, devotion);
    }

    // Greed

    public static int greedCooldown(float devotion)
    {
        return lerp(GREED_COOLDOWN_NOVICE, GREED_COOLDOWN_DEVOTED, devotion);
    }

    /**
     * A novice steals an effect at its lowest level, a devoted wielder at its full level.
     */
    public static int greedStolenLevel(int level, float devotion)
    {
        return Math.round(level * devotion);
    }

    public static int greedStolenTicks(int remaining)
    {
        return remaining < 0 ? GREED_MAX_STOLEN_TICKS : Math.min(remaining, GREED_MAX_STOLEN_TICKS);
    }

    public static float greedAbsorption(float devotion)
    {
        return lerp(GREED_ABSORPTION_NOVICE, GREED_ABSORPTION_DEVOTED, devotion);
    }

    public static int greedDebtTicks(int stolenTicks, float devotion)
    {
        return Math.round(stolenTicks * lerp(GREED_DEBT_NOVICE, GREED_DEBT_DEVOTED, devotion));
    }

    /**
     * A novice repays at the lowest level, a devoted wielder at the level they took.
     */
    public static int greedDebtLevel(int stolenLevel, float devotion)
    {
        return Math.round(stolenLevel * devotion);
    }
}
