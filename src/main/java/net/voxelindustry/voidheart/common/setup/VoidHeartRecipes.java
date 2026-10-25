package net.voxelindustry.voidheart.common.setup;

import net.voxelindustry.steamlayer.recipe.RecipeManager;
import net.voxelindustry.steamlayer.recipe.SteamLayerRecipe;
import net.voxelindustry.steamlayer.recipe.category.RecipeCategory;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.vanilla.SteamLayerRecipeType;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.setup.VoidHeartItems.VOID_PEARL;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class VoidHeartRecipes
{
    public static final SteamLayerRecipeType<AltarRecipe> ALTAR_TYPE = new SteamLayerRecipeType<>((type, identifier) -> new AltarRecipe(identifier));
    public static final SteamLayerRecipeType<ShatterForgeRecipe> SHATTER_FORGE_TYPE = new SteamLayerRecipeType<>((type, identifier) -> new ShatterForgeRecipe(identifier));

    public static RecipeCategory<AltarRecipe> ALTAR_CATEGORY;
    public static RecipeCategory<ShatterForgeRecipe> SHATTER_FORGE_CATEGORY;

    private static final ResourceLocation VOIDHEART_RECIPE = new ResourceLocation(MODID, "altar/voidheart");

    public static void registerRecipes()
    {
        SteamLayerRecipe.RECIPE_CATEGORY_RELOAD_EVENT.register(VoidHeartRecipes::onRecipeReload);

        RecipeManager.addCategory(ALTAR_CATEGORY = new RecipeCategory<>(
                new ResourceLocation(MODID, "altar"),
                ALTAR_TYPE
        ));
        RecipeManager.addCategory(SHATTER_FORGE_CATEGORY = new RecipeCategory<>(
                new ResourceLocation(MODID, "shatter_forge"),
                SHATTER_FORGE_TYPE
        ));
    }

    private static void onRecipeReload(RecipeCategory<?> category)
    {
        if (category != ALTAR_CATEGORY)
            return;

        ALTAR_CATEGORY.getRecipe(VOIDHEART_RECIPE).ifPresent(recipe -> recipe.onCraft((world, blockPos, recipeState, livingEntity) ->
        {
            var tag = recipeState.getIngredientsConsumed(ItemStack.class).get(0).getOrCreateTag();
            if (!tag.hasUUID("player"))
                return;

            var playerID = tag.getUUID("player");
            var voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
            var voidPocketState = VoidPocketState.getVoidPocketState(voidWorld);

            var player = world.getPlayerByUUID(playerID);

            if (!voidPocketState.hasPocket(playerID))
            {
                voidPocketState.createPocket(voidWorld, playerID);
                if (player != null)
                {
                    for (int i = 0; i < 3; i++)
                    {
                        var voidPearls = new ItemStack(VOID_PEARL);
                        player.getInventory().placeItemBackInInventory(voidPearls);
                    }

                    player.displayClientMessage(Component.translatable(MODID + ".pocket_created"), true);
                }
            }
            else if (player != null)
            {
                player.displayClientMessage(Component.translatable(MODID + ".pocket_already_exists"), true);
            }
        }));
    }

    private static ItemStackRecipeIngredient ingredient(ItemLike item)
    {
        return ingredient(item, 1);
    }

    private static ItemStackRecipeIngredient ingredient(ItemLike item, int count)
    {
        return new ItemStackRecipeIngredient(new ItemStack(item, count));
    }
}
