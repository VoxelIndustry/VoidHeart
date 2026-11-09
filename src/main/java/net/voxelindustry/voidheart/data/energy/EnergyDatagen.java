package net.voxelindustry.voidheart.data.energy;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineDatagen;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridDatagen;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneDatagen;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorDatagen;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonDatagen;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeDatagen;
import net.voxelindustry.voidheart.common.energy.storage.RubrumDatagen;
import net.voxelindustry.voidheart.common.energy.tear.VoidTearDatagen;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The datagen providers each make one call here, which hands off to every package's datagen class.
 */
public final class EnergyDatagen
{
    private EnergyDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        placeholderCube(generators, CreativeEnergyContent.CREATIVE_VOID_WELL, "voidstone_chiseled");
        placeholderColumn(generators, CreativeEnergyContent.CREATIVE_LEY_STONE, "ley_anchor_side_active", "ley_anchor_top_active");

        VoidGridDatagen.blockModels(generators);
        RubrumDatagen.blockModels(generators);
        LeyStoneDatagen.blockModels(generators);
        RavenousEngineDatagen.blockModels(generators);
        LeySpikeDatagen.blockModels(generators);
        VoidTearDatagen.blockModels(generators);
        ContravoidReactorDatagen.blockModels(generators);
        DeepSiphonDatagen.blockModels(generators);
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        VoidGridDatagen.itemModels(generators);
        RubrumDatagen.itemModels(generators);
        LeyStoneDatagen.itemModels(generators);
        RavenousEngineDatagen.itemModels(generators);
        LeySpikeDatagen.itemModels(generators);
        VoidTearDatagen.itemModels(generators);
        ContravoidReactorDatagen.itemModels(generators);
        DeepSiphonDatagen.itemModels(generators);
    }

    public static void recipes(EnergyRecipes recipes)
    {
        VoidGridDatagen.recipes(recipes);
        RubrumDatagen.recipes(recipes);
        LeyStoneDatagen.recipes(recipes);
        RavenousEngineDatagen.recipes(recipes);
        LeySpikeDatagen.recipes(recipes);
        VoidTearDatagen.recipes(recipes);
        ContravoidReactorDatagen.recipes(recipes);
        DeepSiphonDatagen.recipes(recipes);
    }

    /**
     * The creative test blocks have no table, since they never drop.
     */
    public static void loot(BlockLootSubProvider loot)
    {
        VoidGridDatagen.loot(loot);
        RubrumDatagen.loot(loot);
        LeyStoneDatagen.loot(loot);
        RavenousEngineDatagen.loot(loot);
        LeySpikeDatagen.loot(loot);
        VoidTearDatagen.loot(loot);
        ContravoidReactorDatagen.loot(loot);
        DeepSiphonDatagen.loot(loot);
    }

    public static void tags(EnergyTags tags)
    {
        VoidGridDatagen.tags(tags);
        RubrumDatagen.tags(tags);
        LeyStoneDatagen.tags(tags);
        RavenousEngineDatagen.tags(tags);
        LeySpikeDatagen.tags(tags);
        VoidTearDatagen.tags(tags);
        ContravoidReactorDatagen.tags(tags);
        DeepSiphonDatagen.tags(tags);
    }

    /**
     * A cube wearing an existing block texture, until the package draws its own.
     */
    public static void placeholderCube(BlockModelGenerators generators, Block block, String texture)
    {
        var model = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(blockTexture(texture)), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, plainVariant(model)));
    }

    public static void placeholderColumn(BlockModelGenerators generators, Block block, String side, String end)
    {
        var model = ModelTemplates.CUBE_COLUMN.create(block, TextureMapping.column(blockTexture(side), blockTexture(end)), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, plainVariant(model)));
    }

    /**
     * A flat item wearing an existing item texture, until the package draws its own.
     */
    public static void placeholderItem(ItemModelGenerators generators, Item item, String texture)
    {
        var model = ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(new Material(Identifier.fromNamespaceAndPath(MODID, "item/" + texture))), generators.modelOutput);
        generators.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
    }

    private static Material blockTexture(String texture)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + texture));
    }
}
