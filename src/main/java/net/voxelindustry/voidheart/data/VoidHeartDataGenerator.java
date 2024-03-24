package net.voxelindustry.voidheart.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.registry.Registries;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.data.provider.AdvancementsProvider;
import net.voxelindustry.voidheart.data.provider.BlockLootsProvider;
import net.voxelindustry.voidheart.data.provider.BlockTagsProvider;
import net.voxelindustry.voidheart.data.provider.ModelsProvider;
import net.voxelindustry.voidheart.data.provider.RecipesProvider;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartDataGenerator implements DataGeneratorEntrypoint
{
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator)
    {
        BlockFamilies.register(VoidHeartBlocks.RAVENOUS_GOLD_BLOCK)
                .cut(VoidHeartBlocks.CUT_RAVENOUS_GOLD)
                .build();
        BlockFamilies.register(VoidHeartBlocks.CUT_RAVENOUS_GOLD)
                .stairs(VoidHeartBlocks.CUT_RAVENOUS_GOLD_STAIRS)
                .slab(VoidHeartBlocks.CUT_RAVENOUS_GOLD_SLAB)
                .wall(VoidHeartBlocks.CUT_RAVENOUS_GOLD_WALL)
                .build();

        var pack = generator.createPack();

        pack.addProvider(AdvancementsProvider::new);
        pack.addProvider(ModelsProvider::new);
        pack.addProvider(BlockTagsProvider::new);
        pack.addProvider(BlockLootsProvider::new);
        pack.addProvider(RecipesProvider::new);
    }

    public static boolean isVoidHeartFamily(BlockFamily family)
    {
        return Registries.BLOCK.getId(family.getBaseBlock()).getNamespace().equals(MODID);
    }
}
