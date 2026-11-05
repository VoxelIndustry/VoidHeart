package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.voxelindustry.voidheart.client.ArmillaryTremor;
import net.voxelindustry.voidheart.client.render.ArmillaryPortalRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the view without touching where the player looks, so that aim stays where it was. Applied before the frustum is taken from the rotation.
 */
@Mixin(Camera.class)
public abstract class MixinCameraTremor
{
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void shake(DeltaTracker deltaTracker, CallbackInfo info)
    {
        if (!ArmillaryTremor.isShaking() || Minecraft.getInstance().level == null)
            return;

        float time = ArmillaryPortalRender.animationTime(Minecraft.getInstance().level.getGameTime(), deltaTracker.getGameTimeDeltaPartialTick(true));
        setRotation(yRot() + ArmillaryTremor.yawOffset(time), xRot() + ArmillaryTremor.pitchOffset(time));
    }
}
