package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.ConfiguredFeatures;
import net.minecraft.world.gen.feature.PlacedFeatures;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeature;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeatureConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static VoidMonolithFeature VOID_MONOLITH;

    public static void registerGeneration()
    {
        Registry.register(Registry.FEATURE, new Identifier(MODID, "void_monolith"), VOID_MONOLITH = new VoidMonolithFeature(VoidMonolithFeatureConfig.CODEC));

        var configuredMonolith = ConfiguredFeatures.register(MODID + ":configured_void_monolith", VOID_MONOLITH, new VoidMonolithFeatureConfig(3, 5));

        var placedMonolithIdentifier = new Identifier(MODID, "placed_void_monolith");

        PlacedFeatures.register(placedMonolithIdentifier.toString(), configuredMonolith, CountPlacementModifier.of(3));

        BiomeModifications.create(new Identifier(MODID, "void_monolith_addition")).add(
                ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(),
                context ->
                {
                    context.getGenerationSettings().addFeature(GenerationStep.Feature.SURFACE_STRUCTURES, RegistryKey.of(BuiltinRegistries.PLACED_FEATURE.getKey(), placedMonolithIdentifier));
                }
        );
    }
}
