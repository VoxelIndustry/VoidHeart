package net.voxelindustry.voidheart.client.render.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.client.armor.VoidArmorRender;
import net.voxelindustry.voidheart.client.model.armor.VoidArmorModel;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseBlock;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import org.jspecify.annotations.Nullable;

/**
 * A docked frame stands open on its base, facing the way the base was placed, its lacing brightening as it charges.
 * <p>
 * Render type: the frame's, see {@link VoidArmorRender}. Drawn even with the base itself out of view, since the frame towers over it; a docked base can't be
 * mined, so it never needs its cracks. Per frame: the frame's boxes, twice, per docked base in view.
 */
public class VoidArmorBaseRender implements BlockEntityRenderer<VoidArmorBaseTile, VoidArmorBaseRender.State>
{
    /** The plinth the frame stands on. */
    private static final float PLINTH = 3 / 16F;
    /** How lit a full frame's lacing is on its base, below how it burns once worn. */
    private static final float DOCKED_GLOW = 0.7F;

    private final VoidArmorModel model;
    private final VoidArmorModel[] glow;

    public static class State extends BlockEntityRenderState
    {
        final AvatarRenderState frame = new AvatarRenderState();
        boolean docked;
        float   yaw;
    }

    public VoidArmorBaseRender(BlockEntityRendererProvider.Context context)
    {
        model = new VoidArmorModel(context.bakeLayer(VoidArmorModel.LAYER), false);
        glow = VoidArmorRender.glow(() -> context.bakeLayer(VoidArmorModel.LAYER));
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VoidArmorBaseTile base, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(base, state, partialTicks, cameraPosition, breakProgress);
        state.docked = base.hasFrame();
        state.yaw = base.getBlockState().getValueOrElse(VoidArmorBaseBlock.FACING, Direction.SOUTH).toYRot();
        if (state.docked)
            state.frame.setData(VoidArmorLook.KEY, VoidArmorLook.standing(base.frame(), DOCKED_GLOW * VoidArmorItem.fill(base.frame())));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.docked)
            return;
        poseStack.pushPose();
        poseStack.translate(0.5, PLINTH, 0.5);
        VoidArmorRender.standing(model, glow, state.frame, state.yaw, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
