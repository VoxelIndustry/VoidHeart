package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.voxelindustry.voidheart.client.render.temple.MosaicStare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the view in a little while a temple's mosaic reaches for the player, through the same field of view change sprinting and bows use, so the camera
 * eases into it and the player's setting for such changes scales it.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayerMosaic
{
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void narrow(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> info)
    {
        float narrowing = MosaicStare.narrowing(effectScale);
        if (narrowing < 1)
            info.setReturnValue(info.getReturnValue() * narrowing);
    }
}
