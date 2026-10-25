package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeature;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static void registerGeneration()
    {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath(MODID, "void_monolith"), VoidMonolithFeature.CODEC);

        var voidMonolithPlacedRegistryKey = ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MODID, "void_monolith"));

        BiomeModifications.create(Identifier.fromNamespaceAndPath(MODID, "void_monolith_addition")).add(
                ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(),
                context ->
                {
                    context.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, voidMonolithPlacedRegistryKey);
                }
        );
    }
}
