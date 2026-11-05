package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.minecraft.client.Minecraft;
import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import net.voxelindustry.voidheart.gametest.fast.VirtualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moving the clock 50 ms before the frame reads it gives the frame exactly one tick.
 */
@Mixin(Minecraft.class)
public class MinecraftClockMixin
{
    @Inject(method = "runTick", at = @At("HEAD"))
    private void voidheart$advanceClock(CallbackInfo ci)
    {
        if (FastGameTests.ENABLED)
            VirtualClock.onFrame(((Minecraft) (Object) this).level != null);
    }
}
