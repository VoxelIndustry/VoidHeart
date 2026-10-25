package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

public class ModelsProvider extends FabricModelProvider
{
    private BlockModelGenerators generator;

    public ModelsProvider(FabricDataOutput output)
    {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generator)
    {
        this.generator = generator;

        BlockFamilies.getAllFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).filter(BlockFamily::shouldGenerateModel).forEach(family ->
        {
            generator.family(family.getBaseBlock()).generateFor(family);
            generator.delegateItemModel(family.getBaseBlock(), ModelLocationUtils.getModelLocation(family.getBaseBlock()));
        });

        registerPillar(VoidHeartBlocks.VOIDSTONE_PILLAR);
        registerPillarSlab(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB);
        registerPillarStairs(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

        registerPillar(VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR);
    }

    @Override
    public void generateItemModels(ItemModelGenerators generator)
    {
        generator.generateFlatItem(VoidHeartItems.RAVENOUS_GOLD_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.ARROGANT_IRON_INGOT, ModelTemplates.FLAT_ITEM);
    }

    private void registerPillar(Block block)
    {
        var textureMap = TextureMapping.column(block);
        generator.blockStateOutput.accept(BlockModelGenerators.createAxisAlignedPillarBlock(block, ModelTemplates.CUBE_COLUMN.create(block, textureMap, this.generator.modelOutput)));
    }

    private void registerPillarSlab(Block pillar, Block slab)
    {
        var textureMap = TextureMapping.column(pillar);
        var slabModel = ModelTemplates.SLAB_BOTTOM.create(slab, textureMap, this.generator.modelOutput);
        var slabTopModel = ModelTemplates.SLAB_TOP.create(slab, textureMap, this.generator.modelOutput);
        var slabDoubleModel = ModelTemplates.CUBE_COLUMN.createWithOverride(slab, "_double", textureMap, this.generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSlab(slab, slabModel, slabTopModel, slabDoubleModel));
    }

    private void registerPillarStairs(Block pillar, Block stairs)
    {
        var textureMap = TextureMapping.column(pillar);

        var innerStairsModel = ModelTemplates.STAIRS_INNER.create(stairs, textureMap, this.generator.modelOutput);
        var stairsModel = ModelTemplates.STAIRS_STRAIGHT.create(stairs, textureMap, this.generator.modelOutput);
        var outerStairsModel = ModelTemplates.STAIRS_OUTER.create(stairs, textureMap, this.generator.modelOutput);

        generator.blockStateOutput.accept(BlockModelGenerators.createStairs(stairs, innerStairsModel, stairsModel, outerStairsModel));
        generator.delegateItemModel(stairs, stairsModel);
    }

    private void registerSimpleCubeAll(Block block)
    {
        generator.createTrivialCube(block);
        generator.delegateItemModel(block, ModelLocationUtils.getModelLocation(block));
    }
}
