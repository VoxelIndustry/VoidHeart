package net.voxelindustry.voidheart.data.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.content.underway.UnderwayEmbrasureBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwayHatchBlock;
import net.voxelindustry.voidheart.common.content.underway.UnderwayTrackBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPropBox;

import java.util.Map;
import java.util.TreeSet;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/** Writes each Underway prop's model from its boxes, with its four facings and an item that shows the same model, and the blocks the Underway is built of. */
final class UnderwayPropModels
{
    private static final Map<Character, String> SIDES = Map.of('n', "north", 's', "south", 'e', "east", 'w', "west", 'u', "up", 'd', "down");
    private static final String TEXTURES = MODID + ":block/underway/";

    private UnderwayPropModels()
    {
    }

    static void generate(BlockModelGenerators generator)
    {
        cube(generator, VoidHeartBlocks.UNDERWAY_CONCRETE, "concrete");
        cube(generator, VoidHeartBlocks.UNDERWAY_STAINED_CONCRETE, "concrete_stained");
        cube(generator, VoidHeartBlocks.UNDERWAY_ARMOUR, "steel_plate");
        cube(generator, VoidHeartBlocks.UNDERWAY_FORMED_CONCRETE, "concrete_formed");
        cube(generator, VoidHeartBlocks.UNDERWAY_CRACKED_CONCRETE, "concrete_cracked");
        cube(generator, VoidHeartBlocks.UNDERWAY_HAZARD_ARMOUR, "armour_hazard");
        cube(generator, VoidHeartBlocks.UNDERWAY_BLAST_GATE, "blast_gate");
        cube(generator, VoidHeartBlocks.UNDERWAY_GRATING, "grating");
        cube(generator, VoidHeartBlocks.UNDERWAY_BALLAST, "ballast");
        cube(generator, VoidHeartBlocks.UNDERWAY_RUBBLE, "rubble");
        track(generator, VoidHeartBlocks.UNDERWAY_TRUNK_RAIL, "trunk_rail");
        track(generator, VoidHeartBlocks.UNDERWAY_TRACK_TIE, "track_tie");
        track(generator, VoidHeartBlocks.UNDERWAY_FEEDER_RAIL, "feeder_rail");
        generator.createDoor(VoidHeartBlocks.UNDERWAY_DOOR);

        var stairs = VoidHeartBlocks.UNDERWAY_STEEL_STAIRS;
        var tread = new Material(Identifier.parse(TEXTURES + "tread"));
        var treads = new TextureMapping().put(TextureSlot.BOTTOM, tread).put(TextureSlot.TOP, tread).put(TextureSlot.SIDE, tread);
        var straight = ModelTemplates.STAIRS_STRAIGHT.create(stairs, treads, generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createStairs(stairs, plainVariant(ModelTemplates.STAIRS_INNER.create(stairs, treads, generator.modelOutput)),
                plainVariant(straight), plainVariant(ModelTemplates.STAIRS_OUTER.create(stairs, treads, generator.modelOutput))));
        generator.registerSimpleItemModel(stairs, straight);

        var hatch = VoidHeartBlocks.UNDERWAY_HATCH;
        var plating = new Material(Identifier.parse(TEXTURES + "hatch_plate"));
        var edging = new Material(Identifier.parse(TEXTURES + "hatch_edge"));
        var plate = plainVariant(ModelTemplates.CUBE_ALL.create(hatch, TextureMapping.cube(plating), generator.modelOutput));
        var edge = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(hatch, "_edge", new TextureMapping().put(TextureSlot.SIDE, plating).put(TextureSlot.TOP, edging).put(TextureSlot.BOTTOM, edging), generator.modelOutput));
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(hatch).with(PropertyDispatch.initial(UnderwayHatchBlock.FACING)
                .select(Direction.UP, plate).select(Direction.DOWN, plate).select(Direction.NORTH, edge).select(Direction.EAST, edge.with(BlockModelGenerators.Y_ROT_90))
                .select(Direction.SOUTH, edge.with(BlockModelGenerators.Y_ROT_180)).select(Direction.WEST, edge.with(BlockModelGenerators.Y_ROT_270))));
        generator.registerSimpleItemModel(hatch, ModelLocationUtils.getModelLocation(hatch));

        var embrasure = VoidHeartBlocks.UNDERWAY_EMBRASURE;
        var slit = ModelLocationUtils.getModelLocation(embrasure);
        generator.modelOutput.accept(slit, UnderwayPropModels::embrasure);
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(embrasure).with(PropertyDispatch.initial(UnderwayEmbrasureBlock.AXIS)
                .select(Direction.Axis.Z, plainVariant(slit)).select(Direction.Axis.X, plainVariant(slit).with(BlockModelGenerators.Y_ROT_90))));
        generator.registerSimpleItemModel(embrasure, slit);
        for (var prop : UnderwayProp.values())
        {
            var block = VoidHeartBlocks.underwayProp(prop);
            var model = ModelLocationUtils.getModelLocation(block);
            generator.modelOutput.accept(model, () -> model(prop));
            generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, plainVariant(model)).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            generator.registerSimpleItemModel(block, model);
        }
    }

    private static void cube(BlockModelGenerators generator, Block block, String texture)
    {
        var model = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(new Material(Identifier.parse(TEXTURES + texture))), generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, plainVariant(model)));
        generator.registerSimpleItemModel(block, model);
    }

    /** A track lying flat, drawn along z and turned for x. */
    private static void track(BlockModelGenerators generator, Block block, String texture)
    {
        var model = ModelTemplates.RAIL_FLAT.create(block, TextureMapping.rail(new Material(Identifier.parse(TEXTURES + texture))), generator.modelOutput);
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(PropertyDispatch.initial(UnderwayTrackBlock.AXIS)
                .select(Direction.Axis.Z, plainVariant(model)).select(Direction.Axis.X, plainVariant(model).with(BlockModelGenerators.Y_ROT_90))));
        generator.registerSimpleItemModel(block, model);
    }

    /** A block of armour around a slit looked through along z. */
    private static JsonObject embrasure()
    {
        int bottom = UnderwayEmbrasureBlock.SLIT_BOTTOM;
        int top = UnderwayEmbrasureBlock.SLIT_TOP;
        int jamb = UnderwayEmbrasureBlock.JAMB;
        int[][] boxes = { { 0, 0, 0, 16, bottom, 16 }, { 0, top, 0, 16, 16, 16 }, { 0, bottom, 0, jamb, top, 16 }, { 16 - jamb, bottom, 0, 16, top, 16 } };
        var elements = new JsonArray();
        for (int[] box : boxes)
        {
            var faces = new JsonObject();
            for (var side : SIDES.values())
                faces.add(side, face("plate"));
            var element = new JsonObject();
            element.add("from", vector(box[0], box[1], box[2]));
            element.add("to", vector(box[3], box[4], box[5]));
            element.add("faces", faces);
            elements.add(element);
        }
        var textures = new JsonObject();
        textures.addProperty("particle", TEXTURES + "steel_plate");
        textures.addProperty("plate", TEXTURES + "steel_plate");
        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /** Faces without a corner of their own get none written, which draws the texture where the box stands in the block. */
    private static JsonObject model(UnderwayProp prop)
    {
        var used = new TreeSet<String>();
        var elements = new JsonArray();
        for (var box : prop.boxes())
        {
            used.add(box.texture());
            var faces = new JsonObject();
            for (var side : SIDES.values())
                faces.add(side, face(box.texture()));
            for (var face : box.faces())
            {
                used.add(face.texture());
                var json = face(face.texture());
                var uv = new JsonArray();
                uv.add(face.u());
                uv.add(face.v());
                uv.add(face.u() + box.faceWidth(face.side()));
                uv.add(face.v() + box.faceHeight(face.side()));
                json.add("uv", uv);
                faces.add(SIDES.get(face.side()), json);
            }
            var element = new JsonObject();
            element.add("from", vector(box.x0(), box.y0(), box.z0()));
            element.add("to", vector(box.x1(), box.y1(), box.z1()));
            element.add("faces", faces);
            elements.add(element);
        }

        var textures = new JsonObject();
        textures.addProperty("particle", TEXTURES + prop.boxes().getFirst().texture());
        for (var name : used)
            textures.addProperty(name, TEXTURES + name);
        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonObject face(String texture)
    {
        var face = new JsonObject();
        face.addProperty("texture", "#" + texture);
        return face;
    }

    private static JsonArray vector(int x, int y, int z)
    {
        var vector = new JsonArray();
        vector.add(x);
        vector.add(y);
        vector.add(z);
        return vector;
    }
}
