package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// The scroll wheel turns through a Void Armor wearer's racks only, never onto the keys of its sights
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerVoidArmor
{
    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
    private int voidheart$racksOnly(double wheel, int selected, int limit, Operation<Integer> original)
    {
        var player = minecraft.player;
        if (player != null && ArmorWear.wearing(player))
            return original.call(wheel, Math.min(selected, ArmorHotbar.RACKS - 1), ArmorHotbar.RACKS);
        return original.call(wheel, selected, limit);
    }
}
