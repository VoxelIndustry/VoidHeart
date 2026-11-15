package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player's own hands are gone while their second heart has dissolved them; the view is near black by then.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinFirstPersonHandsSecondHeart
{
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void voidheart$noHandsWhileDissolved(CallbackInfo info)
    {
        var player = Minecraft.getInstance().player;
        if (player != null && SecondHeart.held(player))
            info.cancel();
    }
}
