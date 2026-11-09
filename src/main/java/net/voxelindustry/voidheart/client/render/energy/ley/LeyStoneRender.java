package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import org.jspecify.annotations.Nullable;

/**
 * The channel down the standing stone's face, lit ley teal while it carries a spur, as bright as its line flows. Additive glow over the carved groove, so
 * overlapping halos never flicker. At most 4 segments of 5 quads each per stone, rebuilt every frame.
 */
public class LeyStoneRender implements BlockEntityRenderer<LeyStoneTile, LeyStoneRender.State>
{
    /**
     * Channel segments in model pixels, facing north: centre x, bottom, top and the face's z. They match models/block/ley_stone.json.
     */
    private static final float[][] CHANNEL = {
            { 8, 1, 12, 4 },
            { 8.5F, 12, 21, 4.5F },
            { 9, 21, 28, 5 },
            { 9.5F, 28, 30, 5.5F },
    };

    private static final float CORE_HALF  = 0.9F;
    private static final float HALO_HALF  = 2.6F;
    // In front of the groove, so the glow never fights the face it lies on
    private static final float LIFT       = 0.05F;
    private static final float CORE_ALPHA = 0.95F;
    private static final float CORE_FLOOR = 0.3F;
    private static final float HALO_ALPHA = 0.28F;

    public static class State extends BlockEntityRenderState
    {
        float     level;
        Direction facing = Direction.NORTH;
    }

    public LeyStoneRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(LeyStoneTile stone, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(stone, state, partialTicks, cameraPosition, breakProgress);
        state.level = LeyLook.flowLevel(stone.glow(partialTicks));
        state.facing = stone.facing();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.level < 0.01F)
            return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        poseStack.translate(-0.5, 0, -0.5);

        float core = (CORE_FLOOR + (CORE_ALPHA - CORE_FLOOR) * state.level);
        float halo = HALO_ALPHA * state.level;
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var segment : CHANNEL)
                drawSegment(pose, buffer, segment, core, halo);
        });
        poseStack.popPose();
    }

    private static void drawSegment(PoseStack.Pose pose, VertexConsumer buffer, float[] segment, float core, float halo)
    {
        float centre = segment[0];
        float bottom = segment[1] / 16;
        float top = segment[2] / 16;
        float z = (segment[3] - LIFT) / 16;

        int bright = LeyLook.color(LeyLook.TEAL_LIGHT, core);
        int edge = LeyLook.color(LeyLook.TEAL, core * 0.8F);
        int glow = LeyLook.color(LeyLook.TEAL, halo);
        int none = LeyLook.color(LeyLook.TEAL, 0);

        float coreLeft = (centre - CORE_HALF) / 16;
        float coreRight = (centre + CORE_HALF) / 16;
        float haloLeft = (centre - HALO_HALF) / 16;
        float haloRight = (centre + HALO_HALF) / 16;
        float middle = centre / 16;

        // The core is brightest down its middle, then soft halos spill onto the stone either side
        quad(pose, buffer, coreLeft, middle, bottom, top, z, edge, bright);
        quad(pose, buffer, middle, coreRight, bottom, top, z, bright, edge);
        quad(pose, buffer, haloLeft, coreLeft, bottom, top, z, none, glow);
        quad(pose, buffer, coreRight, haloRight, bottom, top, z, glow, none);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float left, float right, float bottom, float top, float z, int leftColor, int rightColor)
    {
        LeyLook.vertex(buffer, pose, left, bottom, z, leftColor);
        LeyLook.vertex(buffer, pose, right, bottom, z, rightColor);
        LeyLook.vertex(buffer, pose, right, top, z, rightColor);
        LeyLook.vertex(buffer, pose, left, top, z, leftColor);
    }
}
