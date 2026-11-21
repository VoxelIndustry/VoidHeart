package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.armor.ArmorSight;
import net.voxelindustry.voidheart.client.model.mob.TreantModel;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.state.TreantRenderState;
import net.voxelindustry.voidheart.common.content.mob.DormantTreantTile;
import org.jspecify.annotations.Nullable;

/**
 * A Treant asleep in its trunk is only bark to the eye, but a Void Armor's spectral lens finds it there: its shape shows inside the wood as a hostile
 * silhouette, at the start of coming out, facing the way its trunk's position picks.
 */
public class DormantTreantRender implements BlockEntityRenderer<DormantTreantTile, DormantTreantRender.State>
{
    /** Just begun coming out, so it is drawn curled inside the trunk. */
    private static final float ASLEEP = 0.01F;

    private final TreantModel model;

    public static class State extends BlockEntityRenderState
    {
        public final TreantRenderState treant = new TreantRenderState();
        public int   color;
        public float yaw;
    }

    public DormantTreantRender(BlockEntityRendererProvider.Context context)
    {
        model = new TreantModel(context.bakeLayer(TreantModel.LAYER));
    }

    @Override
    public State createRenderState()
    {
        var state = new State();
        state.treant.emerge = ASLEEP;
        return state;
    }

    @Override
    public void extractRenderState(DormantTreantTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        var pos = tile.getBlockPos();
        state.color = ArmorSight.dormant(pos, partialTicks);
        state.yaw = Math.floorMod(pos.asLong() * 31, 4) * 90;
        state.treant.ageInTicks = tile.getLevel() == null ? 0 : tile.getLevel().getGameTime() + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.color == 0)
            return;
        poseStack.pushPose();
        // As a living thing's renderer stands its model, with its feet on the trunk's base
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yaw));
        poseStack.scale(-1, -1, 1);
        poseStack.translate(0, -1.501F, 0);
        collector.order(2).submitModel(model, state.treant, poseStack, VoidHeartRenderTypes.SILHOUETTE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                state.color, null, 0);
        poseStack.popPose();
    }
}
