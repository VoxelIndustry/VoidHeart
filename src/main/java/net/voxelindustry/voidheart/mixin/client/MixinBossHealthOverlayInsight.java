package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import net.voxelindustry.voidheart.client.DragoonReadout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under Insight, a Dragoon's bar gets the row of what it adapted to.
 */
@Mixin(BossHealthOverlay.class)
public class MixinBossHealthOverlayInsight
{
    @Inject(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V", at = @At("TAIL"))
    private void voidheart$dragoonReadout(GuiGraphicsExtractor graphics, int x, int y, BossEvent event, CallbackInfo callback)
    {
        DragoonReadout.draw(graphics, x, y, event);
    }
}
