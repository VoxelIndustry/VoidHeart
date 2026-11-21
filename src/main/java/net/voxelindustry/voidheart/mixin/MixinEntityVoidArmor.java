package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A Void Armor wearer rides nothing
@Mixin(Entity.class)
public abstract class MixinEntityVoidArmor
{
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void voidheart$noRiding(Entity vehicle, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> info)
    {
        if ((Object) this instanceof Player player && ArmorWear.wearing(player))
            info.setReturnValue(false);
    }
}
