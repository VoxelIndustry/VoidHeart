package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Minecraft;
import net.voxelindustry.voidheart.client.render.oculus.OculusControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Oculus buttons float in the air with no block or entity behind them, so their clicks are taken before the world's.
 */
@Mixin(Minecraft.class)
public class MixinMinecraftOculusControls
{
    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void pressOculusButton(CallbackInfo ci)
    {
        if (OculusControls.press())
        {
            // Vanilla sets this delay itself, without it holding the button would press every tick
            rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void punchOculusButton(CallbackInfoReturnable<Boolean> cir)
    {
        if (OculusControls.press())
            cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void keepOculusTableWhilePressing(boolean down, CallbackInfo ci)
    {
        if (down && OculusControls.isAimingAtButton())
            ci.cancel();
    }
}
