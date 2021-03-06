package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeature;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeatureConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static VoidMonolithFeature VOID_MONOLITH;

    public static void registerGeneration()
    {
        Registry.register(Registry.FEATURE, new Identifier(MODID, "void_monolith"), VOID_MONOLITH = new VoidMonolithFeature(VoidMonolithFeatureConfig.CODEC));

        ConfiguredFeature<VoidMonolithFeatureConfig, ?> configuredMonolith = VOID_MONOLITH.configure(new VoidMonolithFeatureConfig(3, 5));
        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, new Identifier(MODID, "configured_void_monolith"), configuredMonolith);

        BiomeModifications.create(new Identifier(MODID, "void_monolith_addition")).add(
                ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(),
                context ->
                {
                    context.getGenerationSettings().addBuiltInFeature(GenerationStep.Feature.SURFACE_STRUCTURES, configuredMonolith);
                }
        );
    }
}
