package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFireLook;
import net.voxelindustry.voidheart.common.content.mausoleum.FuneraryFire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes on every entity's render state whether the fire on it is a wisp's, so its flames are drawn green.
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRendererFuneraryFire
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void voidheart$extractFuneraryFire(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo callback)
    {
        state.setData(FuneraryFireLook.GREEN, state.displayFireAnimation && FuneraryFire.burnsGreen(entity));
    }
}
