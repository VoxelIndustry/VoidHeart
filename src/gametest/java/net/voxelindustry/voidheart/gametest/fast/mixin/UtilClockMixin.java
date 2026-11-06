package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.minecraft.util.Util;
import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import net.voxelindustry.voidheart.gametest.fast.VirtualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Util.class)
public class UtilClockMixin
{
    @Inject(method = "getNanos", at = @At("HEAD"), cancellable = true)
    private static void voidheart$virtualNanos(CallbackInfoReturnable<Long> cir)
    {
        if (FastGameTests.ENABLED)
            cir.setReturnValue(VirtualClock.nanos());
    }
}
