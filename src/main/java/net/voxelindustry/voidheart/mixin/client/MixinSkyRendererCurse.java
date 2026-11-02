package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Curses reshape the night sky itself, so they must draw inside the sky pass for the world to stay in front of them. Fabric's events don't reach it.
 */
@Mixin(SkyRenderer.class)
public class MixinSkyRendererCurse
{
    @Inject(method = "render", at = @At("HEAD"))
    private void prepareCurseSky(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci)
    {
        CurseDirector.prepareSky(state);
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void hideStarsForCurse(RenderPass renderPass, float starBrightness, PoseStack poseStack, CallbackInfo ci)
    {
        if (CurseDirector.hidesStars())
            ci.cancel();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void drawCurseSky(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci)
    {
        CurseDirector.drawSky(renderPass);
    }
}
