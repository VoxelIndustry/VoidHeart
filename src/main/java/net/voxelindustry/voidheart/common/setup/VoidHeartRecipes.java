package net.voxelindustry.voidheart.common.setup;

import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.recipe.RecipeHandler;
import net.voxelindustry.steamlayer.recipe.category.RecipeCategory;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.VOIDSTONE;
import static net.voxelindustry.voidheart.common.setup.VoidHeartItems.*;

public class VoidHeartRecipes
{
    public static RecipeCategory ALTAR_CATEGORY;

    public static void registerRecipes()
    {
        RecipeHandler.addCategory(ALTAR_CATEGORY = new RecipeCategory(new Identifier(MODID, "altar")));

        ALTAR_CATEGORY.add(new AltarRecipe(
                ingredient(VOID_AMALGAM),
                ingredient(ENDER_SHARD),
                20,
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD),
                ingredient(OBSIDIAN_SHARD)
        ));

        ALTAR_CATEGORY.add(new AltarRecipe(
                ingredient(VOIDSTONE, 8),
                ingredient(Items.ENDER_PEARL),
                20,
                ingredient(Items.OBSIDIAN),
                ingredient(Items.OBSIDIAN),
                ingredient(Items.OBSIDIAN),
                ingredient(Items.OBSIDIAN),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE)

        ));
    }

    private static ItemStackRecipeIngredient ingredient(ItemConvertible item)
    {
        return ingredient(item, 1);
    }

    private static ItemStackRecipeIngredient ingredient(ItemConvertible item, int count)
    {
        return new ItemStackRecipeIngredient(new ItemStack(item, count));
    }
}
