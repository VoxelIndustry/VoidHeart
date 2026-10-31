package net.voxelindustry.voidheart.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus holograms are cached GPU meshes drawn into the solid pass, which Fabric's events don't hand out.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererOculus
{
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "addMainPass", at = @At("HEAD"))
    private void prepareOculusHolograms(CallbackInfo ci)
    {
        OculusRenderer.prepare(levelRenderState.cameraRenderState);
    }

    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void drawOculusHolograms(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass, CallbackInfo ci)
    {
        OculusRenderer.draw(renderPass);
    }
}
