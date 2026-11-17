package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.render.blight.FoldLook;
import net.voxelindustry.voidheart.common.content.blight.RapierStrikes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A target being unseamed goes flat black and collapses toward the crack its punctures tore, turning a little, until the fold ends and it snaps back.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererFold
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void voidheart$fold(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        var punctured = entity.getAttached(RapierStrikes.PUNCTURED);
        if (punctured == null || punctured.fold().isEmpty())
        {
            state.setData(FoldLook.FOLDED, false);
            return;
        }

        var fold = punctured.fold().get();
        float age = FoldLook.age(entity, fold, FoldLook.now(entity.level(), partialTicks));
        float scale = FoldLook.scale(age);
        float[] center = FoldLook.crackCenter(fold.slits());
        // Shrunk toward the crack rather than toward its feet
        float yaw = state.bodyRot * Mth.DEG_TO_RAD;
        double localX = center[0] * entity.getBbWidth();
        double localZ = center[2] * entity.getBbWidth();
        float pull = 1 - scale;
        state.x += (localX * Mth.cos(yaw) - localZ * Mth.sin(yaw)) * pull;
        state.y += center[1] * entity.getBbHeight() * pull;
        state.z += (localX * Mth.sin(yaw) + localZ * Mth.cos(yaw)) * pull;
        state.scale *= scale;
        state.bodyRot += FoldLook.spin(age);
        state.hasRedOverlay = false;
        state.setData(FoldLook.FOLDED, true);
    }

    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int voidheart$black(int tint, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        return state.getDataOrDefault(FoldLook.FOLDED, false) ? 0xFF000000 : tint;
    }

    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private boolean voidheart$noLayers(boolean render, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        // What it wears or holds would stay lit and give the shape away
        return render && !state.getDataOrDefault(FoldLook.FOLDED, false);
    }
}
