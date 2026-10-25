package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.BlockFamily.Variant;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.steamlayer.recipe.data.RecipeBaseBuilder;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RecipesProvider extends FabricRecipeProvider
{
    private static final Map<Variant, Offer> STONECUTTING_OFFERERS = Map.of(
            Variant.CUT, RecipeProvider::stonecutterResultFromBase,
            Variant.STAIRS, RecipeProvider::stonecutterResultFromBase,
            Variant.SLAB, (provider, category, output, input) -> provider.stonecutterResultFromBase(category, output, input, 2),
            Variant.WALL, RecipeProvider::stonecutterResultFromBase,
            Variant.POLISHED, RecipeProvider::stonecutterResultFromBase
    );

    public RecipesProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements)
    {
        // SteamLayer recipes hold ItemStacks, which datagen cannot build until item components are bound
        if (!Items.AIR.builtInRegistryHolder().areComponentsBound())
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);

        return new VoidHeartRecipeProvider(recipes, advancements);
    }

    @Override
    public String getName()
    {
        return "VoidHeart Recipes";
    }

    private static class VoidHeartRecipeProvider extends RecipeProvider
    {
        VoidHeartRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements)
        {
            super(recipes, advancements);
        }

        private void generateFamilyRecipes(BlockFamily family)
        {
            generateRecipes(family, FeatureFlags.VANILLA_SET);
            STONECUTTING_OFFERERS.forEach((variant, offer) ->
            {
                if (family.getVariants().containsKey(variant))
                    offer.accept(this, RecipeCategory.BUILDING_BLOCKS, family.get(variant), family.getBaseBlock());
            });
        }

        @Override
        public void buildRecipes()
        {
            BlockFamilies.getAllFamilies()
                    .filter(VoidHeartDataGenerator::isVoidHeartFamily)
                    .filter(BlockFamily::shouldGenerateCraftingRecipe)
                    .forEach(this::generateFamilyRecipes);

            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_BRICKS_VERTICAL, VoidHeartBlocks.VOIDSTONE_BRICKS);

            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_TILE, VoidHeartBlocks.VOIDSTONE);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_TILE_SMALL, VoidHeartBlocks.VOIDSTONE_TILE);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED, VoidHeartBlocks.VOIDSTONE_TILE);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED, VoidHeartBlocks.VOIDSTONE_TILE);

            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_BRICKS);

            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR, 2);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS);

            slab(RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.VOIDSTONE_PILLAR_SLAB, VoidHeartBlocks.VOIDSTONE_PILLAR);
            stairBuilder(VoidHeartBlocks.VOIDSTONE_PILLAR_STAIRS, Ingredient.of(VoidHeartBlocks.VOIDSTONE_PILLAR))
                    .unlockedBy(getHasName(VoidHeartBlocks.VOIDSTONE_PILLAR), has(VoidHeartBlocks.VOIDSTONE_PILLAR))
                    .save(output);

            cut(RecipeCategory.MISC, VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_POLISHED);

            nineBlockStorageRecipes(RecipeCategory.MISC, VoidHeartItems.RAVENOUS_GOLD_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);
            nineBlockStorageRecipes(RecipeCategory.MISC, VoidHeartItems.ARROGANT_IRON_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.ARROGANT_IRON_BLOCK);

            smeltingResultFromBase(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED, VoidHeartBlocks.VOIDSTONE_BRICKS);
            smeltingResultFromBase(VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR);

            offerShatterForgeRecipe(Items.GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
            offerShatterForgeRecipe(Items.IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT);
            offerShatterForgeRecipe(Blocks.COBBLESTONE, VoidHeartBlocks.VOIDSTONE);
            offerShatterForgeRecipe(Blocks.DEEPSLATE, VoidHeartBlocks.VOIDSTONE);

            // Tier 1: a single aspect, small structures
            offerShatterForgeRecipe(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, 1, 1, 2, 60, 500, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.SOUL_SAND, Blocks.SOUL_SOIL, 1, 1, 2, 60, 500, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN, 1, 2, 3, 60, 1000, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.COBBLESTONE, Blocks.MAGMA_BLOCK, 1, 2, 3, 60, 500, EssenceRegistry.FIERY);
            offerShatterForgeRecipe(Items.RAW_IRON, Items.IRON_INGOT, 2, 2, 3, 60, 1000, EssenceRegistry.FIERY);

            // Tier 2: mixed aspects, requires a Beam Focus
            offerShatterForgeRecipe(Items.DIAMOND, Items.ECHO_SHARD, 1, 100, 5, 3000, Map.of(EssenceRegistry.STILLNESS, 2, EssenceRegistry.FIERY, 2));
            offerShatterForgeRecipe(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, 1, 100, 5, 2500, Map.of(EssenceRegistry.STILLNESS, 3, EssenceRegistry.FIERY, 2));

            // Tier 3: prestige transmutations, large structures
            offerShatterForgeRecipe(Items.DIAMOND_BLOCK, Items.HEAVY_CORE, 1, 200, 7, 6000, Map.of(EssenceRegistry.STILLNESS, 6, EssenceRegistry.FIERY, 4));
            offerShatterForgeRecipe(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, 1, 200, 8, 10000, Map.of(EssenceRegistry.STILLNESS, 5, EssenceRegistry.FIERY, 6));
        }

        private void offerReversibleStoneCuttingRecipe(ItemLike output, ItemLike input, int outputCount)
        {
            stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, input, output, outputCount);
            stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, output, input);
        }

        private void offerReversibleStoneCuttingRecipe(ItemLike output, ItemLike input)
        {
            stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, input, output);
            stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, output, input);
        }

        private void offerShatterForgeRecipe(ItemLike input, ItemLike output)
        {
            offerShatterForgeRecipe(input, output, Map.of(), 0);
        }

        private void offerShatterForgeRecipe(ItemLike input, ItemLike output, Map<EssenceType, Integer> aspects, int draw)
        {
            offerShatterForgeRecipe(input, output, 1, 40, 3, draw, aspects);
        }

        private void offerShatterForgeRecipe(ItemLike input, ItemLike output, int outputCount, int aspectAmount, int monolithCount, int time, int draw, EssenceType aspect)
        {
            offerShatterForgeRecipe(input, output, outputCount, time, monolithCount, draw, Map.of(aspect, aspectAmount));
        }

        private void offerShatterForgeRecipe(ItemLike input, ItemLike output, int outputCount, int time, int monolithCount, int draw, Map<EssenceType, Integer> aspects)
        {
            var recipeId = Identifier.fromNamespaceAndPath(MODID, "shatterforge/" + getConversionRecipeName(output, input));

            var recipe = new ShatterForgeRecipe(recipeId, new ItemStack(output, outputCount), new ItemStack(input), time, monolithCount, aspects, draw);

            new RecipeBaseBuilder(recipe, RecipeCategory.MISC)
                    .unlockedBy(getHasName(input), has(input))
                    .save(this.output, recipeId.toString());
        }
    }

    @FunctionalInterface
    public interface Offer
    {
        void accept(RecipeProvider provider, RecipeCategory category, ItemLike output, ItemLike input);
    }
}
