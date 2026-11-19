package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.blight.BlightCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first-person arm and item held, loaded and drawn as a crossbow's: vanilla keeps that pose for its own crossbow by name.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinFirstPersonRendererBlightCrossbow
{
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean voidheart$blightCrossbow(ItemStack stack, Object item, Operation<Boolean> original)
    {
        return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof BlightCrossbowItem;
    }
}
