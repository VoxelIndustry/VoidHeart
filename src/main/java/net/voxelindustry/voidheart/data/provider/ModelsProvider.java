package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamilies;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.List;
import java.util.Optional;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ModelsProvider extends FabricModelProvider
{
    /**
     * Items whose model is hand-written in assets/voidheart/models/item.
     */
    private static final List<String> HAND_WRITTEN_ITEM_MODELS = List.of(
            "eldritch_voidstone", "ender_shard", "experience_skull", "eye_bottle", "inventory_inserter", "local_pearl",
            "obsidian_shard", "pocket_wall", "portal_core", "portal_frame", "portal_frame_core", "portal_interior",
            "shatter_forge", "void_altar", "void_amalgam", "void_conduit", "void_heart", "void_heart_block",
            "void_monolith", "void_monolith_cap", "void_pearl", "void_pillar", "voidstone_writhing",
            "voidstone_writhing_blossom", "voidstone_writhing_maw", "void_key", "permeable_barrier_item", "permeable_barrier"
    );

    /**
     * A cube cut to the table height, hand-written in assets/voidheart/models/block.
     */
    private static final ModelTemplate OCULUS_TABLE_TEMPLATE = new ModelTemplate(
            Optional.of(Identifier.fromNamespaceAndPath(MODID, "block/template_oculus_table")), Optional.empty(),
            TextureSlot.TOP, TextureSlot.SIDE, TextureSlot.BOTTOM);
    private static final ModelTemplate OCULUS_TABLE_BORDER_TEMPLATE = new ModelTemplate(
            Optional.of(Identifier.fromNamespaceAndPath(MODID, "block/template_oculus_table_border")), Optional.of("_border"),
            TextureSlot.SIDE);
    private static final ModelTemplate OCULUS_TABLE_ITEM_TEMPLATE = new ModelTemplate(
            Optional.of(Identifier.fromNamespaceAndPath(MODID, "block/template_oculus_table_inventory")), Optional.of("_inventory"),
            TextureSlot.TOP, TextureSlot.SIDE, TextureSlot.BOTTOM);

    private BlockModelGenerators generator;

    public ModelsProvider(FabricPackOutput output)
    {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generator)
    {
        this.generator = generator;

        BlockFamilies.getAllFamilies().filter(VoidHeartDataGenerator::isVoidHeartFamily).filter(BlockFamily::shouldGenerateModel).forEach(family ->
                generator.family(family.getBaseBlock()).generateFor(family));

        registerPillar(VoidHeartBlocks.VOIDSTONE_PILLAR);
        registerPillarSlab(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB);
        registerPillarStairs(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

        registerPillar(VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR);

        registerOculusTable(VoidHeartBlocks.OCULUS_TABLE);
        registerBeholder(VoidHeartBlocks.BEHOLDER);
    }

    /**
     * The pedestal, its floating eye drawn apart. The hand-written item model holds the eye too.
     */
    private void registerBeholder(Block beholder)
    {
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(beholder, plainVariant(ModelLocationUtils.getModelLocation(beholder))));
        generator.registerSimpleItemModel(beholder, ModelLocationUtils.getModelLocation(beholder, "_inventory"));
    }

    /**
     * The table base, with a border along each side no other table block stands against.
     */
    private void registerOculusTable(Block table)
    {
        var textures = TextureMapping.cubeBottomTop(table).put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(VoidHeartBlocks.VOIDSTONE_POLISHED));
        var base = plainVariant(OCULUS_TABLE_TEMPLATE.create(table, textures, generator.modelOutput));
        var border = plainVariant(OCULUS_TABLE_BORDER_TEMPLATE.create(table, textures, generator.modelOutput));
        var item = OCULUS_TABLE_ITEM_TEMPLATE.create(table, textures, generator.modelOutput);

        generator.blockStateOutput.accept(MultiPartGenerator.multiPart(table)
                .with(base)
                .with(BlockModelGenerators.condition().term(BlockStateProperties.NORTH, false), border)
                .with(BlockModelGenerators.condition().term(BlockStateProperties.EAST, false), border.with(BlockModelGenerators.Y_ROT_90))
                .with(BlockModelGenerators.condition().term(BlockStateProperties.SOUTH, false), border.with(BlockModelGenerators.Y_ROT_180))
                .with(BlockModelGenerators.condition().term(BlockStateProperties.WEST, false), border.with(BlockModelGenerators.Y_ROT_270)));
        generator.registerSimpleItemModel(table, item);
    }

    @Override
    public void generateItemModels(ItemModelGenerators generator)
    {
        generator.generateFlatItem(VoidHeartItems.RAVENOUS_GOLD_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.ARROGANT_IRON_INGOT, ModelTemplates.FLAT_ITEM);

        for (var name : HAND_WRITTEN_ITEM_MODELS)
            generator.itemModelOutput.accept(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MODID, name)), ItemModelUtils.plainModel(itemModel(name)));

        generator.itemModelOutput.accept(VoidHeartBlocks.VOID_LAMP.asItem(), ItemModelUtils.select(
                new ComponentContents<>(VoidHeartDataComponents.CORRUPTION),
                ItemModelUtils.plainModel(itemModel("void_lamp")),
                ItemModelUtils.when(1, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted1_off"))),
                ItemModelUtils.when(2, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted2_off"))),
                ItemModelUtils.when(3, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted3_off"))),
                ItemModelUtils.when(4, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted4_off"))),
                ItemModelUtils.when(5, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted5"))),
                ItemModelUtils.when(6, ItemModelUtils.plainModel(itemModel("void_lamp_corrupted6")))
        ));
    }

    private static Identifier itemModel(String name)
    {
        return Identifier.fromNamespaceAndPath(MODID, "item/" + name);
    }

    private void registerPillar(Block block)
    {
        var textureMap = TextureMapping.column(block);
        generator.blockStateOutput.accept(BlockModelGenerators.createAxisAlignedPillarBlock(block,
                plainVariant(ModelTemplates.CUBE_COLUMN.create(block, textureMap, generator.modelOutput))));
    }

    private void registerPillarSlab(Block pillar, Block slab)
    {
        var textureMap = TextureMapping.column(pillar);
        var slabModel = ModelTemplates.SLAB_BOTTOM.create(slab, textureMap, generator.modelOutput);
        var slabTopModel = ModelTemplates.SLAB_TOP.create(slab, textureMap, generator.modelOutput);
        var slabDoubleModel = ModelTemplates.CUBE_COLUMN.createWithOverride(slab, "_double", textureMap, generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSlab(slab, plainVariant(slabModel), plainVariant(slabTopModel), plainVariant(slabDoubleModel)));
    }

    private void registerPillarStairs(Block pillar, Block stairs)
    {
        var textureMap = TextureMapping.column(pillar);

        var innerStairsModel = ModelTemplates.STAIRS_INNER.create(stairs, textureMap, generator.modelOutput);
        var stairsModel = ModelTemplates.STAIRS_STRAIGHT.create(stairs, textureMap, generator.modelOutput);
        var outerStairsModel = ModelTemplates.STAIRS_OUTER.create(stairs, textureMap, generator.modelOutput);

        generator.blockStateOutput.accept(BlockModelGenerators.createStairs(stairs, plainVariant(innerStairsModel), plainVariant(stairsModel), plainVariant(outerStairsModel)));
        generator.registerSimpleItemModel(stairs, stairsModel);
    }
}
