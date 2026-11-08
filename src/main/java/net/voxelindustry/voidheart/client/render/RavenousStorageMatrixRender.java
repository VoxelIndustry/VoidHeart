package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import org.jspecify.annotations.Nullable;

/**
 * The essence of each slot of a storage matrix, between the pillars of the column on its face, reaching out with its
 * fill as they do.
 */
public class RavenousStorageMatrixRender implements BlockEntityRenderer<RavenousStorageMatrixTile, RavenousStorageMatrixRender.State>
{
    // Out from the core center, in blocks: from the core body to just under the caps of a full column
    private static final float BOTTOM    = 15 / 16F;
    private static final float EMPTY_TOP = 15.6F / 16;
    private static final float FULL_TOP  = 31 / 16F;

    public static class State extends BlockEntityRenderState
    {
        public final boolean[] shown  = new boolean[RavenousStorageMatrixBlock.FACES.length];
        public final float[]   fills  = new float[RavenousStorageMatrixBlock.FACES.length];
        public final int[]     colors = new int[RavenousStorageMatrixBlock.FACES.length];
        public       float     time;
        public       long      seed;
    }

    public RavenousStorageMatrixRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(RavenousStorageMatrixTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var storage = tile.storage();
        for (int slot = 0; slot < state.shown.length; slot++)
        {
            var type = slot < storage.storageCount() ? storage.types()[slot] : null;
            state.shown[slot] = type != null;
            if (type == null)
                continue;
            state.fills[slot] = Mth.clamp(storage.amounts()[slot] / (float) storage.maximumAmount(), 0F, 1F);
            state.colors[slot] = VoidBeamRender.aspectColor(type);
        }
        state.time = tile.getLevel().getGameTime() + partialTicks;
        state.seed = tile.getBlockPos().asLong();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        for (int slot = 0; slot < state.shown.length; slot++)
        {
            if (!state.shown[slot])
                continue;

            int face = slot;
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            turnUpTo(poseStack, RavenousStorageMatrixBlock.FACES[slot]);
            poseStack.translate(-0.5, -0.5, -0.5);
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true),
                    (pose, buffer) -> RavenousStorageRender.essence(pose, buffer, BOTTOM, Mth.lerp(state.fills[face], EMPTY_TOP, FULL_TOP), FULL_TOP,
                            state.fills[face], state.colors[face], state.time, state.seed + face));
            poseStack.popPose();
        }
    }

    /**
     * Turns the column drawn upward toward its face.
     */
    private static void turnUpTo(PoseStack poseStack, Direction face)
    {
        switch (face)
        {
            case DOWN -> poseStack.rotateDegrees(Axis.XP, 180);
            case NORTH -> poseStack.rotateDegrees(Axis.XP, -90);
            case SOUTH -> poseStack.rotateDegrees(Axis.XP, 90);
            case EAST -> poseStack.rotateDegrees(Axis.ZP, -90);
            case WEST -> poseStack.rotateDegrees(Axis.ZP, 90);
            default ->
            {
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
