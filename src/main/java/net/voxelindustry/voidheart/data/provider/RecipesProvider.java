package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.data.family.BlockFamily.Variant;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.RecipeProvider;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
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

        RecipeProvider.offerReversibleCompactingRecipes(exporter, RecipeCategory.MISC, VoidHeartItems.RAVENOUS_GOLD_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);

        offerShatterForgeRecipe(exporter, Items.GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
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
