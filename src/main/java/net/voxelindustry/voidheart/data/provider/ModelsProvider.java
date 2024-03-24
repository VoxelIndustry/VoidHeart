package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.block.Block;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.ModelIds;
import net.minecraft.data.client.Models;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
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
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator)
    {
        generator.register(VoidHeartItems.RAVENOUS_GOLD_INGOT, Models.GENERATED);
        generator.register(VoidHeartItems.ARROGANT_IRON_INGOT, Models.GENERATED);
    }

    private void registerSimpleCubeAll(Block block)
    {
        generator.registerSimpleCubeAll(block);
        generator.registerParentedItemModel(block, ModelIds.getBlockModelId(block));
    }
}
