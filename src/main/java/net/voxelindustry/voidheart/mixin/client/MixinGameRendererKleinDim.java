package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.voxelindustry.voidheart.client.KleinDim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dims the scene around a Klein Star after the world and the hand are drawn and before the interface, where vanilla runs its own post effects.
 * Fabric has no event this late in the frame.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererKleinDim
{
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(method = "applyPostEffects", at = @At("TAIL"))
    private void dimAroundKleinStar(CallbackInfo info)
    {
        KleinDim.apply((GameRenderer) (Object) this, resourcePool);
    }
}
