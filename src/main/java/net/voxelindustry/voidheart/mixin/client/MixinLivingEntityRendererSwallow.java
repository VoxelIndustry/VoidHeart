package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.render.blight.HoleLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob drawn in by the spear's hole darkens the nearer it gets, as if the dark were already on it.
 */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererSwallow
{
    private static final float SWALLOWED_RED = 0.2F;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void voidheart$swallowed(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        float swallowed = HoleLook.swallowed(entity, partialTicks);
        state.setData(HoleLook.SWALLOWED, swallowed);
        // A crushed mob would flash its hurt red through the dark, so deep in it does not
        if (swallowed > SWALLOWED_RED)
            state.hasRedOverlay = false;
    }

    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int voidheart$darken(int tint, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        float swallowed = state.getDataOrDefault(HoleLook.SWALLOWED, 0F);
        if (swallowed <= 0)
            return tint;
        float kept = 1 - swallowed;
        return ARGB.color(ARGB.alpha(tint), (int) (ARGB.red(tint) * kept), (int) (ARGB.green(tint) * kept), (int) (ARGB.blue(tint) * kept));
    }
}
