package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen for the Twin Ventricle, handed off through EnergyDatagen since the chamber runs on the energy grid. The cradle's hollow and the basin's
 * bowl are hand-written in assets/voidheart/models/block.
 */
public final class TwinVentricleDatagen
{
    private TwinVentricleDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(TwinVentricleContent.HEART_CRADLE, plainVariant(model("block/heart_cradle"))));
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(TwinVentricleContent.VENTRICLE_BASIN, plainVariant(model("block/ventricle_basin"))));
        cube(generators, TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE, "rubrum_veined_voidstone");
        cube(generators, TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE, "azurium_veined_voidstone");
        cube(generators, TwinVentricleContent.RAVENOUS_GOLD_VALVE, "ravenous_gold_valve");
    }

    private static void cube(BlockModelGenerators generators, Block block, String texture)
    {
        var model = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + texture))),
                generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, plainVariant(model)));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(TwinVentricleContent.HEART_CRADLE.asItem(), ItemModelUtils.plainModel(model("block/heart_cradle")));
        generators.itemModelOutput.accept(TwinVentricleContent.VENTRICLE_BASIN.asItem(), ItemModelUtils.plainModel(model("block/ventricle_basin")));
        generators.itemModelOutput.accept(TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE.asItem(), ItemModelUtils.plainModel(model("block/rubrum_veined_voidstone")));
        generators.itemModelOutput.accept(TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE.asItem(), ItemModelUtils.plainModel(model("block/azurium_veined_voidstone")));
        generators.itemModelOutput.accept(TwinVentricleContent.RAVENOUS_GOLD_VALVE.asItem(), ItemModelUtils.plainModel(model("block/ravenous_gold_valve")));
    }

    /**
     * The cradle is an altar recipe around a Ravenous Gold block, holding both sides' shards and Memoriam. The rest is crafted from polished
     * voidstone, gold and the two shards, so no part of the chamber comes only as a drop.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        var output = recipes.output();
        var voidstone = VoidHeartBlocks.VOIDSTONE_POLISHED;
        var gold = VoidHeartItems.RAVENOUS_GOLD_INGOT;

        recipes.altar("heart_cradle", TwinVentricleContent.HEART_CRADLE, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK,
                VoidHeartItems.MEMORIAM_SHARD, VoidHeartItems.MEMORIAM_SHARD,
                VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD,
                VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD,
                voidstone, voidstone);

        provider.shaped(RecipeCategory.MISC, TwinVentricleContent.VENTRICLE_BASIN)
                .pattern("V V").pattern("VGV").pattern("VVV")
                .define('V', voidstone)
                .define('G', gold)
                .unlockedBy(provider.getHasName(gold), provider.has(gold))
                .save(output);

        provider.shaped(RecipeCategory.BUILDING_BLOCKS, TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE, 4)
                .pattern(" V ").pattern("VSV").pattern(" V ")
                .define('V', voidstone)
                .define('S', VoidHeartItems.RUBRUM_SHARD)
                .unlockedBy(provider.getHasName(VoidHeartItems.RUBRUM_SHARD), provider.has(VoidHeartItems.RUBRUM_SHARD))
                .save(output);

        provider.shaped(RecipeCategory.BUILDING_BLOCKS, TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE, 4)
                .pattern(" V ").pattern("VSV").pattern(" V ")
                .define('V', voidstone)
                .define('S', VoidHeartItems.AZURIUM_SHARD)
                .unlockedBy(provider.getHasName(VoidHeartItems.AZURIUM_SHARD), provider.has(VoidHeartItems.AZURIUM_SHARD))
                .save(output);

        provider.shaped(RecipeCategory.MISC, TwinVentricleContent.RAVENOUS_GOLD_VALVE)
                .pattern("VGV").pattern("G G").pattern("VGV")
                .define('V', voidstone)
                .define('G', gold)
                .unlockedBy(provider.getHasName(gold), provider.has(gold))
                .save(output);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(TwinVentricleContent.HEART_CRADLE);
        loot.dropSelf(TwinVentricleContent.VENTRICLE_BASIN);
        loot.dropSelf(TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE);
        loot.dropSelf(TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE);
        loot.dropSelf(TwinVentricleContent.RAVENOUS_GOLD_VALVE);
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
