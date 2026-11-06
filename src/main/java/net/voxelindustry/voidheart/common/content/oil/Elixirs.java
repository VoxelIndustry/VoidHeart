package net.voxelindustry.voidheart.common.content.oil;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;

/**
 * Drunk brews of the athanor. Each answers one danger of the dungeons rather than raising a plain stat, which vanilla potions already do.
 */
public final class Elixirs
{
    public static final int EMBER_DRAUGHT_TICKS = 3 * 60 * 20;
    /**
     * Share of the Dragoon's void fire that still lands on a drinker of Ember Draught.
     */
    public static final float EMBER_WARD_TAKEN  = 0.5F;

    public static final int STILLWATER_TICKS    = 3 * 60 * 20;
    /**
     * Share of a smoker jet's heat that still lands on a drinker of Stillwater.
     */
    public static final float STILLWATER_TAKEN  = 0.25F;

    public static final int BULWARK_TICKS       = 90 * 20;
    /**
     * Absorption VI, 24 health, enough to take the Malignant Boulder's fixed 20 and stand. A golden apple's Absorption IV gives 16.
     */
    public static final int BULWARK_AMPLIFIER   = 5;

    private Elixirs()
    {
    }

    public static Item.Properties emberDraught(Item.Properties properties)
    {
        return drink(properties, new MobEffectInstance(VoidHeartEffects.EMBER_WARD, EMBER_DRAUGHT_TICKS));
    }

    public static Item.Properties stillwater(Item.Properties properties)
    {
        return drink(properties, new MobEffectInstance(VoidHeartEffects.STILLWATER, STILLWATER_TICKS));
    }

    public static Item.Properties bulwark(Item.Properties properties)
    {
        return drink(properties, new MobEffectInstance(MobEffects.ABSORPTION, BULWARK_TICKS, BULWARK_AMPLIFIER));
    }

    private static Item.Properties drink(Item.Properties properties, MobEffectInstance effect)
    {
        return properties
                .component(DataComponents.CONSUMABLE, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(effect)).build())
                .usingConvertsTo(Items.GLASS_BOTTLE)
                .stacksTo(16);
    }

    /**
     * @return what is left of the Dragoon's void fire for this target
     */
    public static float againstVoidFire(LivingEntity target, float damage)
    {
        return target.hasEffect(VoidHeartEffects.EMBER_WARD) ? damage * EMBER_WARD_TAKEN : damage;
    }

    /**
     * @return what is left of a smoker jet's heat for this target
     */
    public static float againstJet(LivingEntity target, float damage)
    {
        return target.hasEffect(VoidHeartEffects.STILLWATER) ? damage * STILLWATER_TAKEN : damage;
    }
}
