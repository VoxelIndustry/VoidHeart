package net.voxelindustry.voidheart.data.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.voxelindustry.voidheart.common.content.essence.GreatRavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Part;
import net.voxelindustry.voidheart.common.content.essence.StorageFill;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Writes the Great Ravenous Storage's models from {@link GreatStorageLayout}: the loose block, and the north-west quarter
 * of the cube for each fill, lower and upper, turned to the other corners by its blockstate.
 */
final class GreatStorageModels
{
    private static final Map<Character, String> SIDES = Map.of('n', "north", 's', "south", 'e', "east", 'w', "west", 'u', "up", 'd', "down");
    // The Ravenous Storage's textures
    private static final Map<String, String> TEXTURES = new TreeMap<>(Map.of(
            "frame", "voidstone_tile_small_carved",
            "body", "voidstone_polished",
            "trim", "cut_ravenous_gold",
            "pillar", "voidstone_pillar_side",
            "cap", "ravenous_gold_block"));
    private static final List<VariantMutator> TURNS = List.of(variant -> variant, BlockModelGenerators.Y_ROT_90, BlockModelGenerators.Y_ROT_180,
            BlockModelGenerators.Y_ROT_270);

    private GreatStorageModels()
    {
    }

    static void generate(BlockModelGenerators generator)
    {
        var block = VoidHeartBlocks.GREAT_RAVENOUS_STORAGE;
        var loose = ModelLocationUtils.getModelLocation(block);
        generator.modelOutput.accept(loose, () -> model(GreatStorageLayout.loose()));

        var dispatch = PropertyDispatch.initial(GreatRavenousStorageBlock.PART, GreatRavenousStorageBlock.FILL);
        for (var fill = 0; fill <= StorageFill.MAX_FILL; fill++)
        {
            var currentFill = fill;
            var lower = ModelLocationUtils.getModelLocation(block, "_lower_" + fill);
            var upper = ModelLocationUtils.getModelLocation(block, "_upper_" + fill);
            generator.modelOutput.accept(lower, () -> model(GreatStorageLayout.quarter(false, currentFill)));
            generator.modelOutput.accept(upper, () -> model(GreatStorageLayout.quarter(true, currentFill)));

            for (var part : Part.values())
            {
                if (part == Part.LOOSE)
                    dispatch.select(part, fill, plainVariant(loose));
                else
                    dispatch.select(part, fill, plainVariant(part.isUpper() ? upper : lower).with(TURNS.get(part.quarterTurns())));
            }
        }
        generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
        generator.registerSimpleItemModel(block, loose);
    }

    private static JsonObject model(List<GreatStorageLayout.Box> boxes)
    {
        var elements = new JsonArray();
        for (var box : boxes)
        {
            var faces = new JsonObject();
            for (var side : box.faces().toCharArray())
            {
                var face = new JsonObject();
                face.addProperty("texture", "#" + box.texture());
                faces.add(SIDES.get(side), face);
            }
            var element = new JsonObject();
            element.add("from", vector(box.x0(), box.y0(), box.z0()));
            element.add("to", vector(box.x1(), box.y1(), box.z1()));
            element.add("faces", faces);
            elements.add(element);
        }

        var textures = new JsonObject();
        textures.addProperty("particle", MODID + ":block/" + TEXTURES.get("frame"));
        TEXTURES.forEach((name, texture) -> textures.addProperty(name, MODID + ":block/" + texture));
        var model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonArray vector(double x, double y, double z)
    {
        var vector = new JsonArray();
        for (var value : new double[] { x, y, z })
        {
            if (value == Math.rint(value))
                vector.add((int) value);
            else
                vector.add(value);
        }
        return vector;
    }
}
