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
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;

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

        builder(BlockTags.MINEABLE_WITH_AXE)
                .setReplace(false)
                .add(VoidHeartBlocks.DORMANT_TREANT.builtInRegistryHolder().key());

        builder(BlockTags.MINEABLE_WITH_SHOVEL)
                .setReplace(false)
                .add(VoidHeartBlocks.ABYSSAL_SILT.builtInRegistryHolder().key());

        // Water washes laid salt off the floor
        builder(BlockTags.WASHED_AWAY_BY_FLUIDS)
                .setReplace(false)
                .add(VoidHeartBlocks.SALT_WARD.builtInRegistryHolder().key());

        builder(BlockTags.ENCHANTMENT_POWER_PROVIDER)
                .setReplace(false)
                .add(VoidHeartBlocks.VOIDSTONE_BOOKSHELF.builtInRegistryHolder().key())
                .add(VoidHeartBlocks.RUINED_VOIDSTONE_BOOKSHELF.builtInRegistryHolder().key());

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

        var aspectStillness = builder(VoidHeartTags.ASPECT_STILLNESS_TAG)
                .setReplace(false);
        aspectStillness.add(Blocks.WATER.builtInRegistryHolder().key());

        var aspectFiery = builder(VoidHeartTags.ASPECT_FIERY_TAG)
                .setReplace(false);
        aspectFiery.add(Blocks.LAVA.builtInRegistryHolder().key());

        EnergyDatagen.tags(this::builder);
    }
}
