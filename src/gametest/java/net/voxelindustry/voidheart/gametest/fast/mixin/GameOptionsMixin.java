package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Options;
import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each tick is one frame, so the frame rate caps the test speed. The frame limiter also waits on the game's clock,
 * which holds still within a frame, so it must stay off.
 */
@Mixin(value = ClientGameTestContextImpl.class, remap = false)
public class GameOptionsMixin
{
    @Inject(method = "initGameOptions", at = @At("HEAD"))
    private static void voidheart$uncapFrames(Options options, CallbackInfo ci)
    {
        if (!FastGameTests.ENABLED)
            return;
        options.enableVsync().set(false);
        options.framerateLimit().set(260);
        options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
    }
}
