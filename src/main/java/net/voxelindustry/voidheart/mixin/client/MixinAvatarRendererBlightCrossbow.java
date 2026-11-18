package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.blight.BlightCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player holding the loaded crossbow blight holds it ready, as one holding a loaded crossbow does: vanilla asks for its own crossbow by name.
 */
@Mixin(AvatarRenderer.class)
public class MixinAvatarRendererBlightCrossbow
{
    @WrapOperation(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private static boolean voidheart$blightCrossbow(ItemStack stack, Object item, Operation<Boolean> original)
    {
        return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof BlightCrossbowItem;
    }
}
