package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.block.Blocks;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.data.family.BlockFamily.Variant;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.RecipeProvider;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
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
            Variant.CUT, RecipeProvider::offerStonecuttingRecipe,
            Variant.STAIRS, RecipeProvider::offerStonecuttingRecipe,
            Variant.SLAB, (exporter, category, output, input) -> offerStonecuttingRecipe(exporter, category, output, input, 2),
            Variant.WALL, RecipeProvider::offerStonecuttingRecipe,
            Variant.POLISHED, RecipeProvider::offerStonecuttingRecipe
    );

    public RecipesProvider(FabricDataOutput output)
    {
        super(output);
    }

    public static void generateFamily(Consumer<RecipeJsonProvider> exporter, BlockFamily family)
    {
        RecipeProvider.generateFamily(exporter, family);
        STONECUTTING_OFFERERS.forEach((variant, offer) ->
        {
            if (family.getVariants().containsKey(variant))
                offer.accept(exporter, RecipeCategory.BUILDING_BLOCKS, family.getVariant(variant), family.getBaseBlock());
        });
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> exporter)
    {
        BlockFamilies.getFamilies()
                .filter(VoidHeartDataGenerator::isVoidHeartFamily)
                .filter(family -> family.shouldGenerateRecipes(FeatureFlags.VANILLA_FEATURES))
                .forEach(family -> RecipesProvider.generateFamily(exporter, family));

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_BRICKS_VERTICAL, VoidHeartBlocks.VOIDSTONE_BRICKS);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE, VoidHeartBlocks.VOIDSTONE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL, VoidHeartBlocks.VOIDSTONE_TILE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED, VoidHeartBlocks.VOIDSTONE_TILE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED, VoidHeartBlocks.VOIDSTONE_TILE);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_BRICKS);

        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR, 2);
        offerReversibleStoneCuttingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

        RecipeProvider.offerSlabRecipe(exporter, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR);
        RecipeProvider.createStairsRecipe(VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS, Ingredient.ofItems(VoidHeartBlocks.VOIDSTONE_PILLAR))
                .criterion(hasItem(VoidHeartBlocks.VOIDSTONE_PILLAR), conditionsFromItem(VoidHeartBlocks.VOIDSTONE_PILLAR))
                .offerTo(exporter);

        RecipeProvider.offerCutCopperRecipe(exporter, RecipeCategory.MISC, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_POLISHED);

        RecipeProvider.offerReversibleCompactingRecipes(exporter, RecipeCategory.MISC, VoidHeartItems.RAVENOUS_GOLD_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);
        RecipeProvider.offerReversibleCompactingRecipes(exporter, RecipeCategory.MISC, VoidHeartItems.ARROGANT_IRON_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.ARROGANT_IRON_BLOCK);

        RecipeProvider.offerCrackingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED, VoidHeartBlocks.VOIDSTONE_BRICKS);
        RecipeProvider.offerCrackingRecipe(exporter, VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR);

        offerShatterForgeRecipe(exporter, Items.GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
        offerShatterForgeRecipe(exporter, Items.IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT);
        offerShatterForgeRecipe(exporter, Blocks.COBBLESTONE, VoidHeartBlocks.VOIDSTONE);
        offerShatterForgeRecipe(exporter, Blocks.DEEPSLATE, VoidHeartBlocks.VOIDSTONE);
    }

    private void offerReversibleStoneCuttingRecipe(Consumer<RecipeJsonProvider> exporter, ItemConvertible output, ItemConvertible input, int outputCount)
    {
        RecipeProvider.offerStonecuttingRecipe(exporter, RecipeCategory.BUILDING_BLOCKS, input, output, outputCount);
        RecipeProvider.offerStonecuttingRecipe(exporter, RecipeCategory.BUILDING_BLOCKS, output, input);
    }

    private void offerReversibleStoneCuttingRecipe(Consumer<RecipeJsonProvider> exporter, ItemConvertible output, ItemConvertible input)
    {
        RecipeProvider.offerStonecuttingRecipe(exporter, RecipeCategory.BUILDING_BLOCKS, input, output);
        RecipeProvider.offerStonecuttingRecipe(exporter, RecipeCategory.BUILDING_BLOCKS, output, input);
    }

    private void offerShatterForgeRecipe(Consumer<RecipeJsonProvider> exporter, ItemConvertible input, ItemConvertible output)
    {
        var recipePath = "shatterforge/" + RecipeProvider.convertBetween(output, input);

        var recipe = new ShatterForgeRecipe(new Identifier(MODID, recipePath), output, input, 40, 3);

        new RecipeBaseJsonBuilder(recipe, VoidHeartRecipes.SHATTER_FORGE_TYPE, RecipeCategory.MISC)
                .criterion(RecipeProvider.hasItem(input), RecipeProvider.conditionsFromItem(input))
                .offerTo(exporter, recipePath);
    }

    @FunctionalInterface
    public interface Offer
    {
        void accept(Consumer<RecipeJsonProvider> exporter, RecipeCategory category, ItemConvertible output, ItemConvertible input);
    }
}
