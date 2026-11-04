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
import net.voxelindustry.voidheart.common.world.dungeon.DungeonBiomeSource;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonChunkGenerator;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static void registerGeneration()
    {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath(MODID, "void_monolith"), VoidMonolithFeature.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MODID, "dungeon_gen"), DungeonChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(MODID, "dungeon_biomes"), DungeonBiomeSource.CODEC);

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
