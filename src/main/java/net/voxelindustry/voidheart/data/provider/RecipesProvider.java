package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamily.Variant;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.steamlayer.recipe.data.RecipeBaseJsonBuilder;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.Map;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RecipesProvider extends FabricRecipeProvider
{
    private static final Map<Variant, Offer> STONECUTTING_OFFERERS = Map.of(
            Variant.CUT, RecipeProvider::stonecutterResultFromBase,
            Variant.STAIRS, RecipeProvider::stonecutterResultFromBase,
            Variant.SLAB, (exporter, category, output, input) -> stonecutterResultFromBase(exporter, category, output, input, 2),
            Variant.WALL, RecipeProvider::stonecutterResultFromBase,
            Variant.POLISHED, RecipeProvider::stonecutterResultFromBase
    );

    public RecipesProvider(FabricDataOutput output)
    {
        super(output);
    }

    public static void generateRecipes(Consumer<FinishedRecipe> exporter, BlockFamily family)
    {
        RecipeProvider.generateRecipes(exporter, family);
        STONECUTTING_OFFERERS.forEach((variant, offer) ->
        {
            if (family.getVariants().containsKey(variant))
                offer.accept(exporter, RecipeCategory.BUILDING_BLOCKS, family.get(variant), family.getBaseBlock());
        });
    }

    @Override
    public void buildRecipes(Consumer<FinishedRecipe> exporter)
    {
        BlockFamilies.getAllFamilies()
                .filter(VoidHeartDataGenerator::isVoidHeartFamily)
                .filter(family -> family.shouldGenerateRecipe(FeatureFlags.VANILLA_SET))
                .forEach(family -> RecipesProvider.generateRecipes(exporter, family));

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_BRICKS_VERTICAL, VoidHeartBlocks.VOIDSTONE_BRICKS);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE, VoidHeartBlocks.VOIDSTONE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL, VoidHeartBlocks.VOIDSTONE_TILE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED, VoidHeartBlocks.VOIDSTONE_TILE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED, VoidHeartBlocks.VOIDSTONE_TILE);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_BRICKS);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR, 2);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

        RecipeProvider.slab(exporter, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR);
        RecipeProvider.stairBuilder(VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS, Ingredient.of(VoidHeartBlocks.VOIDSTONE_PILLAR))
                .unlockedBy(getHasName(VoidHeartBlocks.VOIDSTONE_PILLAR), has(VoidHeartBlocks.VOIDSTONE_PILLAR))
                .save(exporter);

        RecipeProvider.cut(exporter, RecipeCategory.MISC, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_POLISHED);

        RecipeProvider.nineBlockStorageRecipes(exporter, RecipeCategory.MISC, VoidHeartItems.RAVENOUS_GOLD_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);
        RecipeProvider.nineBlockStorageRecipes(exporter, RecipeCategory.MISC, VoidHeartItems.ARROGANT_IRON_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.ARROGANT_IRON_BLOCK);

        RecipeProvider.smeltingResultFromBase(exporter, VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED, VoidHeartBlocks.VOIDSTONE_BRICKS);
        RecipeProvider.smeltingResultFromBase(exporter, VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR);

        offerShatterForgeRecipe(exporter, Items.GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
        offerShatterForgeRecipe(exporter, Items.IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT);
        offerShatterForgeRecipe(exporter, Blocks.COBBLESTONE, VoidHeartBlocks.VOIDSTONE);
        offerShatterForgeRecipe(exporter, Blocks.DEEPSLATE, VoidHeartBlocks.VOIDSTONE);
    }

    private void offerReversibleStoneCuttingRecipe(Consumer<FinishedRecipe> exporter, ItemLike output, ItemLike input, int outputCount)
    {
        RecipeProvider.stonecutterResultFromBase(exporter, RecipeCategory.BUILDING_BLOCKS, input, output, outputCount);
        RecipeProvider.stonecutterResultFromBase(exporter, RecipeCategory.BUILDING_BLOCKS, output, input);
    }

    private void offerReversibleStoneCuttingRecipe(Consumer<FinishedRecipe> exporter, ItemLike output, ItemLike input)
    {
        RecipeProvider.stonecutterResultFromBase(exporter, RecipeCategory.BUILDING_BLOCKS, input, output);
        RecipeProvider.stonecutterResultFromBase(exporter, RecipeCategory.BUILDING_BLOCKS, output, input);
    }

    private void offerShatterForgeRecipe(Consumer<FinishedRecipe> exporter, ItemLike input, ItemLike output)
    {
        var recipePath = "shatterforge/" + RecipeProvider.getConversionRecipeName(output, input);

        var recipe = new ShatterForgeRecipe(new ResourceLocation(MODID, recipePath), output, input, 40, 3);

        new RecipeBaseJsonBuilder(recipe, VoidHeartRecipes.SHATTER_FORGE_TYPE, RecipeCategory.MISC)
                .unlockedBy(RecipeProvider.getHasName(input), RecipeProvider.has(input))
                .save(exporter, recipePath);
    }

    @FunctionalInterface
    public interface Offer
    {
        void accept(Consumer<FinishedRecipe> exporter, RecipeCategory category, ItemLike output, ItemLike input);
    }
}
