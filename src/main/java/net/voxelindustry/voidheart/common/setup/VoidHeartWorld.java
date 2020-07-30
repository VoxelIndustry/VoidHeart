package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome.Category;
import net.minecraft.world.gen.GenerationStep;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeature;
import net.voxelindustry.voidheart.common.world.VoidMonolithFeatureConfig;
import org.apache.commons.lang3.ArrayUtils;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartWorld
{
    public static VoidMonolithFeature VOID_MONOLITH;

    public static void registerGeneration()
    {
        Category[] categories = new Category[]{Category.BEACH, Category.PLAINS, Category.FOREST, Category.DESERT, Category.ICY, Category.JUNGLE, Category.MESA};

        Registry.register(Registry.FEATURE, new Identifier(MODID, "void_monolith"), VOID_MONOLITH = new VoidMonolithFeature(VoidMonolithFeatureConfig.CODEC));

        Registry.BIOME.forEach(biome ->
        {
            if (ArrayUtils.contains(categories, biome.getCategory()))
                biome.addFeature(GenerationStep.Feature.SURFACE_STRUCTURES,
                        VOID_MONOLITH.configure(new VoidMonolithFeatureConfig(3, 5)));
        });
        RegistryEntryAddedCallback.event(Registry.BIOME).register(((i, identifier, biome) ->
        {
            if (ArrayUtils.contains(categories, biome.getCategory()))
                biome.addFeature(GenerationStep.Feature.SURFACE_STRUCTURES,
                        VOID_MONOLITH.configure(new VoidMonolithFeatureConfig(3, 5)));
        }));
    }
}
