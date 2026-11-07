package net.voxelindustry.voidheart.common.content.oil;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Turns what a recipe makes into what was actually brewed: a drink's effect is rewritten for its shards and vessel, and an oil's flask remembers
 * them for the coat it lays.
 */
public final class Brews
{
    private Brews()
    {
    }

    public static Brew brewOf(ItemStack stack)
    {
        var brew = stack.get(VoidHeartDataComponents.BREW);
        return brew == null ? Brew.PLAIN : brew;
    }

    @Nullable
    public static SinVessel vesselOf(ItemStack stack)
    {
        for (var vessel : SinVessel.values())
        {
            if (stack.is(flask(vessel)))
                return vessel;
        }
        return null;
    }

    public static Item flask(SinVessel vessel)
    {
        return switch (vessel)
        {
            case ARROGANT_IRON -> VoidHeartItems.ARROGANT_IRON_FLASK;
            case RAVENOUS_GOLD -> VoidHeartItems.RAVENOUS_GOLD_FLASK;
            case SEETHING_STEEL -> VoidHeartItems.SEETHING_STEEL_FLASK;
            case COVETOUS_COPPER -> VoidHeartItems.COVETOUS_COPPER_FLASK;
            case LISTLESS_LEAD -> VoidHeartItems.LISTLESS_LEAD_FLASK;
        };
    }

    public static boolean isOil(ItemStack product)
    {
        return product.getItem() instanceof OilFlaskItem;
    }

    public static boolean isElixir(ItemStack product)
    {
        return product.has(DataComponents.CONSUMABLE);
    }

    /**
     * Whether a product can be brewed in this vessel at all.
     */
    public static boolean holds(SinVessel vessel, ItemStack product)
    {
        return isOil(product) ? vessel.holdsOils() : isElixir(product) && vessel.holdsElixirs();
    }

    /**
     * Whether shards change this product, which only drinks and oils answer.
     */
    public static boolean refines(ItemStack product)
    {
        return isOil(product) || isElixir(product);
    }

    /**
     * @return the product of a brew, as many as it yields
     */
    public static ItemStack apply(ItemStack product, Brew brew)
    {
        if (brew.isPlain())
            return product;

        var brewed = product.copyWithCount(product.getCount() * brew.yield());
        brewed.set(VoidHeartDataComponents.BREW, brew);
        var consumable = brewed.get(DataComponents.CONSUMABLE);
        if (consumable != null)
            brewed.set(DataComponents.CONSUMABLE, rewrite(consumable, brew));
        return brewed;
    }

    private static Consumable rewrite(Consumable consumable, Brew brew)
    {
        var effects = new ArrayList<ConsumeEffect>();
        // Pride brooks no other brew beside it, so they go before its own takes hold
        if (brew.in(SinVessel.ARROGANT_IRON))
            effects.add(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(VoidHeartEffects.EMBER_WARD, VoidHeartEffects.STILLWATER, VoidHeartEffects.INSIGHT,
                    VoidHeartEffects.CLARITY, VoidHeartEffects.RECOLLECTION)));

        int longest = 0;
        for (var effect : consumable.onConsumeEffects())
        {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect(var instances, float probability)))
            {
                effects.add(effect);
                continue;
            }

            var changed = new ArrayList<MobEffectInstance>();
            for (var instance : instances)
            {
                int duration = brew.duration(instance.getDuration());
                longest = Math.max(longest, duration);
                changed.add(new MobEffectInstance(instance.getEffect(), duration, brew.amplifier(instance.getAmplifier()), instance.isAmbient(), instance.isVisible(),
                        instance.showIcon()));
            }
            effects.add(new ApplyStatusEffectsConsumeEffect(changed, probability));
        }

        // Memoriam lets Insight remember, for as long as the Insight lasts
        if (longest > 0 && brew.remembering())
            effects.add(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(VoidHeartEffects.RECOLLECTION, longest)));
        if (longest > 0 && brew.in(SinVessel.RAVENOUS_GOLD))
            effects.add(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, longest)));
        if (longest > 0 && brew.in(SinVessel.LISTLESS_LEAD))
            effects.add(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SLOWNESS, longest)));

        return new Consumable(consumable.consumeSeconds(), consumable.animation(), consumable.sound(), consumable.hasConsumeParticles(), effects);
    }

    /**
     * @return the shards and vessel a brew would take from what sits in the athanor, or empty if the vessel can't hold this product
     */
    public static Optional<Brew> brewFor(ItemStack product, boolean empowered, boolean prolonged, @Nullable SinVessel vessel)
    {
        return brewFor(product, empowered, prolonged, false, vessel);
    }

    /**
     * @return the shards and vessel a brew would take from what sits in the athanor, or empty if the product can't take them: a vessel that can't hold
     * it, shards on what isn't a drink or an oil, or Memoriam on anything but Insight
     */
    public static Optional<Brew> brewFor(ItemStack product, boolean empowered, boolean prolonged, boolean remembering, @Nullable SinVessel vessel)
    {
        if (vessel != null && !holds(vessel, product))
            return Optional.empty();
        if ((empowered || prolonged) && !refines(product))
            return Optional.empty();
        if (remembering && !product.is(VoidHeartItems.INSIGHT_ELIXIR))
            return Optional.empty();
        return Optional.of(new Brew(empowered, prolonged, remembering, Optional.ofNullable(vessel)));
    }
}
