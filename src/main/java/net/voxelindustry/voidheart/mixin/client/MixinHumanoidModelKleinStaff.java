package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a Klein Star grows on a staff, its bearer holds the staff out ahead of them, upright, so the star sits on its end. Vanilla has no arm pose
 * that holds an item out and up, and the star is known to the client alone, so the arm is turned here after vanilla has posed it.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelKleinStaff
{
    // The arm is lifted halfway to level, which stands the staff on its end: carried, the staff leans halfway between level and upright
    private static final float ARM_PITCH = -0.8F;
    private static final float ARM_YAW   = 0.1F;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void voidheart$holdStaffOut(HumanoidRenderState state, CallbackInfo callback)
    {
        Integer raised = state.getData(KleinStarRender.RAISED_ARM);
        if (raised == null || raised == 0)
            return;
        var arm = raised == 1 ? rightArm : leftArm;
        arm.xRot = ARM_PITCH;
        arm.yRot = raised == 1 ? -ARM_YAW : ARM_YAW;
        arm.zRot = 0;
    }
}
