package net.voxelindustry.voidheart.gametest.fast.mixin;

import net.voxelindustry.voidheart.gametest.fast.FastGameTests;
import net.voxelindustry.voidheart.gametest.fast.VirtualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Jade refreshes its tooltips and their server data on the wall clock, so its tooltips go stale at test speed.
 */
@Mixin(targets = {"snownee.jade.overlay.WailaTickHandler", "snownee.jade.impl.ui.BoxElementImpl", "snownee.jade.addon.universal.ItemCollector"}, remap = false)
public class JadeClockMixin
{
    @Redirect(method = {"tickClient", "narrate", "updateRect", "update"}, at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 1)
    private long voidheart$virtualMillis()
    {
        return FastGameTests.ENABLED ? VirtualClock.millis() : System.currentTimeMillis();
    }
}
