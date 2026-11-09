package net.voxelindustry.voidheart.client.render.energy.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketGeometry;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import org.jspecify.annotations.Nullable;

/**
 * The cell standing in its socket's clasp, drawn with its own fill texture as two crossed sprites so it reads from
 * every side, with a faint glow along its shard that drains downward as it discharges and fills upward as it charges.
 * The glow is additive {@link VoidHeartRenderTypes#GLOW} inside the crossed sprites, five quads a frame.
 */
public class CellSocketRender implements BlockEntityRenderer<CellSocketTile, CellSocketRender.State>
{
    private static final int   RUBRUM_GLOW  = 0xFF9238;
    private static final float CELL_SCALE   = 0.5F;
    private static final float GLOW_HALF    = 0.75F / 16;
    /** The shard inside the casing, between the end caps, in blocks from the socket's floor. */
    private static final float SHARD_BOTTOM = CellSocketGeometry.CELL_BOTTOM + 1.5F / 16;
    private static final float SHARD_TOP    = CellSocketGeometry.CELL_TOP - 1.5F / 16;
    private static final float GLOW_ALPHA   = 0.35F;
    private static final float ACTIVE_PULSE = 0.15F;

    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState
    {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float   fill;
        public boolean active;
        public float   time;
    }

    public CellSocketRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CellSocketTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        itemModelResolver.updateForTopItem(state.item, tile.getCell(), ItemDisplayContext.FIXED, tile.getLevel(), null, (int) tile.getBlockPos().asLong());
        state.fill = tile.fill();
        state.active = tile.activity() != CellSocketTile.Activity.IDLE;
        state.time = tile.getLevel().getGameTime() + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.item.isEmpty())
            return;

        var middle = (CellSocketGeometry.CELL_BOTTOM + CellSocketGeometry.CELL_TOP) / 2;
        for (var turn = 0; turn < 2; turn++)
        {
            poseStack.pushPose();
            poseStack.translate(0.5F, middle, 0.5F);
            poseStack.rotate(MathUtil.quatFromAngleDegrees(90 * turn, MathUtil.POSITIVE_Y));
            poseStack.scale(CELL_SCALE, CELL_SCALE, CELL_SCALE);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.fill <= 0.001F)
            return;
        var pulse = state.active ? 1 - ACTIVE_PULSE + ACTIVE_PULSE * Mth.sin(state.time * 0.3F) : 1;
        var color = ARGB.color(Math.round(255 * GLOW_ALPHA * pulse), RUBRUM_GLOW);
        var top = Mth.lerp(Mth.clamp(state.fill, 0, 1), SHARD_BOTTOM, SHARD_TOP);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> column(pose, buffer, SHARD_BOTTOM, top, color));
    }

    private static void column(PoseStack.Pose pose, VertexConsumer buffer, float bottom, float top, int color)
    {
        float x0 = 0.5F - GLOW_HALF, x1 = 0.5F + GLOW_HALF, z0 = 0.5F - GLOW_HALF, z1 = 0.5F + GLOW_HALF;
        vertexQuad(pose, buffer, color, x0, bottom, z0, x1, bottom, z0, x1, top, z0, x0, top, z0);
        vertexQuad(pose, buffer, color, x1, bottom, z1, x0, bottom, z1, x0, top, z1, x1, top, z1);
        vertexQuad(pose, buffer, color, x0, bottom, z1, x0, bottom, z0, x0, top, z0, x0, top, z1);
        vertexQuad(pose, buffer, color, x1, bottom, z0, x1, bottom, z1, x1, top, z1, x1, top, z0);
        vertexQuad(pose, buffer, color, x0, top, z0, x1, top, z0, x1, top, z1, x0, top, z1);
    }

    private static void vertexQuad(PoseStack.Pose pose, VertexConsumer buffer, int color,
                                   float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz)
    {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }
}
