package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.voxelindustry.voidheart.client.armor.ArmorSight;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The Void Armor's night vision lifts the lightmap's dark the way the vanilla effect does, with its own colour, and whichever is brighter wins
@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapExtractorVoidArmor
{
    @Inject(method = "extract", at = @At("TAIL"))
    private void voidheart$frameNightVision(LightmapRenderState state, float partialTicks, CallbackInfo callback)
    {
        if (!state.needsUpdate)
            return;
        float sight = ArmorSight.nightVision(partialTicks);
        if (sight <= 0)
            return;
        var vanilla = new Vector3f(state.nightVisionColor).mul(state.nightVisionEffectIntensity);
        state.nightVisionColor = new Vector3f(ArmorSight.NIGHT_VISION_COLOR).mul(sight).max(vanilla);
        state.nightVisionEffectIntensity = 1;
    }
}
