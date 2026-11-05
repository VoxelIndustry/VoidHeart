package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Old armor walking with nothing alive inside it, so nothing that works on flesh works on it.
 */
public abstract class Automaton extends VoidMonster
{
    protected Automaton(EntityType<? extends Automaton> type, Level level)
    {
        super(type, level);
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect)
    {
        return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && super.canBeAffected(effect);
    }
}
