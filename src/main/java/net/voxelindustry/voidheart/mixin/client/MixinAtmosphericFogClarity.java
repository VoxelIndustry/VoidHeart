package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.voxelindustry.voidheart.client.ClarityFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clarity thins the air's fog once every other rule has set it.
 */
@Mixin(AtmosphericFogEnvironment.class)
public class MixinAtmosphericFogClarity
{
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void voidheart$clarity(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo callback)
    {
        ClarityFog.apply(fog, renderDistance, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
