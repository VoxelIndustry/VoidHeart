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

    private static final int INSIGHT_COLOR = 0x8A3DF0;
    private static final int CLARITY_COLOR = 0x6BDBC0;

    /**
     * Insight: wall-crossers show through stone, a Bishop about to cast its lance gathers light first, and a Dragoon's adaptation can be read. All drawn on
     * the drinker's client.
     */
    public static final Holder<MobEffect> INSIGHT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "insight"),
            new DisplayEffect(MobEffectCategory.BENEFICIAL, INSIGHT_COLOR));

    /**
     * Clarity: the fog of the dungeons and the Daedalus starts and ends farther away.
     */
    public static final Holder<MobEffect> CLARITY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "clarity"),
            new DisplayEffect(MobEffectCategory.BENEFICIAL, CLARITY_COLOR));

    private static final int RECOLLECTION_COLOR = 0xBFA9E8;

    /**
     * Recollection, from Insight brewed with Memoriam: a hostile mob seen in plain view stays marked for a while after it goes out of sight.
     */
    public static final Holder<MobEffect> RECOLLECTION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(MODID, "recollection"),
            new DisplayEffect(MobEffectCategory.BENEFICIAL, RECOLLECTION_COLOR));

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
