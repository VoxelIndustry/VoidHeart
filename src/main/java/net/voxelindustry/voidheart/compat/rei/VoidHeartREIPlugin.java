package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.EntryRegistry;
import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.api.RecipeHelper;
import me.shedaniel.rei.api.plugins.REIPluginV0;
import net.minecraft.recipe.Recipe;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.function.Predicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartREIPlugin implements REIPluginV0
{
    @Override
    public Identifier getPluginIdentifier()
    {
        return new Identifier(MODID, "rei_plugin");
    }

    @Override
    public void registerPluginCategories(RecipeHelper recipeHelper)
    {
        recipeHelper.registerCategory(new AltarRecipeCategory());
    }

    @Override
    public void registerOthers(RecipeHelper recipeHelper)
    {
        recipeHelper.registerWorkingStations(AltarRecipeCategory.IDENTIFIER, EntryStack.create(VoidHeartBlocks.VOID_ALTAR));
    }

    @Override
    public void registerRecipeDisplays(RecipeHelper recipeHelper)
    {
        recipeHelper.registerRecipes(
                AltarRecipeCategory.IDENTIFIER,
                (Predicate<Recipe>) recipe -> recipe.getType() == VoidHeartRecipes.ALTAR_CATEGORY.getType(),
                recipe -> new AltarRecipeDisplay((AltarRecipe) recipe));
    }

    @Override
    public void registerEntries(EntryRegistry entryRegistry)
    {
        entryRegistry.registerEntries();
    }
}
