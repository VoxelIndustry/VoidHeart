package net.voxelindustry.voidheart.common.setup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.recipe.RecipeManager;
import net.voxelindustry.steamlayer.recipe.SteamLayerRecipe;
import net.voxelindustry.steamlayer.recipe.category.RecipeCategory;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.vanilla.SteamLayerRecipeType;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.setup.VoidHeartItems.VOID_PEARL;

public class VoidHeartRecipes
{
    public static RecipeCategory<AltarRecipe> ALTAR_CATEGORY;

    private static final Identifier VOIDHEART_RECIPE = new Identifier(MODID, "altar/voidheart");

    public static void registerRecipes()
    {
        SteamLayerRecipe.RECIPE_CATEGORY_RELOAD_EVENT.register(VoidHeartRecipes::onRecipeReload);

        RecipeManager.addCategory(ALTAR_CATEGORY = new RecipeCategory<>(
                new Identifier(MODID, "altar"),
                new SteamLayerRecipeType<>((type, identifier) -> new AltarRecipe(identifier))));
    }

    private static void onRecipeReload(RecipeCategory<?> category)
    {
        if (category == ALTAR_CATEGORY)
        {
            ALTAR_CATEGORY.getRecipe(VOIDHEART_RECIPE).ifPresent(recipe -> recipe.onCraft((world, blockPos, recipeState, livingEntity) ->
            {
                NbtCompound tag = recipeState.getIngredientsConsumed(ItemStack.class).get(0).getOrCreateNbt();
                if (!tag.containsUuid("player"))
                    return;

                UUID playerID = tag.getUuid("player");
                ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
                VoidPocketState voidPocketState = VoidPocketState.getVoidPocketState(voidWorld);

                PlayerEntity player = world.getPlayerByUuid(playerID);

                if (!voidPocketState.hasPocket(playerID))
                {
                    voidPocketState.createPocket(voidWorld, playerID);
                    if (player != null)
                    {
                        for (int i = 0; i < 3; i++)
                        {
                            var voidPearls = new ItemStack(VOID_PEARL);
                            player.getInventory().offerOrDrop(voidPearls);
                        }

                        player.sendMessage(Text.translatable(MODID + ".pocket_created"), true);
                    }
                }
                else if (player != null)
                {
                    player.sendMessage(Text.translatable(MODID + ".pocket_already_exists"), true);
                }
            }));
        }
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
