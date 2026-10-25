package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider.BlockTagProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily.Variant;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.concurrent.CompletableFuture;

public class BlockTagsProvider extends BlockTagProvider
{
    public BlockTagsProvider(FabricDataOutput output, CompletableFuture<Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(Provider arg)
    {
        var pickaxeMineable = getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
                .setReplace(false);

        var walls = getOrCreateTagBuilder(BlockTags.WALLS)
                .setReplace(false);

        for (var block : VoidHeartBlocks.MINEABLE_BLOCKS)
        {
            pickaxeMineable.add(block);
        }

        BlockFamilies.getAllFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).forEachOrdered(family ->
        {
            if (family.get(Variant.WALL) != null)
                walls.add(family.get(Variant.WALL));
        });

        var essenceWater = getOrCreateTagBuilder(VoidHeartTags.ESSENCE_WATER_TAG)
                .setReplace(false);
        essenceWater.add(Blocks.WATER);

        var essenceLava = getOrCreateTagBuilder(VoidHeartTags.ESSENCE_LAVA_TAG)
                .setReplace(false);
        essenceLava.add(Blocks.LAVA);
    }
}
