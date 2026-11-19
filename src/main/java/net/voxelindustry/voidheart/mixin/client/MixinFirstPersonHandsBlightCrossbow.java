package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.blight.BlightCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Which hands show in first person, as for a crossbow: vanilla asks for its own crossbow by name, so the crossbow blight is answered for it too.
 */
@Mixin(FirstPersonHandsAndItems.class)
public class MixinFirstPersonHandsBlightCrossbow
{
    @WrapOperation(method = { "evaluateWhichHandsToRender", "isChargedCrossbow" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private static boolean voidheart$blightCrossbow(ItemStack stack, Object item, Operation<Boolean> original)
    {
        return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof BlightCrossbowItem;
    }
}
