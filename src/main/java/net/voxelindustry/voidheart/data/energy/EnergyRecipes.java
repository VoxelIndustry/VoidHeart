package net.voxelindustry.voidheart.data.energy;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.ItemStack;
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

    /**
     * An altar recipe for one exact catalyst, components and all, whose result becomes the output item and keeps those components.
     */
    void altarTransform(String name, ItemLike output, ItemStack catalyst, ItemLike... ingredients);

    void shatterForge(ItemLike input, ItemLike output, int outputCount, int time, int monolithCount, int draw, Map<EssenceType, Integer> aspects);
}
