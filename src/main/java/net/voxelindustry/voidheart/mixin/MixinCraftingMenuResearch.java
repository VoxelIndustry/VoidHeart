package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The inventory and crafting table both go through here: refusing the recipe leaves the result slot empty. */
@Mixin(CraftingMenu.class)
public class MixinCraftingMenuResearch
{
    @WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private static boolean refuseLockedRecipe(ResultContainer result, ServerPlayer player, RecipeHolder<?> recipe, Operation<Boolean> original)
    {
        return Knowledge.canUse(player, recipe.id().identifier()) && original.call(result, player, recipe);
    }
}
