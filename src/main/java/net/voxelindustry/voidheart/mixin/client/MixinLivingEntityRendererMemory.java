package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.InsightMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands each mob's remembered silhouette to its render state, since only the entity knows whether it was seen.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererMemory
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void voidheart$memory(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        state.setData(InsightMemory.ALPHA, InsightMemory.alpha(entity));
    }
}
