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
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import net.voxelindustry.voidheart.common.energy.storage.SocketWallGeometry;
import net.voxelindustry.voidheart.common.energy.storage.SocketWallTile;
import org.jspecify.annotations.Nullable;

/**
 * Each cell standing in its niche, drawn once with its own fill texture facing out of the wall, with the cell socket's
 * faint glow along its shard. Unlike the socket's crossed sprites, a niche is only seen from the front. The glow is
 * additive {@link VoidHeartRenderTypes#GLOW} around the sprite, five quads per filled cell, so twenty a frame at most.
 */
public class SocketWallRender implements BlockEntityRenderer<SocketWallTile, SocketWallRender.State>
{
    private static final int   RUBRUM_GLOW  = 0xFF9238;
    /** A 16-pixel sprite at this scale stands about five pixels tall, the room above the ledge. */
    private static final float CELL_SCALE   = 0.34F;
    /** Matches the socket's glow, scaled with the cell. */
    private static final float GLOW_HALF    = 0.75F / 16 * CELL_SCALE / 0.5F;
    /** The shard between the casing's caps, as a share of the sprite's height from its middle. */
    private static final float SHARD_REACH  = 0.25F * CELL_SCALE;
    private static final float GLOW_ALPHA   = 0.35F;
    private static final float ACTIVE_PULSE = 0.15F;

    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState
    {
        public final ItemStackRenderState[] items  = new ItemStackRenderState[SocketWallGeometry.SLOTS];
        public final float[]                fills  = new float[SocketWallGeometry.SLOTS];
        public final boolean[]              active = new boolean[SocketWallGeometry.SLOTS];
        public       Direction              facing = Direction.NORTH;
        public       float                  time;

        public State()
        {
            for (var slot = 0; slot < items.length; slot++)
                items[slot] = new ItemStackRenderState();
        }
    }

    public SocketWallRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(SocketWallTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);
        var seed = (int) tile.getBlockPos().asLong();
        for (var slot = 0; slot < SocketWallGeometry.SLOTS; slot++)
        {
            itemModelResolver.updateForTopItem(state.items[slot], tile.getCell(slot), ItemDisplayContext.FIXED, tile.getLevel(), null, seed + slot);
            state.fills[slot] = tile.fill(slot);
            state.active[slot] = tile.activity(slot) != CellSocketTile.Activity.IDLE;
        }
        state.facing = tile.facing();
        state.time = tile.getLevel().getGameTime() + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        for (var slot = 0; slot < SocketWallGeometry.SLOTS; slot++)
        {
            var item = state.items[slot];
            if (item.isEmpty())
                continue;

            var center = SocketWallGeometry.cellCenter(state.facing, slot);
            poseStack.pushPose();
            poseStack.translate(center.x, center.y, center.z);
            poseStack.rotate(MathUtil.quatFromAngleDegrees(-state.facing.toYRot(), MathUtil.POSITIVE_Y));
            poseStack.pushPose();
            poseStack.scale(CELL_SCALE, CELL_SCALE, CELL_SCALE);
            item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();

            var fill = Mth.clamp(state.fills[slot], 0, 1);
            if (fill > 0.001F)
            {
                var pulse = state.active[slot] ? 1 - ACTIVE_PULSE + ACTIVE_PULSE * Mth.sin(state.time * 0.3F + slot) : 1;
                var color = ARGB.color(Math.round(255 * GLOW_ALPHA * pulse), RUBRUM_GLOW);
                var top = Mth.lerp(fill, -SHARD_REACH, SHARD_REACH);
                collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> column(pose, buffer, -SHARD_REACH, top, color));
            }
            poseStack.popPose();
        }
    }

    private static void column(PoseStack.Pose pose, VertexConsumer buffer, float bottom, float top, int color)
    {
        float x0 = -GLOW_HALF, x1 = GLOW_HALF, z0 = -GLOW_HALF, z1 = GLOW_HALF;
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
