package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.recipe.RecipeHandler;
import net.voxelindustry.steamlayer.recipe.category.RecipeCategory;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.UUID;

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
                ingredient(VOID_PEARL, 8),
                ingredient(Items.ENDER_PEARL),
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
                ingredient(Blocks.OBSIDIAN),
                20,
                ingredient(ENDER_SHARD),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE),
                ingredient(Items.COBBLESTONE)
        ));

        ALTAR_CATEGORY.add(new AltarRecipe(
                ingredient(Items.AIR),
                new ItemStackRecipeIngredient(new ItemStack(VOID_HEART), false),
                20
        ).onCraft(((world, blockPos, recipeState, livingEntity) ->
        {
            CompoundTag tag = recipeState.getIngredientsConsumed(ItemStack.class).get(0).getOrCreateTag();
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
                    ItemStack voidPearls = new ItemStack(VOID_PEARL, 3);
                    if (!player.giveItemStack(voidPearls))
                        player.dropItem(voidPearls, true);

                    player.sendMessage(new TranslatableText(MODID + ".pocket_created"), true);
                }
            }
            else if (player != null)
            {
                player.sendMessage(new TranslatableText(MODID + ".pocket_already_exists"), true);
            }
        })));
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
