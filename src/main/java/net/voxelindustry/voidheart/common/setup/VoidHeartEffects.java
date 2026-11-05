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
