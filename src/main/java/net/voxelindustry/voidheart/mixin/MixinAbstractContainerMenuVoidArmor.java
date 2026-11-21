package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A number key or the swap key over a slot trades with the hotbar or off hand directly, past their slots' own checks, so a Void Armor wearer can't
@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenuVoidArmor
{
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void voidheart$noSwapIntoFrame(int slotIndex, int buttonNum, ContainerInput input, Player player, CallbackInfo info)
    {
        if (input == ContainerInput.SWAP && ArmorWear.wearing(player))
            info.cancel();
    }
}
