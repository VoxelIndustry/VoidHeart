package net.voxelindustry.voidheart.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** A crafter works for nobody, so it can't be credited with any research. */
@Mixin(CrafterBlock.class)
public class MixinCrafterBlockResearch
{
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void refuseLockedRecipe(ServerLevel level, CraftingInput input, CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> callback)
    {
        callback.setReturnValue(callback.getReturnValue().filter(recipe -> !Knowledge.isLocked(level, recipe.id().identifier())));
    }
}
