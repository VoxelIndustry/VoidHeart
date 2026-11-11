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
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.steamlayer.recipe.data.RecipeBaseBuilder;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.AthanorRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.VoidHeartDataGenerator;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

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
        private static final int ALTAR_TIME = 20;

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

        /**
         * The rooms of the Daedalus are furnished with these, and players can build with them too. The ruined bookshelf is only found there.
         */
        private void buildDaedalusDecor()
        {
            inlaid(VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE, Items.COPPER_INGOT);
            inlaid(VoidHeartBlocks.IRON_INLAID_VOIDSTONE, Items.IRON_INGOT);

            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_ENGRAVED_GEAR, VoidHeartBlocks.VOIDSTONE_POLISHED);
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_ENGRAVED_EYE, VoidHeartBlocks.VOIDSTONE_POLISHED);

            cagedLamp(VoidHeartBlocks.CAGED_LAMP_AMBER, Items.GLOWSTONE);
            cagedLamp(VoidHeartBlocks.CAGED_LAMP_TEAL, Items.SEA_LANTERN);
            cagedLamp(VoidHeartBlocks.CAGED_LAMP_PALE, Items.END_ROD);

            rug(VoidHeartBlocks.UMBRAL_RUG, DyeColor.BLACK);
            rug(VoidHeartBlocks.VIOLET_RUG, DyeColor.PURPLE);
            rug(VoidHeartBlocks.TEAL_RUG, DyeColor.CYAN);

            shaped(RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.VOIDSTONE_BOOKSHELF)
                    .pattern("VVV").pattern("BBB").pattern("VVV")
                    .define('V', VoidHeartBlocks.VOIDSTONE_POLISHED)
                    .define('B', Items.BOOK)
                    .unlockedBy(getHasName(VoidHeartBlocks.VOIDSTONE_POLISHED), has(VoidHeartBlocks.VOIDSTONE_POLISHED))
                    .save(output);
        }

        private void inlaid(Block inlaid, Item metal)
        {
            shaped(RecipeCategory.BUILDING_BLOCKS, inlaid, 8)
                    .pattern("VVV").pattern("VMV").pattern("VVV")
                    .define('V', VoidHeartBlocks.VOIDSTONE_POLISHED)
                    .define('M', metal)
                    .unlockedBy(getHasName(VoidHeartBlocks.VOIDSTONE_POLISHED), has(VoidHeartBlocks.VOIDSTONE_POLISHED))
                    .save(output);
        }

        private void cagedLamp(Block lamp, Item light)
        {
            shaped(RecipeCategory.BUILDING_BLOCKS, lamp, 2)
                    .pattern("NVN").pattern("VLV").pattern("NVN")
                    .define('N', Items.IRON_NUGGET)
                    .define('V', VoidHeartBlocks.VOIDSTONE_POLISHED)
                    .define('L', light)
                    .unlockedBy(getHasName(light), has(light))
                    .save(output);
        }

        private void rug(Block rug, DyeColor color)
        {
            var wool = Items.WOOL.pick(color);
            shaped(RecipeCategory.DECORATIONS, rug, 3)
                    .pattern("WAW")
                    .define('W', wool)
                    .define('A', Items.AMETHYST_SHARD)
                    .unlockedBy(getHasName(wool), has(wool))
                    .save(output);
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
            offerReversibleStoneCuttingRecipe(VoidHeartBlocks.VOIDSTONE_CHISELED, VoidHeartBlocks.VOIDSTONE_POLISHED);

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
            nineBlockStorageRecipes(RecipeCategory.MISC, VoidHeartItems.SEETHING_STEEL_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.SEETHING_STEEL_BLOCK);
            nineBlockStorageRecipes(RecipeCategory.MISC, VoidHeartItems.COVETOUS_COPPER_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.COVETOUS_COPPER_BLOCK);
            nineBlockStorageRecipes(RecipeCategory.MISC, VoidHeartItems.LISTLESS_LEAD_INGOT, RecipeCategory.BUILDING_BLOCKS, VoidHeartBlocks.LISTLESS_LEAD_BLOCK);

            smeltingResultFromBase(VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED, VoidHeartBlocks.VOIDSTONE_BRICKS);
            smeltingResultFromBase(VoidHeartBlocks.VOIDSTONE_WEATHERED_PILLAR, VoidHeartBlocks.VOIDSTONE_PILLAR);

            buildDaedalusDecor();

            offerShatterForgeRecipe(Items.GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
            offerShatterForgeRecipe(Items.IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT);
            offerShatterForgeRecipe(Items.COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT);
            offerShatterForgeRecipe(Blocks.COBBLESTONE, VoidHeartBlocks.VOIDSTONE);
            offerShatterForgeRecipe(Blocks.DEEPSLATE, VoidHeartBlocks.VOIDSTONE);

            // Tier 1: a single aspect, small structures
            offerShatterForgeRecipe(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, 1, 1, 2, 60, 500, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.SOUL_SAND, Blocks.SOUL_SOIL, 1, 1, 2, 60, 500, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN, 1, 2, 3, 60, 1000, EssenceRegistry.STILLNESS);
            offerShatterForgeRecipe(Blocks.COBBLESTONE, Blocks.MAGMA_BLOCK, 1, 2, 3, 60, 500, EssenceRegistry.FIERY);
            offerShatterForgeRecipe(Items.RAW_IRON, Items.IRON_INGOT, 2, 2, 3, 60, 1000, EssenceRegistry.FIERY);
            offerShatterForgeRecipe(Items.IRON_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, 1, 2, 3, 60, 1000, EssenceRegistry.FIERY);
            offerShatterForgeRecipe(Items.IRON_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT, 1, 2, 3, 60, 1000, EssenceRegistry.STILLNESS);
            // The principles of alchemy, sulfur being taken as vanilla gives it
            offerShatterForgeRecipe(Blocks.CINNABAR, VoidHeartItems.QUICKSILVER, 2, 2, 3, 60, 1000, EssenceRegistry.FIERY);
            offerShatterForgeRecipe(VoidHeartBlocks.ABYSSAL_SILT, VoidHeartItems.SALT, 2, 1, 2, 60, 500, EssenceRegistry.STILLNESS);

            // Tier 2: mixed aspects, requires a Beam Focus
            offerShatterForgeRecipe(Items.DIAMOND, Items.ECHO_SHARD, 1, 100, 5, 3000, Map.of(EssenceRegistry.STILLNESS, 2, EssenceRegistry.FIERY, 2));
            offerShatterForgeRecipe(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, 1, 100, 5, 2500, Map.of(EssenceRegistry.STILLNESS, 3, EssenceRegistry.FIERY, 2));

            // Tier 3: prestige transmutations, large structures
            offerShatterForgeRecipe(Items.DIAMOND_BLOCK, Items.HEAVY_CORE, 1, 200, 7, 6000, Map.of(EssenceRegistry.STILLNESS, 6, EssenceRegistry.FIERY, 4));
            // A full experience skull, 30 levels
            offerShatterForgeRecipe(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, 1, 200, 8, 10000, Map.of(EssenceRegistry.STILLNESS, 5, EssenceRegistry.FIERY, 6),
                    ExperienceSkullTile.MAX_EXPERIENCE);

            // Altar: the catalyst sits on the altar, each ingredient on one of the eight pillars
            shieldingApparatus();
            offerAltarRecipe("armillary_core", VoidHeartBlocks.ARMILLARY_CORE, Items.COMPASS,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("cogitant", VoidHeartBlocks.COGITANT, VoidHeartItems.WRIGGLING_BRAIN,
                    VoidHeartBlocks.VOIDSTONE, VoidHeartBlocks.VOIDSTONE, VoidHeartBlocks.VOIDSTONE, VoidHeartBlocks.VOIDSTONE,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            offerAltarRecipe("void_loom", VoidHeartBlocks.VOID_LOOM, Items.LOOM,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
            // The proxy is a Cogitant grown past its cage
            offerAltarRecipe("meat_proxy", VoidHeartBlocks.MEAT_PROXY, VoidHeartBlocks.COGITANT,
                    VoidHeartItems.WRIGGLING_BRAIN, VoidHeartItems.WRIGGLING_BRAIN, VoidHeartItems.WRIGGLING_BRAIN,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.ARROGANT_IRON_INGOT,
                    Items.ROTTEN_FLESH, Items.ROTTEN_FLESH);
            // Grafts, eaten to go one step further into the void after the heart
            offerAltarRecipe("void_vein", VoidHeartItems.VOID_VEIN, VoidHeartItems.VOID_AMALGAM,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    Items.REDSTONE, Items.REDSTONE,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    Items.ROTTEN_FLESH, Items.ROTTEN_FLESH);
            offerAltarRecipe("void_marrow", VoidHeartItems.VOID_MARROW, Items.BONE_BLOCK,
                    VoidHeartItems.WRIGGLING_BRAIN, VoidHeartItems.WRIGGLING_BRAIN,
                    VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL);
            // The Avenging Reach, an arm grown at a time around a blade, each dearer than the last
            offerAltarRecipe("avenging_reach_1", VoidHeartItems.AVENGING_REACH[0], Items.IRON_SWORD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    Items.BONE, Items.BONE,
                    Items.ROTTEN_FLESH, Items.ROTTEN_FLESH);
            offerAltarRecipe("avenging_reach_2", VoidHeartItems.AVENGING_REACH[1], Items.IRON_SWORD,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.ARROGANT_IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT,
                    VoidHeartItems.WRIGGLING_BRAIN, VoidHeartItems.WRIGGLING_BRAIN);
            offerAltarRecipe("avenging_reach_3", VoidHeartItems.AVENGING_REACH[2], Items.IRON_SWORD,
                    VoidHeartItems.WRAITH_INK, VoidHeartItems.WRAITH_INK,
                    VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL);
            offerAltarRecipe("avenging_reach_4", VoidHeartItems.AVENGING_REACH[3], Items.IRON_SWORD,
                    VoidHeartItems.MEMORIAM_SHARD, VoidHeartItems.MEMORIAM_SHARD, VoidHeartItems.MEMORIAM_SHARD,
                    VoidHeartItems.WRAITH_INK, VoidHeartItems.WRAITH_INK,
                    VoidHeartItems.RUBRUM_SHARD,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL);
            offerAltarRecipe("beam_focus", VoidHeartBlocks.BEAM_FOCUS, VoidHeartBlocks.VOID_CONDUIT,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            offerAltarRecipe("experience_skull", VoidHeartBlocks.EXPERIENCE_SKULL, VoidHeartBlocks.VOID_SKELETON_HEAD,
                    Items.ROTTEN_FLESH, Items.ROTTEN_FLESH, Items.ROTTEN_FLESH,
                    Items.DIAMOND, Items.DIAMOND,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("eye_bottle", VoidHeartBlocks.EYE_BOTTLE, Items.GLASS_BOTTLE,
                    VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("inventory_inserter", VoidHeartBlocks.INVENTORY_INSERTER, Blocks.HOPPER, eight(VoidHeartItems.OBSIDIAN_SHARD));
            offerAltarRecipe("ravenous_collector", VoidHeartBlocks.RAVENOUS_COLLECTOR, VoidHeartBlocks.VOID_CONDUIT,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    Items.BUCKET, VoidHeartItems.ENDER_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("shatter_forge", VoidHeartBlocks.SHATTER_FORGE, Blocks.ANVIL,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            offerAltarRecipe("void_amalgam", VoidHeartItems.VOID_AMALGAM, Items.ENDER_PEARL, eight(VoidHeartItems.OBSIDIAN_SHARD));
            offerAltarRecipe("void_lamp_from_glowstone", VoidHeartBlocks.VOID_LAMP, Blocks.GLOWSTONE,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    Items.REDSTONE, Items.REDSTONE, Items.REDSTONE, Items.REDSTONE);
            offerAltarRecipe("void_lamp_from_redstone_lamp", VoidHeartBlocks.VOID_LAMP, Blocks.REDSTONE_LAMP,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("void_pearl", VoidHeartItems.VOID_PEARL, VoidHeartItems.ENDER_SHARD, eight(VoidHeartItems.OBSIDIAN_SHARD));
            offerAltarRecipe("voidstone", new ItemStack(VoidHeartBlocks.VOIDSTONE, 8), Blocks.OBSIDIAN,
                    VoidHeartItems.ENDER_SHARD,
                    Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE);
            offerAltarRecipe("shoulder_eye", 40, new ItemStack(VoidHeartItems.SHOULDER_EYE), Items.ENDER_EYE,
                    Items.SPIDER_EYE, VoidHeartItems.VOID_PEARL, Items.SLIME_BALL,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);

            // The membrane again, soaked in what lets a wraith pass through stone
            offerAltarRecipe("wraith_veil", new ItemStack(VoidHeartItems.WRAITH_VEIL), Items.PHANTOM_MEMBRANE,
                    VoidHeartItems.WRAITH_INK, VoidHeartItems.WRAITH_INK, VoidHeartItems.WRAITH_INK, VoidHeartItems.WRAITH_INK,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.VOID_PEARL);

            // A membrane is what lets some things through and not others
            offerAltarRecipe("permeable_barrier_item", VoidHeartItems.PERMEABLE_BARRIER, Items.PHANTOM_MEMBRANE,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    Blocks.GLASS, Blocks.GLASS);

            offerAltarRecipe("malignant_tormenter", VoidHeartBlocks.MALIGNANT_TORMENTER, Blocks.SOUL_SAND,
                    Items.ROTTEN_FLESH, Items.ROTTEN_FLESH, Items.ROTTEN_FLESH, Items.ROTTEN_FLESH,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.RAVENOUS_GOLD_INGOT);

            offerAltarRecipe("athanor", VoidHeartBlocks.ATHANOR, Blocks.BLAST_FURNACE,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);

            // Athanor: the base sits in the core, one reagent in each crucible
            offerAthanorRecipe("searing_oil", new ItemStack(VoidHeartItems.SEARING_OIL), Items.GLASS_BOTTLE, 200, Map.of(EssenceRegistry.FIERY, 500),
                    Items.SULFUR, VoidHeartItems.QUICKSILVER);
            offerAthanorRecipe("concussive_oil", new ItemStack(VoidHeartItems.CONCUSSIVE_OIL), Items.GLASS_BOTTLE, 200,
                    Map.of(EssenceRegistry.STILLNESS, 400, EssenceRegistry.FIERY, 100),
                    Items.SULFUR, VoidHeartItems.QUICKSILVER, VoidHeartItems.OBSIDIAN_SHARD);
            offerAthanorRecipe("arcane_oil", new ItemStack(VoidHeartItems.ARCANE_OIL), Items.GLASS_BOTTLE, 200,
                    Map.of(EssenceRegistry.STILLNESS, 250, EssenceRegistry.FIERY, 250),
                    Items.SULFUR, VoidHeartItems.QUICKSILVER, Items.LAPIS_LAZULI);
            offerAthanorRecipe("kernacian_oil", new ItemStack(VoidHeartItems.KERNACIAN_OIL), Items.GLASS_BOTTLE, 300,
                    Map.of(EssenceRegistry.STILLNESS, 500, EssenceRegistry.FIERY, 500),
                    Items.SULFUR, VoidHeartItems.QUICKSILVER, VoidHeartItems.KERNACIAN_CORE);
            offerAthanorRecipe("ember_draught", new ItemStack(VoidHeartItems.EMBER_DRAUGHT), Items.GLASS_BOTTLE, 200, Map.of(EssenceRegistry.FIERY, 300),
                    VoidHeartItems.QUICKSILVER, VoidHeartItems.RUBRUM_SHARD, VoidHeartBlocks.SULFIDE_CRUST);
            offerAthanorRecipe("stillwater", new ItemStack(VoidHeartItems.STILLWATER), Items.GLASS_BOTTLE, 200, Map.of(EssenceRegistry.STILLNESS, 300),
                    VoidHeartItems.QUICKSILVER, VoidHeartBlocks.STILL_KELP, VoidHeartBlocks.STILL_ANEMONE);
            offerAthanorRecipe("bulwark_elixir", new ItemStack(VoidHeartItems.BULWARK_ELIXIR), Items.GLASS_BOTTLE, 240,
                    Map.of(EssenceRegistry.STILLNESS, 200, EssenceRegistry.FIERY, 200),
                    VoidHeartItems.QUICKSILVER, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.OBSIDIAN_SHARD);
            offerAthanorRecipe("insight_elixir", new ItemStack(VoidHeartItems.INSIGHT_ELIXIR), Items.GLASS_BOTTLE, 240,
                    Map.of(EssenceRegistry.STILLNESS, 200, EssenceRegistry.FIERY, 200),
                    VoidHeartItems.QUICKSILVER, VoidHeartItems.WRIGGLING_BRAIN, Items.SPIDER_EYE);
            offerAthanorRecipe("clarity_draught", new ItemStack(VoidHeartItems.CLARITY_DRAUGHT), Items.GLASS_BOTTLE, 200, Map.of(EssenceRegistry.STILLNESS, 300),
                    VoidHeartItems.QUICKSILVER, VoidHeartBlocks.GLOW_ALGAE, VoidHeartBlocks.LUMEN_CORAL);

            // The heart binding is done by an onCraft callback registered on this id, see VoidHeartRecipes
            saveAltarRecipe(new AltarRecipe(altarRecipeId("voidheart"), ingredient(VoidHeartItems.VOID_HEART), ALTAR_TIME), VoidHeartItems.VOID_HEART);

            EnergyDatagen.recipes(energyRecipes());
        }

        private EnergyRecipes energyRecipes()
        {
            var provider = this;
            return new EnergyRecipes()
            {
                @Override
                public RecipeProvider provider()
                {
                    return provider;
                }

                @Override
                public RecipeOutput output()
                {
                    return output;
                }

                @Override
                public void altar(String name, ItemLike output, ItemLike catalyst, ItemLike... ingredients)
                {
                    offerAltarRecipe(name, output, catalyst, ingredients);
                }

                @Override
                public void shatterForge(ItemLike input, ItemLike output, int outputCount, int time, int monolithCount, int draw, Map<EssenceType, Integer> aspects)
                {
                    offerShatterForgeRecipe(input, output, outputCount, time, monolithCount, draw, aspects);
                }
            };
        }

        private static ItemLike[] eight(ItemLike ingredient)
        {
            return new ItemLike[]{ingredient, ingredient, ingredient, ingredient, ingredient, ingredient, ingredient, ingredient};
        }

        private static Identifier altarRecipeId(String name)
        {
            return Identifier.fromNamespaceAndPath(MODID, "altar/" + name);
        }

        private static ItemStackRecipeIngredient ingredient(ItemLike item)
        {
            return new ItemStackRecipeIngredient(new ItemStack(item), false);
        }

        /**
         * Each larger apparatus is raised from the one before on the altar, and keeps the components set in it. Every component has a catalyst of its own.
         */
        private void shieldingApparatus()
        {
            offerAltarRecipe("lesser_shielding_apparatus", VoidHeartItems.LESSER_SHIELDING_APPARATUS, Items.SHIELD,
                    VoidHeartItems.ARROGANT_IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            offerAltarRecipe("greater_shielding_apparatus", VoidHeartItems.GREATER_SHIELDING_APPARATUS, VoidHeartItems.LESSER_SHIELDING_APPARATUS,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.AZURIUM_SHARD);
            offerAltarRecipe("supreme_shielding_apparatus", VoidHeartItems.SUPREME_SHIELDING_APPARATUS, VoidHeartItems.GREATER_SHIELDING_APPARATUS,
                    Items.NETHERITE_INGOT, Items.NETHERITE_INGOT,
                    VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD,
                    VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL);

            component(ApparatusComponent.CELL, Items.AMETHYST_SHARD,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            component(ApparatusComponent.BANK, Items.LAPIS_BLOCK,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.ARROGANT_IRON_INGOT);
            component(ApparatusComponent.RESERVOIR, Items.AMETHYST_BLOCK,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.RAVENOUS_GOLD_INGOT);
            component(ApparatusComponent.RESONATOR, Items.NOTE_BLOCK,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT);
            component(ApparatusComponent.CHOIR, Items.JUKEBOX,
                    VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT, VoidHeartItems.COVETOUS_COPPER_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.VOID_PEARL);
            component(ApparatusComponent.QUICKENER, Items.CLOCK,
                    VoidHeartItems.SEETHING_STEEL_INGOT, Items.REDSTONE, Items.REDSTONE, VoidHeartItems.ENDER_SHARD);
            component(ApparatusComponent.HASTENER, Items.COMPARATOR,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, Items.REDSTONE, Items.REDSTONE, VoidHeartItems.VOID_PEARL);
            component(ApparatusComponent.EMBER_WARD, Items.MAGMA_CREAM,
                    VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    Items.BLAZE_POWDER, Items.BLAZE_POWDER);
            component(ApparatusComponent.ARROW_WARD, Items.TARGET,
                    VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT, VoidHeartItems.ARROGANT_IRON_INGOT,
                    Items.FEATHER, Items.FEATHER);
            component(ApparatusComponent.BLAST_WARD, Items.OBSIDIAN,
                    VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    Items.GUNPOWDER, Items.GUNPOWDER);
            component(ApparatusComponent.ARCANE_WARD, VoidHeartItems.AZURIUM_SHARD,
                    VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    Items.LAPIS_LAZULI, Items.LAPIS_LAZULI);
            component(ApparatusComponent.PULSE_CORE, Items.FIRE_CHARGE,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            component(ApparatusComponent.BLINK_CORE, Items.CHORUS_FRUIT,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            component(ApparatusComponent.STASIS_CORE, Items.BLUE_ICE,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL, VoidHeartItems.LISTLESS_LEAD_INGOT, VoidHeartItems.LISTLESS_LEAD_INGOT);
            component(ApparatusComponent.MENDING_CORE, Items.GLISTERING_MELON_SLICE,
                    VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT);
        }

        private void component(ApparatusComponent component, ItemLike catalyst, ItemLike... ingredients)
        {
            offerAltarRecipe(component.itemName(), VoidHeartItems.APPARATUS_COMPONENTS.get(component), catalyst, ingredients);
        }

        private void offerAltarRecipe(String name, ItemLike output, ItemLike catalyst, ItemLike... ingredients)
        {
            offerAltarRecipe(name, new ItemStack(output), catalyst, ingredients);
        }

        private void offerAltarRecipe(String name, ItemStack output, ItemLike catalyst, ItemLike... ingredients)
        {
            offerAltarRecipe(name, ALTAR_TIME, output, catalyst, ingredients);
        }

        private void offerAltarRecipe(String name, int time, ItemStack output, ItemLike catalyst, ItemLike... ingredients)
        {
            var ingredientList = new ItemStackRecipeIngredient[ingredients.length];
            for (var i = 0; i < ingredients.length; i++)
                ingredientList[i] = ingredient(ingredients[i]);

            saveAltarRecipe(new AltarRecipe(altarRecipeId(name), new ItemStackRecipeIngredient(output, false), ingredient(catalyst), time, ingredientList), catalyst);
        }

        private void offerAthanorRecipe(String name, ItemStack output, ItemLike base, int time, Map<EssenceType, Integer> aspects, ItemLike... reagents)
        {
            var reagentStacks = new ItemStack[reagents.length];
            for (var i = 0; i < reagents.length; i++)
                reagentStacks[i] = new ItemStack(reagents[i]);

            var recipeId = Identifier.fromNamespaceAndPath(MODID, "athanor/" + name);
            new RecipeBaseBuilder(new AthanorRecipe(recipeId, output, new ItemStack(base), time, aspects, reagentStacks), RecipeCategory.MISC)
                    .unlockedBy(getHasName(base), has(base))
                    .save(this.output, recipeId.toString());
        }

        private void saveAltarRecipe(AltarRecipe recipe, ItemLike unlockedBy)
        {
            new RecipeBaseBuilder(recipe, RecipeCategory.MISC)
                    .unlockedBy(getHasName(unlockedBy), has(unlockedBy))
                    .save(this.output, recipe.getId().toString());
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
            offerShatterForgeRecipe(input, output, outputCount, time, monolithCount, draw, aspects, 0);
        }

        private void offerShatterForgeRecipe(ItemLike input, ItemLike output, int outputCount, int time, int monolithCount, int draw, Map<EssenceType, Integer> aspects,
                                             int experience)
        {
            var recipeId = Identifier.fromNamespaceAndPath(MODID, "shatterforge/" + getConversionRecipeName(output, input));

            var recipe = new ShatterForgeRecipe(recipeId, new ItemStack(output, outputCount), new ItemStack(input), time, monolithCount, aspects, draw)
                    .withExperience(experience);

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
