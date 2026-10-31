package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.extract.LevelExtractor;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block change and chunk arrival funnels through here, which is when hologram meshes go stale.
 */
@Mixin(LevelExtractor.class)
public class MixinLevelExtractorOculus
{
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void markOculusSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci)
    {
        OculusRenderer.onSectionDirty(sectionX, sectionY, sectionZ);
    }
}
