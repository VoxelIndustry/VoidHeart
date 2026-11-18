package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.blight.HarvestWraithRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A wraith raised by the scythe blight is drawn by the renderer of the mob it was, as a husk of wraith ink: its own texture through the husk shader,
 * how much it frays in the tint, and where its feet are and how tall it is in the overlay coordinates. What it wore or held is left off.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererHusk
{
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @ModifyExpressionValue(method = SUBMIT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private RenderType voidheart$huskType(RenderType type, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        if (type == null || state.getData(HarvestWraithRender.HUSK) == null)
            return type;
        return VoidHeartRenderTypes.wraithHusk(((LivingEntityRenderer) (Object) this).getTextureLocation(state));
    }

    @ModifyExpressionValue(method = SUBMIT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int voidheart$huskFray(int tint, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        var husk = state.getData(HarvestWraithRender.HUSK);
        return husk == null ? tint : ARGB.color(255, (int) (Mth.clamp(husk.fray(), 0, 1) * 255), 255, 255);
    }

    @ModifyExpressionValue(method = SUBMIT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getOverlayCoords(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)I"))
    private int voidheart$huskBody(int overlay, @Local(argsOnly = true) LivingEntityRenderState state, @Local(argsOnly = true) CameraRenderState camera)
    {
        var husk = state.getData(HarvestWraithRender.HUSK);
        if (husk == null)
            return overlay;
        int feet = Mth.clamp((int) Math.round((state.y - camera.pos.y) * 64) + 32768, 0, 0xFFFF);
        int height = Mth.clamp(Math.round(husk.height() * 64), 1, 0xFFFF);
        return feet | height << 16;
    }

    @ModifyExpressionValue(method = SUBMIT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private boolean voidheart$huskBare(boolean render, @Local(argsOnly = true) LivingEntityRenderState state)
    {
        return render && state.getData(HarvestWraithRender.HUSK) == null;
    }
}
