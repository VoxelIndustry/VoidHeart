package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.client.armor.VoidArmorRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// In first person, a Void Armor wearer sees the frame's plated forearm and gauntlet around what they hold, not their own arm
@Mixin(AvatarRenderer.class)
public class MixinAvatarRendererVoidArmor
{
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void voidheart$frameArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm, boolean hasSleeve,
                                    CallbackInfo callback)
    {
        var player = Minecraft.getInstance().player;
        var model = VoidArmorRender.firstPersonModel();
        if (player == null || model == null)
            return;
        var look = VoidArmorLook.of(player, 1);
        if (!look.shown())
            return;

        var own = (AvatarRenderer<?>) (Object) this;
        boolean right = arm == own.getModel().rightArm;
        var frameArm = right ? model.rightArm : model.leftArm;
        frameArm.resetPose();
        frameArm.visible = true;
        frameArm.zRot = right ? 0.1F : -0.1F;
        // The shoulder guard would hang across the view, so the arm comes alone
        frameArm.getChild("guard").visible = false;
        frameArm.getChild("piston").visible = look.exoskeleton();
        VoidArmorRender.submitArm(frameArm, poseStack, collector, lightCoords);
        callback.cancel();
    }
}
