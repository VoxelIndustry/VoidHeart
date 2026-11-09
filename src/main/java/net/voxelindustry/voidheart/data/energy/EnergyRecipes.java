package net.voxelindustry.voidheart.data.energy;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.level.ItemLike;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.Map;

/**
 * What a package's datagen may use to add recipes: the vanilla helpers, and the altar and Shatter Forge recipes, which always go through datagen.
 */
public interface EnergyRecipes
{
    /**
     * Vanilla builders such as shaped, has and getHasName.
     */
    RecipeProvider provider();

    RecipeOutput output();

    void altar(String name, ItemLike output, ItemLike catalyst, ItemLike... ingredients);

    void shatterForge(ItemLike input, ItemLike output, int outputCount, int time, int monolithCount, int draw, Map<EssenceType, Integer> aspects);
}
