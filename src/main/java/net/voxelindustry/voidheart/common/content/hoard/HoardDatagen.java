package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Hoard's models, recipes and loot, called from the datagen providers through EnergyDatagen.
 */
public final class HoardDatagen
{
    private HoardDatagen()
    {
    }

    private static Material blockTexture(String texture)
    {
        return new Material(Identifier.fromNamespaceAndPath(MODID, "block/" + texture));
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        var fold = ModelTemplates.CUBE_ALL.create(HoardContent.FOLD, TextureMapping.cube(blockTexture("fold")), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(HoardContent.FOLD, plainVariant(fold)));

        var aspectFold = ModelTemplates.CUBE_ALL.create(HoardContent.ASPECT_FOLD, TextureMapping.cube(blockTexture("aspect_fold")), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(HoardContent.ASPECT_FOLD, plainVariant(aspectFold)));

        var coffer = ModelTemplates.CUBE_BOTTOM_TOP.create(HoardContent.HOARD_COFFER, new TextureMapping()
                .put(TextureSlot.SIDE, blockTexture("hoard_coffer_side"))
                .put(TextureSlot.TOP, blockTexture("hoard_coffer_top"))
                .put(TextureSlot.BOTTOM, blockTexture("voidstone_polished")), generators.modelOutput);
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(HoardContent.HOARD_COFFER, plainVariant(coffer)));
    }

    /**
     * Greed keeps, so both are worked in Covetous Copper. The Fold carries through azurium, and folds a place onto the pocket through a Void Pearl.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("hoard_coffer", HoardContent.HOARD_COFFER, Blocks.BARREL,
                VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT,
                VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
        recipes.altar("fold", HoardContent.FOLD, Blocks.HOPPER,
                VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT,
                VoidHeartItems.VOID_PEARL, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, CrystalBundles.shard(CrystalKind.AZURIUM));
        // Essence is gluttony's, so the Fold is recast in Ravenous Gold
        recipes.altar("aspect_fold", HoardContent.ASPECT_FOLD, HoardContent.FOLD,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL, CrystalBundles.shard(CrystalKind.AZURIUM), CrystalBundles.shard(CrystalKind.AZURIUM));
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(HoardContent.HOARD_COFFER);
        loot.dropSelf(HoardContent.FOLD);
        loot.dropSelf(HoardContent.ASPECT_FOLD);
    }
}
