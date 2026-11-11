package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The burn a Klein Star leaves: a little damage every second, through armor and shields alike.
 */
public class RadiantEffect extends MobEffect
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "radiant"));

    public RadiantEffect(int color)
    {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier)
    {
        entity.hurtServer(level, entity.damageSources().source(DAMAGE), KleinStarBalance.RADIANT_DAMAGE * (amplifier + 1));
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier)
    {
        return duration % KleinStarBalance.RADIANT_INTERVAL == 0;
    }
}
