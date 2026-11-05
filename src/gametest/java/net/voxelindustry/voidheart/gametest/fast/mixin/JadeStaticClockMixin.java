package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import net.voxelindustry.voidheart.gametest.fast.VirtualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The static half of {@link JadeClockMixin}, including the timer that asks the server for fresh tooltip data.
 */
@Mixin(targets = {"snownee.jade.impl.ObjectDataCenter", "snownee.jade.JadeClient"}, remap = false)
public class JadeStaticClockMixin
{
    @Redirect(method = {"isTimeElapsed", "resetTimer", "builtInOverrides"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 1)
    private static long voidheart$virtualMillis()
    {
        return FastGameTests.ENABLED ? VirtualClock.millis() : System.currentTimeMillis();
    }
}
