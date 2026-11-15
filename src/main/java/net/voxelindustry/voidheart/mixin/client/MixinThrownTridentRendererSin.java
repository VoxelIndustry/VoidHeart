package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.voxelindustry.voidheart.client.render.sin.SinTridentRender;
import net.voxelindustry.voidheart.common.content.sin.SinCarrier;
import org.spongepowered.asm.mixin.Final;
import net.voxelindustry.voidheart.client.render.sin.SinTridentState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A thrown sin trident keeps its inlay in flight, drawn over the trident before its pose is popped
@Mixin(ThrownTridentRenderer.class)
public abstract class MixinThrownTridentRendererSin
{
    @Shadow
    @Final
    private TridentModel model;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;Lnet/minecraft/client/renderer/entity/state/ThrownTridentRenderState;F)V", at = @At("TAIL"))
    private void voidheart$extractSin(ThrownTrident entity, ThrownTridentRenderState state, float partialTicks, CallbackInfo info)
    {
        ((SinTridentState) state).voidheart$setInlay(SinTridentRender.overlayOf(((SinCarrier) entity).voidheart$sin()));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ThrownTridentRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void voidheart$submitInlay(ThrownTridentRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info)
    {
        var inlay = ((SinTridentState) state).voidheart$inlay();
        if (inlay != null)
            SinTridentRender.submitInlay(model, inlay, poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
}
