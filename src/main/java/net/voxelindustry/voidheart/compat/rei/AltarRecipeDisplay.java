package net.voxelindustry.voidheart.compat.rei;

import lombok.Getter;
import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.api.RecipeDisplay;
import me.shedaniel.rei.utils.CollectionUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;

import java.util.Collections;
import java.util.List;

public class AltarRecipeDisplay implements RecipeDisplay
{
    private final AltarRecipe recipe;

    @Getter
    private final List<List<EntryStack>> inputEntries;
    @Getter
    private final List<EntryStack>       outputEntries;

    public AltarRecipeDisplay(AltarRecipe recipe)
    {
        this.recipe = recipe;

        inputEntries = CollectionUtils.map(recipe.getRecipeInputs(ItemStack.class),
                ingredient -> Collections.singletonList(EntryStack.create(ingredient.getRaw())));
        outputEntries = CollectionUtils.map(recipe.getRecipeOutputs(ItemStack.class),
                ingredient -> EntryStack.create(ingredient.getRaw()));
    }

    @Override
    public Identifier getRecipeCategory()
    {
        return AltarRecipeCategory.IDENTIFIER;
    }
}
