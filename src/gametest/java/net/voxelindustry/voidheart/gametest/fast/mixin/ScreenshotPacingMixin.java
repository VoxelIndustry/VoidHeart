package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl;
import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A screenshot ticks until the GPU hands the image back, which takes real time. Unpaced, that costs many more ticks
 * than at real speed, and the scene moves on past the moment the test meant to capture.
 */
@Mixin(value = ClientGameTestContextImpl.class, remap = false)
public class ScreenshotPacingMixin
{
    @Unique
    private static volatile boolean voidheart$inScreenshot;

    @Inject(method = "doTakeScreenshot", at = @At("HEAD"))
    private void voidheart$startScreenshot(CallbackInfoReturnable<?> cir)
    {
        voidheart$inScreenshot = true;
    }

    @Inject(method = "doTakeScreenshot", at = @At("RETURN"))
    private void voidheart$endScreenshot(CallbackInfoReturnable<?> cir)
    {
        voidheart$inScreenshot = false;
    }

    @Inject(method = "waitTick", at = @At("HEAD"))
    private void voidheart$paceScreenshot(CallbackInfo ci)
    {
        if (!FastGameTests.ENABLED || !voidheart$inScreenshot)
            return;
        try
        {
            Thread.sleep(10);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
        }
    }
}
