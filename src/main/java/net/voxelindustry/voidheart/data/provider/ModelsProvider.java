package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.block.Block;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.ModelIds;
import net.minecraft.data.client.Models;
import net.minecraft.data.client.TextureMap;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

public class ModelsProvider extends FabricModelProvider
{
    private BlockStateModelGenerator generator;

    public ModelsProvider(FabricDataOutput output)
    {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator generator)
    {
        this.generator = generator;

        BlockFamilies.getFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).filter(BlockFamily::shouldGenerateModels).forEach(family ->
        {
            generator.registerCubeAllModelTexturePool(family.getBaseBlock()).family(family);
            generator.registerParentedItemModel(family.getBaseBlock(), ModelIds.getBlockModelId(family.getBaseBlock()));
        });

        registerPillar(VoidHeartBlocks.VOIDSTONE_PILLAR);
        registerPillarSlab(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB);
        registerPillarStairs(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

        registerPillar(VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR);
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator)
    {
        generator.register(VoidHeartItems.RAVENOUS_GOLD_INGOT, Models.GENERATED);
        generator.register(VoidHeartItems.ARROGANT_IRON_INGOT, Models.GENERATED);
    }

    private void registerPillar(Block block)
    {
        var textureMap = TextureMap.sideEnd(block);
        generator.blockStateCollector.accept(BlockStateModelGenerator.createAxisRotatedBlockState(block, Models.CUBE_COLUMN.upload(block, textureMap, this.generator.modelCollector)));
    }

    private void registerPillarSlab(Block pillar, Block slab)
    {
        var textureMap = TextureMap.sideEnd(pillar);
        var slabModel = Models.SLAB.upload(slab, textureMap, this.generator.modelCollector);
        var slabTopModel = Models.SLAB_TOP.upload(slab, textureMap, this.generator.modelCollector);
        var slabDoubleModel = Models.CUBE_COLUMN.uploadWithoutVariant(slab, "_double", textureMap, this.generator.modelCollector);
        generator.blockStateCollector.accept(BlockStateModelGenerator.createSlabBlockState(slab, slabModel, slabTopModel, slabDoubleModel));
    }

    private void registerPillarStairs(Block pillar, Block stairs)
    {
        var textureMap = TextureMap.sideEnd(pillar);

        var innerStairsModel = Models.INNER_STAIRS.upload(stairs, textureMap, this.generator.modelCollector);
        var stairsModel = Models.STAIRS.upload(stairs, textureMap, this.generator.modelCollector);
        var outerStairsModel = Models.OUTER_STAIRS.upload(stairs, textureMap, this.generator.modelCollector);

        generator.blockStateCollector.accept(BlockStateModelGenerator.createStairsBlockState(stairs, innerStairsModel, stairsModel, outerStairsModel));
        generator.registerParentedItemModel(stairs, stairsModel);
    }

    private void registerSimpleCubeAll(Block block)
    {
        generator.registerSimpleCubeAll(block);
        generator.registerParentedItemModel(block, ModelIds.getBlockModelId(block));
    }
}
