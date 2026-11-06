package net.voxelindustry.voidheart.common.setup;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class VoidHeartEffects
{
    private static final int INFECTION_COLOR = 0x277D63;

    // Only shows the state of the infection, which lives on the player
    public static final Holder<MobEffect> BARNACLE_INFECTION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "barnacle_infection"),
            new DisplayEffect(MobEffectCategory.HARMFUL, INFECTION_COLOR));

    private static final int EMBER_WARD_COLOR = 0xFF8A3D;
    private static final int STILLWATER_COLOR = 0x7FB8FF;

    /**
     * Ember Draught: the Kernacian Dragoon's void fire does less, though it still sets the drinker alight.
     */
    public static final Holder<MobEffect> EMBER_WARD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "ember_ward"),
            new DisplayEffect(MobEffectCategory.BENEFICIAL, EMBER_WARD_COLOR));

    /**
     * Stillwater: the jets of the Still Depths' smokers do less.
     */
    public static final Holder<MobEffect> STILLWATER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "stillwater"),
            new DisplayEffect(MobEffectCategory.BENEFICIAL, STILLWATER_COLOR));

    private VoidHeartEffects()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the effects
    }

    private static final class DisplayEffect extends MobEffect
    {
        private DisplayEffect(MobEffectCategory category, int color)
        {
            super(category, color);
        }
    }
}
