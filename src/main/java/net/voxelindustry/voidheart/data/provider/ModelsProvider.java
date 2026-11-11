package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamilies;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import com.geckolib.renderer.internal.GeckolibItemSpecialRenderer;
import net.voxelindustry.voidheart.common.content.depths.StrandPlantBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryCoreBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.client.render.tormenter.MalignantTormenterItemRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;

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
            "barnacle_nest", "void_skeleton_head", "eldritch_voidstone", "ender_shard", "experience_skull", "eye_bottle", "inventory_inserter", "local_pearl",
            "obsidian_shard", "pocket_wall", "portal_core", "portal_frame", "portal_frame_core", "portal_interior",
            "shatter_forge", "void_altar", "void_amalgam", "void_conduit", "void_heart", "void_heart_block",
            "void_monolith", "void_monolith_cap", "void_pearl", "void_pillar", "voidstone_writhing",
            "voidstone_writhing_blossom", "voidstone_writhing_maw", "void_key", "permeable_barrier_item", "knowledge_tablet", "knowledge_repository", "knowledge_column", "shoulder_eye", "permeable_barrier", "beam_focus",
            "ravenous_relay", "ravenous_storage", "ravenous_storage_matrix", "cogitant", "meat_proxy", "void_loom",
            "athanor", "alembic", "crucible", "mending_altar", "dormant_treant"
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

    private static final ModelTemplate SURFACE_GROWTH_TEMPLATE = new ModelTemplate(
            Optional.of(Identifier.fromNamespaceAndPath(MODID, "block/template_surface_growth")), Optional.empty(), TextureSlot.CROSS);

    /**
     * The skin a 3D flask model reads its liquid, glass and cork from, hand-written templates in assets/voidheart/models/item/flask.
     */
    private static final TextureSlot FLASK_SKIN = TextureSlot.create("skin");

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

        registerBundle(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE);
        registerBundle(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE);
        registerBundle(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE);
        registerBundle(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE);
        registerBundle(VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE);
        registerBundle(VoidHeartBlocks.LARGE_RUBRUM_BUNDLE);
        registerBundle(VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE);
        registerBundle(VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE);
        registerBundle(VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE);

        registerArmillaryCore();
        generator.createTrivialCube(VoidHeartBlocks.VOIDSTONE_CHISELED);
        registerOculusTable(VoidHeartBlocks.OCULUS_TABLE);
        registerBeholder(VoidHeartBlocks.BEHOLDER);

        registerDepths();
        registerAthanor();
        registerDaedalusDecor();
        registerRepairAndDoor();
        EnergyDatagen.blockModels(generator);
    }

    /**
     * The mending altar is the void altar's shape in amber, hand-written as children of its model. The void door is a vanilla door with its own textures.
     */
    private void registerRepairAndDoor()
    {
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidHeartBlocks.MENDING_ALTAR)
                .with(BlockModelGenerators.createBooleanModelDispatch(BlockStateProperties.LIT,
                        plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.MENDING_ALTAR, "_lit")),
                        plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.MENDING_ALTAR)))));
        generator.createDoor(VoidHeartBlocks.VOID_DOOR);
    }

    private void registerDaedalusDecor()
    {
        generator.createTrivialCube(VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE);
        generator.createTrivialCube(VoidHeartBlocks.IRON_INLAID_VOIDSTONE);
        generator.createTrivialCube(VoidHeartBlocks.VOIDSTONE_ENGRAVED_GEAR);
        generator.createTrivialCube(VoidHeartBlocks.VOIDSTONE_ENGRAVED_EYE);
        generator.createTrivialCube(VoidHeartBlocks.CAGED_LAMP_AMBER);
        generator.createTrivialCube(VoidHeartBlocks.CAGED_LAMP_TEAL);
        generator.createTrivialCube(VoidHeartBlocks.CAGED_LAMP_PALE);

        for (var rug : List.of(VoidHeartBlocks.UMBRAL_RUG, VoidHeartBlocks.VIOLET_RUG, VoidHeartBlocks.TEAL_RUG))
            generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(rug, plainVariant(TexturedModel.CARPET.create(rug, generator.modelOutput))));

        // Shelves end in polished voidstone, as vanilla ones end in planks
        for (var shelf : List.of(VoidHeartBlocks.VOIDSTONE_BOOKSHELF, VoidHeartBlocks.RUINED_VOIDSTONE_BOOKSHELF))
        {
            var textures = TextureMapping.column(TextureMapping.getBlockTexture(shelf), TextureMapping.getBlockTexture(VoidHeartBlocks.VOIDSTONE_POLISHED));
            generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(shelf, plainVariant(ModelTemplates.CUBE_COLUMN.create(shelf, textures, generator.modelOutput))));
        }
    }

    /**
     * The core and alembic turn to face whoever placed them, the core glowing once lit. Their shapes and the crucible's are hand-written.
     */
    private void registerAthanor()
    {
        var idle = plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.ATHANOR));
        var lit = plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.ATHANOR, "_lit"));
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidHeartBlocks.ATHANOR)
                .with(PropertyDispatch.initial(BlockStateProperties.LIT).select(false, idle).select(true, lit))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidHeartBlocks.ALEMBIC,
                        plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.ALEMBIC)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(VoidHeartBlocks.CRUCIBLE,
                plainVariant(ModelLocationUtils.getModelLocation(VoidHeartBlocks.CRUCIBLE))));

        var intakeTextures = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(VoidHeartBlocks.ASPECT_INTAKE, "_side"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(VoidHeartBlocks.VOIDSTONE_BRICKS))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(VoidHeartBlocks.ASPECT_INTAKE, "_top"));
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(VoidHeartBlocks.ASPECT_INTAKE,
                plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.create(VoidHeartBlocks.ASPECT_INTAKE, intakeTextures, generator.modelOutput))));
    }

    /**
     * Alone it's a plain cube, and once formed the middle of the base's hand-written model.
     */
    private void registerArmillaryCore()
    {
        var core = VoidHeartBlocks.ARMILLARY_CORE;
        var idle = plainVariant(TexturedModel.CUBE_BOTTOM_TOP.create(core, generator.modelOutput));
        var formed = plainVariant(ModelLocationUtils.getModelLocation(core, "_formed"));
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(core)
                .with(PropertyDispatch.initial(ArmillaryCoreBlock.FORMED).select(false, idle).select(true, formed)));
    }

    private void registerDepths()
    {
        generator.createTrivialCube(VoidHeartBlocks.ABYSSAL_SILT);
        generator.createTrivialCube(VoidHeartBlocks.SULFIDE_CRUST);
        generator.createTrivialCube(VoidHeartBlocks.PALLID_CORAL_BLOCK);
        generator.createTrivialCube(VoidHeartBlocks.VIOLET_CORAL_BLOCK);
        generator.createTrivialCube(VoidHeartBlocks.LUMEN_CORAL_BLOCK);
        var ventTextures = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(VoidHeartBlocks.SULFIDE_CRUST))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(VoidHeartBlocks.SULFIDE_CRUST))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(VoidHeartBlocks.SMOKER_VENT));
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(VoidHeartBlocks.SMOKER_VENT,
                plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.create(VoidHeartBlocks.SMOKER_VENT, ventTextures, generator.modelOutput))));

        registerBundle(VoidHeartBlocks.PALLID_CORAL);
        registerBundle(VoidHeartBlocks.VIOLET_CORAL);
        registerBundle(VoidHeartBlocks.LUMEN_CORAL);
        registerBundle(VoidHeartBlocks.STILL_ANEMONE);
        registerSurfaceGrowth(VoidHeartBlocks.GLOW_ALGAE);
        registerSurfaceGrowth(VoidHeartBlocks.BARNACLE_CRUST);
        registerStrand(VoidHeartBlocks.STILL_KELP);
        registerStrand(VoidHeartBlocks.DRIFT_ALGAE);

        generator.createDoublePlant(VoidHeartBlocks.GIANT_TUBE_WORMS, BlockModelGenerators.PlantType.NOT_TINTED);
        generator.registerSimpleFlatItemModel(VoidHeartBlocks.GIANT_TUBE_WORMS, "_top");
    }

    /**
     * Lying flat against the face it grows from, turned the same way a cross is by {@link #registerBundle}.
     */
    private void registerSurfaceGrowth(Block growth)
    {
        var model = plainVariant(SURFACE_GROWTH_TEMPLATE.create(growth, TextureMapping.cross(growth), generator.modelOutput));
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(growth, model).with(BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING));
        generator.registerSimpleFlatItemModel(growth);
    }

    /**
     * A cross per block of the column, with its own texture at the end of it.
     */
    private void registerStrand(Block strand)
    {
        var body = plainVariant(ModelTemplates.CROSS.create(strand, TextureMapping.cross(strand), generator.modelOutput));
        var tip = plainVariant(ModelTemplates.CROSS.createWithSuffix(strand, "_tip", TextureMapping.cross(TextureMapping.getBlockTexture(strand, "_tip")), generator.modelOutput));
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(strand)
                .with(PropertyDispatch.initial(StrandPlantBlock.TIP).select(false, body).select(true, tip)));
        generator.registerSimpleFlatItemModel(strand, "_tip");
    }

    /**
     * A cross turned to the face it grows from, shown flat as an item.
     */
    private void registerBundle(Block bundle)
    {
        generator.createAmethystCluster(bundle);
        generator.registerSimpleFlatItemModel(bundle);
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

    /**
     * A flask drawn flat in screens and as its 3D shape everywhere else, so it stands in the athanor's mouth and on a block.
     */
    private static void generateFlask(ItemModelGenerators generator, Item item, String shape)
    {
        var flat = ItemModelUtils.plainModel(generator.createFlatItemModel(item, ModelTemplates.FLAT_ITEM));
        var template = new ModelTemplate(Optional.of(Identifier.fromNamespaceAndPath(MODID, "item/flask/" + shape)), Optional.of("_3d"), FLASK_SKIN);
        var skin = new TextureMapping().put(FLASK_SKIN, new Material(Identifier.fromNamespaceAndPath(MODID, "item/flask/" + BuiltInRegistries.ITEM.getKey(item).getPath())));
        var solid = ItemModelUtils.plainModel(template.create(item, skin, generator.modelOutput));
        generator.itemModelOutput.accept(item, ItemModelUtils.select(new DisplayContext(), solid, ItemModelUtils.when(ItemDisplayContext.GUI, flat)));
    }

    @Override
    public void generateItemModels(ItemModelGenerators generator)
    {
        generator.generateFlatItem(VoidHeartItems.RAVENOUS_GOLD_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.ARROGANT_IRON_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.AZURIUM_SHARD, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.RUBRUM_SHARD, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.MEMORIAM_SHARD, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.KERNACIAN_CORE, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.SEETHING_STEEL_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.COVETOUS_COPPER_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.LISTLESS_LEAD_INGOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.WRIGGLING_BRAIN, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.HEARTWOOD_KNOT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.SALT, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.QUICKSILVER, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.WRAITH_INK, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.WRAITH_VEIL, ModelTemplates.FLAT_ITEM);
        generateFlask(generator, VoidHeartItems.SEARING_OIL, "oil");
        generateFlask(generator, VoidHeartItems.CONCUSSIVE_OIL, "oil");
        generateFlask(generator, VoidHeartItems.ARCANE_OIL, "oil");
        generateFlask(generator, VoidHeartItems.KERNACIAN_OIL, "oil");
        generateFlask(generator, VoidHeartItems.ARROGANT_IRON_FLASK, "round");
        generateFlask(generator, VoidHeartItems.RAVENOUS_GOLD_FLASK, "round");
        generateFlask(generator, VoidHeartItems.SEETHING_STEEL_FLASK, "round");
        generateFlask(generator, VoidHeartItems.COVETOUS_COPPER_FLASK, "round");
        generateFlask(generator, VoidHeartItems.LISTLESS_LEAD_FLASK, "round");
        generateFlask(generator, VoidHeartItems.EMBER_DRAUGHT, "elixir");
        generateFlask(generator, VoidHeartItems.STILLWATER, "elixir");
        generateFlask(generator, VoidHeartItems.BULWARK_ELIXIR, "elixir");
        generateFlask(generator, VoidHeartItems.INSIGHT_ELIXIR, "elixir");
        generateFlask(generator, VoidHeartItems.CLARITY_DRAUGHT, "elixir");
        generator.generateFlatItem(VoidHeartItems.VOID_VEIN, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.VOID_MARROW, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.IDEA_ABOUT_THE_VOID, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.SPARK_OF_UNDERSTANDING, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.TAINTED_BREAKTHROUGH, ModelTemplates.FLAT_ITEM);
        VoidHeartItems.SPAWN_EGGS.forEach(egg -> generator.generateFlatItem(egg, ModelTemplates.FLAT_ITEM));
        EnergyDatagen.itemModels(generator);
        generator.generateFlatItem(VoidHeartItems.LESSER_SHIELDING_APPARATUS, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.GREATER_SHIELDING_APPARATUS, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(VoidHeartItems.SUPREME_SHIELDING_APPARATUS, ModelTemplates.FLAT_ITEM);
        VoidHeartItems.APPARATUS_COMPONENTS.values().forEach(component -> generator.generateFlatItem(component, ModelTemplates.FLAT_ITEM));

        for (var name : HAND_WRITTEN_ITEM_MODELS)
            generator.itemModelOutput.accept(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MODID, name)), ItemModelUtils.plainModel(itemModel(name)));
        for (var prop : DaedalusProp.values())
            generator.itemModelOutput.accept(VoidHeartBlocks.daedalusProp(prop).asItem(), ItemModelUtils.plainModel(itemModel(prop.id())));

        // Drawn by GeckoLib with the block model, the base model only provides display transforms
        generator.itemModelOutput.accept(VoidHeartBlocks.RAVENOUS_COLLECTOR.asItem(),
                ItemModelUtils.specialModel(itemModel("ravenous_collector"), new GeckolibItemSpecialRenderer.Unbaked<>()));

        // Drawn like the placed block, the base model only provides display transforms
        generator.itemModelOutput.accept(VoidHeartBlocks.MALIGNANT_TORMENTER.asItem(),
                ItemModelUtils.specialModel(itemModel("malignant_tormenter"), new MalignantTormenterItemRender.Unbaked()));

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
