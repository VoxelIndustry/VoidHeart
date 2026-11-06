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
     * Share of the Dragoon's void fire that still lands on a drinker of Ember Draught, by the effect's level. A stronger brew raises the level.
     */
    public static final float[] EMBER_WARD_TAKEN = { 0.5F, 0.3F, 0.15F };

    public static final int STILLWATER_TICKS    = 3 * 60 * 20;
    /**
     * Share of a smoker jet's heat that still lands on a drinker of Stillwater, by the effect's level.
     */
    public static final float[] STILLWATER_TAKEN = { 0.25F, 0.12F, 0.05F };

    public static final int BULWARK_TICKS       = 90 * 20;
    /**
     * Absorption VI, 24 health, enough to take the Malignant Boulder's fixed 20 and stand. A golden apple's Absorption IV gives 16.
     */
    public static final int BULWARK_AMPLIFIER   = 5;

    public static final int INSIGHT_TICKS       = 3 * 60 * 20;
    public static final int CLARITY_TICKS       = 5 * 60 * 20;
    /**
     * How far Insight reaches, by the effect's level. Silhouettes are at full strength within a third of it.
     */
    public static final float[] INSIGHT_RANGE   = { 24, 36, 48 };
    /**
     * How much further Clarity pushes the fog's start and end, by the effect's level.
     */
    public static final float[] CLARITY_START   = { 2.5F, 3.25F, 4 };
    public static final float[] CLARITY_END     = { 1.75F, 2.25F, 2.75F };

    private Elixirs()
    {
    }

    public static Item.Properties insight(Item.Properties properties)
    {
        return drink(properties, new MobEffectInstance(VoidHeartEffects.INSIGHT, INSIGHT_TICKS));
    }

    public static Item.Properties clarity(Item.Properties properties)
    {
        return drink(properties, new MobEffectInstance(VoidHeartEffects.CLARITY, CLARITY_TICKS));
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
        var ward = target.getEffect(VoidHeartEffects.EMBER_WARD);
        return ward == null ? damage : damage * byLevel(EMBER_WARD_TAKEN, ward.getAmplifier());
    }

    /**
     * @return what is left of a smoker jet's heat for this target
     */
    public static float againstJet(LivingEntity target, float damage)
    {
        var still = target.getEffect(VoidHeartEffects.STILLWATER);
        return still == null ? damage : damage * byLevel(STILLWATER_TAKEN, still.getAmplifier());
    }

    /**
     * @return the table's value for an effect level, holding at the last one for any higher level
     */
    public static float byLevel(float[] table, int amplifier)
    {
        return table[Math.clamp(amplifier, 0, table.length - 1)];
    }
}
