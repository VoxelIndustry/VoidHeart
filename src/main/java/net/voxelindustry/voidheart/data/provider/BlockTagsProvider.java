package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider.BlockTagProvider;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily.Variant;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.tag.BlockTags;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.concurrent.CompletableFuture;

public class BlockTagsProvider extends BlockTagProvider
{
    public BlockTagsProvider(FabricDataOutput output, CompletableFuture<WrapperLookup> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(WrapperLookup arg)
    {
        var pickaxeMineable = getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE)
                .setReplace(false);

        var walls = getOrCreateTagBuilder(BlockTags.WALLS)
                .setReplace(false);

        for (var block : VoidHeartBlocks.MINEABLE_BLOCKS)
        {
            pickaxeMineable.add(block);
        }

        BlockFamilies.getFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).forEachOrdered(family ->
        {
            if (family.getVariant(Variant.WALL) != null)
                walls.add(family.getVariant(Variant.WALL));
        });
    }
}
