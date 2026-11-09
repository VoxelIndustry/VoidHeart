package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

/**
 * Datagen hand-off for W4 Standing stones and wards, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * The block models are hand-written in assets/voidheart/models/block, since the menhir's channel lines up across its cuboids.
 */
public final class LeyStoneDatagen
{
    private LeyStoneDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        var stone = LeyStoneContent.LEY_STONE;
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(stone)
                .with(PropertyDispatch.initial(LeyStoneBlock.HALF)
                        .select(DoubleBlockHalf.LOWER, plainVariant(ModelLocationUtils.getModelLocation(stone)))
                        .select(DoubleBlockHalf.UPPER, plainVariant(ModelLocationUtils.getModelLocation(stone, "_upper"))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(LeyStoneContent.LEY_WARD,
                        plainVariant(ModelLocationUtils.getModelLocation(LeyStoneContent.LEY_WARD)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(LeyStoneContent.LEY_GROWER,
                plainVariant(ModelLocationUtils.getModelLocation(LeyStoneContent.LEY_GROWER))));
    }

    /**
     * Each item shows its block model, whose display transforms shrink the two-block menhir to fit a slot.
     */
    public static void itemModels(ItemModelGenerators generators)
    {
    }

    /**
     * Plain overworld stone and lapis, the earth's own blue, so nothing here reads as void. Research gates them through their ids, which match the blocks.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        provider.shaped(RecipeCategory.DECORATIONS, LeyStoneContent.LEY_STONE)
                .pattern("S").pattern("L").pattern("S")
                .define('S', Blocks.STONE)
                .define('L', Items.LAPIS_LAZULI)
                .unlockedBy(provider.getHasName(Items.LAPIS_LAZULI), provider.has(Items.LAPIS_LAZULI))
                .save(recipes.output());

        provider.shaped(RecipeCategory.DECORATIONS, LeyStoneContent.LEY_WARD)
                .pattern(" L ").pattern("SAS").pattern("BBB")
                .define('L', Items.LAPIS_LAZULI)
                .define('S', Blocks.STONE)
                .define('A', Items.AMETHYST_SHARD)
                .define('B', Blocks.STONE_SLAB)
                .unlockedBy(provider.getHasName(Items.LAPIS_LAZULI), provider.has(Items.LAPIS_LAZULI))
                .save(recipes.output());

        provider.shaped(RecipeCategory.DECORATIONS, LeyStoneContent.LEY_GROWER)
                .pattern("L L").pattern("SMS").pattern("SSS")
                .define('L', Items.LAPIS_LAZULI)
                .define('S', Blocks.STONE)
                .define('M', Blocks.MOSS_BLOCK)
                .unlockedBy(provider.getHasName(Items.LAPIS_LAZULI), provider.has(Items.LAPIS_LAZULI))
                .save(recipes.output());
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.add(LeyStoneContent.LEY_STONE, loot::createDoorTable);
        loot.dropSelf(LeyStoneContent.LEY_WARD);
        loot.dropSelf(LeyStoneContent.LEY_GROWER);
    }

    public static void tags(EnergyTags tags)
    {
        tags.builder(LeyStoneContent.GROWER_BOOSTS)
                .addOptionalTag(BlockTags.CROPS)
                .addOptionalTag(BlockTags.SAPLINGS)
                .add(key(Blocks.SUGAR_CANE))
                .add(key(Blocks.CACTUS))
                .add(key(Blocks.SWEET_BERRY_BUSH))
                .add(key(Blocks.COCOA))
                .add(key(Blocks.NETHER_WART))
                .add(key(Blocks.BAMBOO_SAPLING))
                .add(key(Blocks.BAMBOO))
                .add(key(Blocks.KELP))
                .add(key(Blocks.BROWN_MUSHROOM))
                .add(key(Blocks.RED_MUSHROOM));
    }

    private static ResourceKey<Block> key(Block block)
    {
        return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
    }
}
