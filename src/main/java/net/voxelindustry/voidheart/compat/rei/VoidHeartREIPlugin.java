package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidHeartREIPlugin implements REIClientPlugin
{
    @Override
    public void registerCategories(CategoryRegistry registry)
    {
        registry.add(new AltarRecipeCategory());

        registry.addWorkstations(AltarRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.VOID_ALTAR));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry)
    {
        registry.registerFiller(AltarRecipe.class, AltarRecipeDisplay::new);
    }
}
