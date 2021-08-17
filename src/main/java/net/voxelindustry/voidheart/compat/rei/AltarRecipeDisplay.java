package net.voxelindustry.voidheart.compat.rei;

import lombok.Getter;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.CollectionUtils;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;

import java.util.List;

public class AltarRecipeDisplay implements Display
{
    @Getter
    private final List<EntryIngredient> inputEntries;
    @Getter
    private final List<EntryIngredient> outputEntries;

    public AltarRecipeDisplay(AltarRecipe recipe)
    {
        inputEntries = CollectionUtils.map(recipe.getRecipeInputs(ItemStack.class),
                ingredient -> EntryIngredients.of(ingredient.getRaw()));
        outputEntries = CollectionUtils.map(recipe.getRecipeOutputs(ItemStack.class),
                ingredient -> EntryIngredients.of(ingredient.getRaw()));
    }

    @Override
    public CategoryIdentifier<AltarRecipeDisplay> getCategoryIdentifier()
    {
        return AltarRecipeCategory.IDENTIFIER;
    }
}
