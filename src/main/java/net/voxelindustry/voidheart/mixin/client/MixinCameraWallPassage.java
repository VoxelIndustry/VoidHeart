package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside a wall, sections behind solid stone would be culled as hidden, so the rooms on either side could vanish. Vanilla turns this culling off for
 * spectators inside blocks, and a player crossing with the Wraith Veil gets the same.
 */
@Mixin(Camera.class)
public abstract class MixinCameraWallPassage
{
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void voidheart$seeBothSides(CameraRenderState cameraState, DeltaTracker deltaTracker, CallbackInfo info)
    {
        var player = Minecraft.getInstance().player;
        if (player != null && WallPassages.isInside(player))
            cameraState.smartCull = false;
    }
}
