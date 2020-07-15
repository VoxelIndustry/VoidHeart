package net.voxelindustry.voidheart.common.recipe;

import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;

public class AltarRecipe extends RecipeBase
{
    @Getter
    private int time;

    public AltarRecipe(ItemStackRecipeIngredient output, ItemStackRecipeIngredient catalyst, int time, ItemStackRecipeIngredient... ingredients)
    {
        addInputs(ItemStack.class, catalyst, ingredients);
        addOutputs(ItemStack.class, output);

        this.time = time;
    }
}
