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
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.voxelindustry.voidheart.client.render.energy.storage.RubrumChargeProperty;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.HabitShapedRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.common.energy.pearl.PearlContent;

import java.util.ArrayList;

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

        var wall = ModelLocationUtils.getModelLocation(RubrumContent.SOCKET_WALL);
        generators.modelOutput.accept(wall, RubrumDatagen::socketWallModel);
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(RubrumContent.SOCKET_WALL, plainVariant(wall))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        var large = RubrumContent.LARGE_RUBRUM_CELL;
        var largeDispatch = PropertyDispatch.initial(LargeRubrumCellBlock.HALF, LargeRubrumCellBlock.CHARGE);
        for (var half : DoubleBlockHalf.values())
        {
            for (var lit = 0; lit <= LargeCellCharge.SEGMENTS; lit++)
            {
                var model = ModelLocationUtils.getModelLocation(large, "_" + half.getSerializedName() + "_" + lit);
                var elements = largeCellHalf(half, lit, 0);
                generators.modelOutput.accept(model, () -> largeCellModel(elements, false));
                largeDispatch.select(half, lit, plainVariant(model));
            }
        }
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(large).with(largeDispatch));
        // The item shows both halves at once, fully lit
        var whole = largeCellHalf(DoubleBlockHalf.LOWER, LargeCellCharge.SEGMENTS, 0);
        whole.addAll(largeCellHalf(DoubleBlockHalf.UPPER, LargeCellCharge.SEGMENTS, 16));
        generators.modelOutput.accept(ModelLocationUtils.getModelLocation(large), () -> largeCellModel(whole, true));
    }

    /** Light the lit segments of a large cell's column give off in its model. */
    private static final int LARGE_CELL_EMISSION = 12;

    /**
     * One half of the Large Rubrum Cell, the same from every side: a carved plinth or cap behind a gold band, rubrum
     * pillars at the corners and two of the four segments of a crystal column between them, lit from the bottom up.
     *
     * @param lit    segments lit over the whole column
     * @param offset raises every box, to stack both halves in the item model
     */
    private static JsonArray largeCellHalf(DoubleBlockHalf half, int lit, int offset)
    {
        var elements = new JsonArray();
        var lower = half == DoubleBlockHalf.LOWER;
        // Faces where the halves meet are left out
        java.util.function.Predicate<Direction> sides = face -> face.getAxis() != Direction.Axis.Y;
        int pillarFrom = lower ? 4 : 0, pillarTo = lower ? 16 : 12;
        int[][] corners = {{2, 2}, {12, 2}, {2, 12}, {12, 12}};
        for (var corner : corners)
            elements.add(cellBox(corner[0], pillarFrom, corner[1], corner[0] + 2, pillarTo, corner[1] + 2, offset, face -> "pillar", sides, -1));

        var firstSegment = lower ? 0 : 2;
        for (var i = 0; i < 2; i++)
        {
            var y0 = lower ? 4 + i * 6 : i * 6;
            var on = firstSegment + i < lit;
            elements.add(cellBox(4, y0, 4, 12, y0 + 6, 12, offset, face -> on ? "lit" : "dim", sides, on ? LARGE_CELL_EMISSION : -1));
        }

        if (lower)
        {
            elements.add(cellBox(2, 3, 2, 14, 4, 14, offset, face -> "gold", face -> face != Direction.DOWN, -1));
            elements.add(cellBox(1, 0, 1, 15, 3, 15, offset, face -> face.getAxis() == Direction.Axis.Y ? "body" : "frame", face -> true, -1));
        }
        else
        {
            elements.add(cellBox(2, 12, 2, 14, 13, 14, offset, face -> "gold", face -> face != Direction.UP, -1));
            elements.add(cellBox(1, 13, 1, 15, 16, 15, offset, face -> face.getAxis() == Direction.Axis.Y ? "body" : "frame", face -> true, -1));
        }
        return elements;
    }

    private static JsonObject cellBox(int x0, int y0, int z0, int x1, int y1, int z1, int offset,
                                      java.util.function.Function<Direction, String> texture, java.util.function.Predicate<Direction> drawn, int emission)
    {
        var element = new JsonObject();
        element.add("from", vector(x0, y0 + offset, z0));
        element.add("to", vector(x1, y1 + offset, z1));
        if (emission >= 0)
            element.addProperty("light_emission", emission);
        var faces = new JsonObject();
        for (var face : Direction.values())
        {
            if (!drawn.test(face))
                continue;
            var json = new JsonObject();
            json.addProperty("texture", "#" + texture.apply(face));
            // The UV vanilla would give the box before it was raised, so the item's upper half samples inside its texture
            json.add("uv", switch (face)
            {
                case DOWN -> vector(x0, 16 - z1, x1, 16 - z0);
                case UP -> vector(x0, z0, x1, z1);
                case NORTH -> vector(16 - x1, 16 - y1, 16 - x0, 16 - y0);
                case SOUTH -> vector(x0, 16 - y1, x1, 16 - y0);
                case WEST -> vector(z0, 16 - y1, z1, 16 - y0);
                case EAST -> vector(16 - z1, 16 - y1, 16 - z0, 16 - y0);
            });
            // Only the plinth's bottom and the cap's top lie on the block's outside
            if (offset == 0 && (face == Direction.DOWN && y0 == 0 || face == Direction.UP && y1 == 16))
                json.addProperty("cullface", face.getSerializedName());
            faces.add(face.getSerializedName(), json);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject largeCellModel(JsonArray elements, boolean item)
    {
        var textures = new JsonObject();
        textures.addProperty("particle", MODID + ":block/large_rubrum_cell_core_lit");
        textures.addProperty("body", MODID + ":block/voidstone_polished");
        textures.addProperty("frame", MODID + ":block/voidstone_tile_small_carved");
        textures.addProperty("pillar", MODID + ":block/rubrum_construct_pillar");
        textures.addProperty("gold", MODID + ":block/cut_ravenous_gold");
        textures.addProperty("lit", MODID + ":block/large_rubrum_cell_core_lit");
        textures.addProperty("dim", MODID + ":block/large_rubrum_cell_core_dim");

        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        if (item)
            model.add("display", largeCellDisplay());
        return model;
    }

    /**
     * The two-block column shrunk to fit a slot and a hand.
     */
    private static JsonObject largeCellDisplay()
    {
        var display = new JsonObject();
        display.add("gui", transform(vector(30, 225, 0), vector(0, -3.5F, 0), 0.36F));
        display.add("ground", transform(vector(0, 0, 0), vector(0, 2, 0), 0.2F));
        display.add("fixed", transform(vector(0, 0, 0), vector(0, -3.5F, 0), 0.4F));
        display.add("thirdperson_righthand", transform(vector(75, 45, 0), vector(0, 1.5F, 0), 0.25F));
        display.add("firstperson_righthand", transform(vector(0, 45, 0), vector(0, 2, 0), 0.2F));
        display.add("firstperson_lefthand", transform(vector(0, 225, 0), vector(0, 2, 0), 0.2F));
        return display;
    }

    private static JsonObject transform(JsonArray rotation, JsonArray translation, float scale)
    {
        var json = new JsonObject();
        json.add("rotation", rotation);
        json.add("translation", translation);
        json.add("scale", vector(scale, scale, scale));
        return json;
    }

    private static JsonArray vector(float... values)
    {
        var array = new JsonArray();
        for (var value : values)
            array.add(value);
        return array;
    }

    /**
     * The socket wall facing north, built from {@link SocketWallGeometry#NICHES}: a voidstone back, a carved frame
     * around four niches veined with rubrum, and a gold ledge in each for its cell to stand on. Bars run full width
     * across and stand between them upright; faces against the back or against another bar end are left out.
     */
    private static JsonObject socketWallModel()
    {
        var textures = new JsonObject();
        textures.addProperty("particle", MODID + ":block/voidstone_polished");
        textures.addProperty("body", MODID + ":block/voidstone_polished");
        textures.addProperty("frame", MODID + ":block/voidstone_tile_small_carved");
        textures.addProperty("niche", MODID + ":block/rubrum_veined_voidstone");
        textures.addProperty("gold", MODID + ":block/cut_ravenous_gold");

        var depth = SocketWallGeometry.NICHE_DEPTH;
        var elements = new JsonArray();
        elements.add(wallBox(0, 0, depth, 16, 16, 16, face -> face == Direction.NORTH ? "niche" : "body", face -> true));

        // Bars across the whole width under, between and over the niches
        int[][] rows = {{0, 1}, {7, 9}, {15, 16}};
        for (var row : rows)
            elements.add(wallBox(0, row[0], 0, 16, row[1], depth, face -> frameTexture(face, row[0], row[1], 0, 16),
                    face -> face != Direction.SOUTH));
        // Uprights between the bars, beside each niche
        int[][] columns = {{0, 1}, {7, 9}, {15, 16}};
        int[][] spans = {{1, 7}, {9, 15}};
        for (var column : columns)
            for (var span : spans)
                elements.add(wallBox(column[0], span[0], 0, column[1], span[1], depth, face -> frameTexture(face, span[0], span[1], column[0], column[1]),
                        face -> face != Direction.SOUTH && face.getAxis() != Direction.Axis.Y));

        for (var niche : SocketWallGeometry.NICHES)
            elements.add(wallBox(niche.x0() + 1, niche.y0(), 1, niche.x1() - 1, niche.y0() + 1, depth, face -> "gold",
                    face -> face != Direction.SOUTH && face != Direction.DOWN));

        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * The carved frame on the front and inside the niches, plain voidstone where a bar meets the block's outside.
     */
    private static String frameTexture(Direction face, int y0, int y1, int x0, int x1)
    {
        if (face == Direction.NORTH)
            return "frame";
        var outside = switch (face)
        {
            case DOWN -> y0 == 0;
            case UP -> y1 == 16;
            case WEST -> x0 == 0;
            case EAST -> x1 == 16;
            default -> false;
        };
        return outside ? "body" : "frame";
    }

    private static JsonObject wallBox(int x0, int y0, int z0, int x1, int y1, int z1,
                                      java.util.function.Function<Direction, String> texture, java.util.function.Predicate<Direction> drawn)
    {
        var element = new JsonObject();
        element.add("from", vector(x0, y0, z0));
        element.add("to", vector(x1, y1, z1));
        var faces = new JsonObject();
        for (var face : Direction.values())
        {
            if (!drawn.test(face))
                continue;
            var json = new JsonObject();
            json.addProperty("texture", "#" + texture.apply(face));
            var flush = switch (face)
            {
                case DOWN -> y0 == 0;
                case UP -> y1 == 16;
                case NORTH -> z0 == 0;
                case SOUTH -> z1 == 16;
                case WEST -> x0 == 0;
                case EAST -> x1 == 16;
            };
            if (flush)
                json.addProperty("cullface", face.getSerializedName());
            faces.add(face.getSerializedName(), json);
        }
        element.add("faces", faces);
        return element;
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
        var plain = cellModel(generators, "");
        // A cell sealed around a habit bundle shows that habit in its shard, at every charge
        var cases = new ArrayList<SelectItemModel.SwitchCase<CrystalHabit>>();
        for (var habit : HabitShapedRecipe.CARRIED.stream().sorted().toList())
            cases.add(ItemModelUtils.when(habit, cellModel(generators, "_" + habit.getSerializedName())));
        generators.itemModelOutput.accept(cell, ItemModelUtils.select(new ComponentContents<>(VoidHeartDataComponents.CRYSTAL_HABIT), plain, cases));

        generators.itemModelOutput.accept(RubrumContent.LARGE_RUBRUM_CELL.asItem(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(RubrumContent.LARGE_RUBRUM_CELL)));
    }

    private static ItemModel.Unbaked cellModel(ItemModelGenerators generators, String habit)
    {
        var cell = RubrumContent.RUBRUM_CELL;
        var empty = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, habit + "_empty", ModelTemplates.FLAT_ITEM));
        var third = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, habit + "_third", ModelTemplates.FLAT_ITEM));
        var twoThirds = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, habit + "_two_thirds", ModelTemplates.FLAT_ITEM));
        var full = ItemModelUtils.plainModel(generators.createFlatItemModel(cell, habit + "_full", ModelTemplates.FLAT_ITEM));
        return ItemModelUtils.rangeSelect(new RubrumChargeProperty(), empty,
                ItemModelUtils.override(third, 0.0001F),
                ItemModelUtils.override(twoThirds, 0.5F),
                ItemModelUtils.override(full, 0.84F));
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

        var socket = RubrumContent.CELL_SOCKET;
        provider.shaped(RecipeCategory.MISC, RubrumContent.SOCKET_WALL)
                .pattern("VSV").pattern("SGS").pattern("VSV")
                .define('V', voidstone)
                .define('S', socket)
                .define('G', gold)
                .unlockedBy(provider.getHasName(socket), provider.has(socket))
                .save(output);

        provider.shaped(RecipeCategory.MISC, RubrumContent.RUBRUM_CELL)
                .pattern(" G ").pattern("VBV").pattern(" G ")
                .define('V', voidstone)
                .define('G', gold)
                .define('B', VoidHeartBlocks.SMALL_RUBRUM_BUNDLE)
                .unlockedBy(provider.getHasName(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE), provider.has(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE))
                // The cell takes the habit of the bundle sealed in it
                .save(HabitShapedRecipe.carryingHabit(output));

        // The core: a rubrum shard at the center of voidstone, gold and both crystals
        recipes.altar("rubrum_construct_core", RubrumContent.RUBRUM_CONSTRUCT_CORE, VoidHeartItems.RUBRUM_SHARD,
                voidstone, voidstone, voidstone, voidstone,
                gold, gold,
                VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.AZURIUM_SHARD);

        // The large cell grows around a collapsing pearl, sealed in large rubrum and gold
        var bundle = VoidHeartBlocks.LARGE_RUBRUM_BUNDLE;
        recipes.altar("large_rubrum_cell", RubrumContent.LARGE_RUBRUM_CELL, PearlContent.COLLAPSING_RUBRUM_PEARL,
                bundle, bundle, bundle, bundle,
                VoidHeartBlocks.RAVENOUS_GOLD_BLOCK, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK,
                voidstone, voidstone);
    }

    /**
     * The core drops itself through RubrumConstructBlock.getDrops, which keeps its contents; the table is a fallback.
     */
    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_CORE);
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_FRAME);
        loot.dropSelf(RubrumContent.CELL_SOCKET);
        loot.dropSelf(RubrumContent.SOCKET_WALL);
        // Only the lower half drops the cell, and never its charge
        loot.add(RubrumContent.LARGE_RUBRUM_CELL, loot::createDoorTable);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
