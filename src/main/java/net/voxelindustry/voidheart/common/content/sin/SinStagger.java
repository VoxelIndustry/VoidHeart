package net.voxelindustry.voidheart.common.content.sin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * A charged sloth blow staggers its target, breaking off what it was winding up.
 */
public final class SinStagger
{
    /**
     * Slowness level that pins a staggered mob in place.
     */
    public static final int PIN_LEVEL = 4;

    private SinStagger()
    {
    }

    /**
     * Mobs with wind-ups, grabs or feedings of their own break them off here. Others lose their running goals and their footing.
     */
    public interface Staggerable
    {
        void stagger(int ticks);
    }

    public static void stagger(LivingEntity target, int ticks)
    {
        if (target instanceof Staggerable staggerable)
            staggerable.stagger(ticks);

        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, PIN_LEVEL));
        if (target.isUsingItem())
            target.stopUsingItem();

        if (target instanceof Mob mob)
        {
            mob.getNavigation().stop();
            mob.getGoalSelector().getAvailableGoals().stream()
                    .filter(WrappedGoal::isRunning)
                    .filter(WrappedGoal::isInterruptable)
                    .forEach(WrappedGoal::stop);
        }
    }
}
