package net.voxelindustry.voidheart.common.energy.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.render.energy.storage.RubrumChargeProperty;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for W2 Rubrum storage, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class RubrumDatagen
{
    private RubrumDatagen()
    {
    }

    private static Material texture(String path)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + path));
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        var core = RubrumContent.RUBRUM_CONSTRUCT_CORE;
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(core,
                plainVariant(ModelTemplates.CUBE_ALL.create(core, TextureMapping.cube(texture("rubrum_construct_core")), generators.modelOutput))));

        var frame = RubrumContent.RUBRUM_CONSTRUCT_FRAME;
        var loose = ModelTemplates.CUBE_ALL.create(frame, TextureMapping.cube(texture("rubrum_construct_frame")), generators.modelOutput);
        var dispatch = PropertyDispatch.initial(ConstructFrameBlock.PART);
        for (var part : FramePart.values())
        {
            if (part == FramePart.LOOSE)
            {
                dispatch.select(part, plainVariant(loose));
                continue;
            }
            var model = ModelLocationUtils.getModelLocation(frame, "_" + part.getSerializedName());
            generators.modelOutput.accept(model, () -> frameModel(part));
            dispatch.select(part, plainVariant(model));
        }
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(frame).with(dispatch));

        // Hand-written model, a plinth with a gold clasp
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(RubrumContent.CELL_SOCKET,
                plainVariant(ModelLocationUtils.getModelLocation(RubrumContent.CELL_SOCKET))));
    }

    /**
     * A formed frame block's model, built from {@link FrameGeometry}. Beam ends lie against the next piece of the frame
     * and are left out; outer faces cull against whatever covers them outside the cube, never against the frame itself.
     */
    private static JsonObject frameModel(FramePart part)
    {
        var textures = new JsonObject();
        textures.addProperty("particle", MODID + ":block/rubrum_construct_frame");
        textures.addProperty("pillar", MODID + ":block/rubrum_construct_pillar");
        textures.addProperty("beam", MODID + ":block/rubrum_construct_beam");
        textures.addProperty("gold", MODID + ":block/cut_ravenous_gold");

        var elements = new JsonArray();
        int[] signs = {part.x(), part.y(), part.z()};
        for (var box : FrameGeometry.boxes(part))
        {
            var element = new JsonObject();
            element.add("from", vector(box.x0(), box.y0(), box.z0()));
            element.add("to", vector(box.x1(), box.y1(), box.z1()));
            var faces = new JsonObject();
            for (var face : Direction.values())
            {
                if (face.getAxis() == box.runs())
                    continue;
                var json = new JsonObject();
                json.addProperty("texture", "#" + box.skin().name().toLowerCase(java.util.Locale.ROOT));
                if (box.skin() != FrameGeometry.Skin.GOLD)
                    json.add("uv", uv(box, face));
                var sign = signs[face.getAxis().ordinal()];
                var flush = face.getAxisDirection().getStep() > 0 ? box.max(face.getAxis()) == 16 : box.min(face.getAxis()) == 0;
                if (flush && sign == face.getAxisDirection().getStep())
                    json.addProperty("cullface", face.getSerializedName());
                faces.add(face.getSerializedName(), json);
            }
            element.add("faces", faces);
            elements.add(element);
        }

        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * The middle slice of the texture across the beam, so the pillar's vein runs down the middle of each face, and the
     * box's own span along it.
     */
    private static JsonArray uv(FrameGeometry.Box box, Direction face)
    {
        var across = (16 - FrameGeometry.BEAM) / 2;
        var runs = box.runs();
        if (runs == Direction.Axis.Y)
            return vector(across, 16 - box.y1(), 16 - across, 16 - box.y0());
        // A horizontal beam: along it on u, across it on v
        var along = runs == null ? Direction.Axis.X : runs;
        return vector(box.min(along), across, box.max(along), 16 - across);
    }

    private static JsonArray vector(int... values)
    {
        var array = new JsonArray();
        for (var value : values)
            array.add(value);
        return array;
    }

    /**
     * The cell picks one of four textures by how full it is: empty only when it holds nothing, then the nearest of a
     * third, two thirds and full.
     */
    public static void itemModels(ItemModelGenerators generators)
    {
        RubrumChargeProperty.register();
        var cell = RubrumContent.RUBRUM_CELL;
        var empty = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, "_empty", ModelTemplates.FLAT_ITEM));
        var third = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, "_third", ModelTemplates.FLAT_ITEM));
        var twoThirds = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, "_two_thirds", ModelTemplates.FLAT_ITEM));
        var full = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, "_full", ModelTemplates.FLAT_ITEM));
        generators.itemModelOutput.accept(cell, ItemModelUtils.rangeSelect(new RubrumChargeProperty(), empty,
                ItemModelUtils.override(third, 0.0001F),
                ItemModelUtils.override(twoThirds, 0.5F),
                ItemModelUtils.override(full, 0.84F)));
    }

    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        var output = recipes.output();
        var voidstone = VoidHeartBlocks.VOIDSTONE_POLISHED;
        var gold = VoidHeartItems.RAVENOUS_GOLD_INGOT;

        provider.shaped(RecipeCategory.MISC, RubrumContent.RUBRUM_CONSTRUCT_FRAME, 6)
                .pattern("VGV").pattern("V V").pattern("VGV")
                .define('V', voidstone)
                .define('G', gold)
                .unlockedBy(provider.getHasName(voidstone), provider.has(voidstone))
                .save(output);

        provider.shaped(RecipeCategory.MISC, RubrumContent.CELL_SOCKET)
                .pattern(" G ").pattern("VVV")
                .define('V', voidstone)
                .define('G', gold)
                .unlockedBy(provider.getHasName(gold), provider.has(gold))
                .save(output);

        provider.shaped(RecipeCategory.MISC, RubrumContent.RUBRUM_CELL)
                .pattern(" G ").pattern("VBV").pattern(" G ")
                .define('V', voidstone)
                .define('G', gold)
                .define('B', VoidHeartBlocks.SMALL_RUBRUM_BUNDLE)
                .unlockedBy(provider.getHasName(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE), provider.has(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE))
                .save(output);

        // The core: a rubrum shard at the center of voidstone, gold and both crystals
        recipes.altar("rubrum_construct_core", RubrumContent.RUBRUM_CONSTRUCT_CORE, VoidHeartItems.RUBRUM_SHARD,
                voidstone, voidstone, voidstone, voidstone,
                gold, gold,
                VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.AZURIUM_SHARD);
    }

    /**
     * The core drops itself through RubrumConstructBlock.getDrops, which keeps its contents; the table is a fallback.
     */
    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_CORE);
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_FRAME);
        loot.dropSelf(RubrumContent.CELL_SOCKET);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
