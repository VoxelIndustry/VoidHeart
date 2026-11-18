package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.secondheart.HeartDissolve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a player their second heart is taking apart or rebuilding through the dissolve, body, armour and held items alike, by handing the
 * renderer a collector that swaps each textured layer for the dissolve's.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererSecondHeart
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void voidheart$extractDissolve(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        state.setData(HeartDissolve.DISSOLVE, HeartDissolve.extract(entity, partialTicks));
    }

    @ModifyVariable(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector voidheart$dissolve(SubmitNodeCollector collector, LivingEntityRenderState state, PoseStack poseStack,
                                                  SubmitNodeCollector unused, CameraRenderState camera)
    {
        return HeartDissolve.wrap(collector, state, poseStack);
    }
}
