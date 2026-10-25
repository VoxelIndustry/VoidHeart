package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily.Variant;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.concurrent.CompletableFuture;

public class BlockTagsProvider extends FabricTagsProvider.BlockTagsProvider
{
    public BlockTagsProvider(FabricPackOutput output, CompletableFuture<Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(Provider arg)
    {
        var pickaxeMineable = builder(BlockTags.MINEABLE_WITH_PICKAXE)
                .setReplace(false);

        var walls = builder(BlockTags.WALLS)
                .setReplace(false);

        for (var block : VoidHeartBlocks.MINEABLE_BLOCKS)
        {
            pickaxeMineable.add(block.builtInRegistryHolder().key());
        }

        BlockFamilies.getAllFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).forEachOrdered(family ->
        {
            if (family.get(Variant.WALL) != null)
                walls.add(family.get(Variant.WALL).builtInRegistryHolder().key());
        });

        var essenceWater = builder(VoidHeartTags.ESSENCE_WATER_TAG)
                .setReplace(false);
        essenceWater.add(Blocks.WATER.builtInRegistryHolder().key());

        var essenceLava = builder(VoidHeartTags.ESSENCE_LAVA_TAG)
                .setReplace(false);
        essenceLava.add(Blocks.LAVA.builtInRegistryHolder().key());
    }
}
