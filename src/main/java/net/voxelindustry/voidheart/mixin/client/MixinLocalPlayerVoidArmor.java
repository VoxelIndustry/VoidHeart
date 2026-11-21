package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.LocalPlayer;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The local player works out crouching from the sneak key alone, so a Void Armor wearer holding it to step out would slow to a crouch
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerVoidArmor
{
    @ModifyReturnValue(method = "isCrouching", at = @At("RETURN"))
    private boolean voidheart$noCrouch(boolean crouching)
    {
        return crouching && !ArmorWear.wearing((LocalPlayer) (Object) this);
    }
}
