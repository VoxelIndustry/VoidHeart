package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeature;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeatureConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static VoidMonolithFeature VOID_MONOLITH;

    public static void registerGeneration()
    {
        Registry.register(BuiltInRegistries.FEATURE, new ResourceLocation(MODID, "void_monolith"), VOID_MONOLITH = new VoidMonolithFeature(VoidMonolithFeatureConfig.CODEC));

        var voidMonolithPlacedRegistryKey = ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(MODID, "void_monolith"));

        BiomeModifications.create(new ResourceLocation(MODID, "void_monolith_addition")).add(
                ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInOverworld(),
                context ->
                {
                    context.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, voidMonolithPlacedRegistryKey);
                }
        );
    }
}
