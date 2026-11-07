package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.flask.FlaskLayout;
import net.voxelindustry.voidheart.common.content.flask.PlacedFlasksTile;
import org.jspecify.annotations.Nullable;

/**
 * Each placed flask as its 3D item model, standing on the block's floor. The models are opaque and stand apart, so nothing overlaps or sorts.
 */
public class PlacedFlasksRender implements BlockEntityRenderer<PlacedFlasksTile, PlacedFlasksRender.State>
{
    private static final float FLASK_SCALE = FlaskLayout.PLACED_SCALE;

    private final ItemModelResolver itemModelResolver;

    public PlacedFlasksRender(BlockEntityRendererProvider.Context context)
    {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState
    {
        public final ItemStackRenderState[] flasks = new ItemStackRenderState[FlaskLayout.MAX_FLASKS];
        public int  count;
        public long seed;

        public State()
        {
            for (int i = 0; i < flasks.length; i++)
                flasks[i] = new ItemStackRenderState();
        }
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(PlacedFlasksTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var flasks = tile.getFlasks();
        state.count = flasks.size();
        state.seed = tile.getBlockPos().asLong();
        for (int i = 0; i < state.count; i++)
            itemModelResolver.updateForTopItem(state.flasks[i], FlaskModels.forDisplay(flasks.get(i)), ItemDisplayContext.FIXED, tile.getLevel(), null, (int) state.seed + i);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.count == 0)
            return;

        var spots = FlaskLayout.spots(state.count);
        for (int i = 0; i < state.count; i++)
        {
            poseStack.pushPose();
            // Item models are centered, so lift by half the drawn height to stand on the floor
            poseStack.translate(spots[i][0] / 16, FLASK_SCALE / 2, spots[i][1] / 16);
            poseStack.rotate(Axis.YP.rotationDegrees(FlaskLayout.turn(state.seed, i, state.count)));
            poseStack.scale(FLASK_SCALE, FLASK_SCALE, FLASK_SCALE);
            state.flasks[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
