package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A charged Void Armor pays for its wearer's walking and jumping out of its battery instead of their hunger, by the block walked and by the powered jump
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerVoidArmor
{
    @Inject(method = "checkMovementStatistics", at = @At("HEAD"))
    private void voidheart$walked(double dx, double dy, double dz, CallbackInfo info)
    {
        ArmorWear.onMoved((ServerPlayer) (Object) this, dx, dz);
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void voidheart$jumped(CallbackInfo info)
    {
        ArmorWear.onJumped((ServerPlayer) (Object) this);
    }

    @WrapWithCondition(method = { "checkMovementStatistics", "jumpFromGround" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private boolean voidheart$frameFeedsMoving(ServerPlayer self, float amount)
    {
        return !ArmorWear.paysForMoving(self);
    }
}
