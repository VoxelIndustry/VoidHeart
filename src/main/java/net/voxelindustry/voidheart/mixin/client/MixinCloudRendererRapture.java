package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.renderer.CloudRenderer;
import net.voxelindustry.voidheart.client.curse.RaptureCurse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Clouds draw in front of the sky pass, where they would veil what the sky turns into during this curse.
 */
@Mixin(CloudRenderer.class)
public class MixinCloudRendererRapture
{
    @ModifyVariable(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int partCloudsForRapture(int color)
    {
        return RaptureCurse.partClouds(color);
    }
}
