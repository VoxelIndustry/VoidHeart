package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import org.jspecify.annotations.Nullable;

/**
 * Column of the stored essence between the pillars of a Ravenous Storage, in its aspect's color, rising with the fill
 * as the pillars do: a bright core inside a translucent body, in a faint halo.
 */
public class RavenousStorageRender implements BlockEntityRenderer<RavenousStorageTile, RavenousStorageRender.State>
{
    private static final int FULL_BRIGHT = 15728880;

    private static final float BOTTOM    = 12 / 16F;
    // An attuned but empty storage keeps a sliver of its aspect on the rim
    private static final float EMPTY_TOP = 12.6F / 16;
    // Just under the caps of full pillars
    private static final float FULL_TOP  = 31 / 16F;

    private static final float HALO_HALF  = 4 / 16F;
    private static final float BODY_HALF  = 3 / 16F;
    private static final float CORE_HALF  = 1 / 16F;
    private static final float HALO_ALPHA = 0.14F;
    private static final float BODY_ALPHA = 0.5F;
    private static final float CORE_ALPHA = 0.9F;
    // Share of white mixed into the core so it reads as the brightest part
    private static final float CORE_WHITE = 0.45F;

    private static final float SURFACE_WAVE  = 0.5F / 16;
    private static final float SURFACE_SPEED = 0.08F;
    private static final float SCROLL        = 0.03F;

    public static class State extends BlockEntityRenderState
    {
        public boolean shown;
        public float   fill;
        public int     color;
        public float   time;
        public long    seed;
    }

    public RavenousStorageRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(RavenousStorageTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var storage = tile.storage();
        state.shown = !storage.isUnattuned() && tile.getBlockState().getValue(RavenousStorageBlock.PART) == RavenousStorageBlock.RavenousStoragePart.BASE;
        state.fill = Mth.clamp(storage.amount() / (float) storage.maximumAmount(), 0F, 1F);
        state.color = VoidBeamRender.aspectColor(storage.type());
        state.time = tile.getLevel().getGameTime() + partialTicks;
        state.seed = tile.getBlockPos().asLong();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.shown)
            return;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true),
                (pose, buffer) -> essence(pose, buffer, BOTTOM, Mth.lerp(state.fill, EMPTY_TOP, FULL_TOP), FULL_TOP, state.fill, state.color, state.time, state.seed));
    }

    /**
     * A column of essence centered on the block, from {@code bottom} up to about {@code top}: a bright core in a
     * translucent body, in a faint halo, its surface heaving. The core never passes {@code ceiling}.
     */
    static void essence(PoseStack.Pose pose, VertexConsumer buffer, float bottom, float top, float ceiling, float fill, int color, float time, long seed)
    {
        var phase = (seed & 0xFF) / 40F;
        // The surface of the body heaves slowly, the core reaches a little above it
        var surface = top + (fill > 0 ? SURFACE_WAVE * Mth.sin(time * SURFACE_SPEED + phase) : 0);
        var v = time * SCROLL;

        box(pose, buffer, HALO_HALF, bottom, surface, ARGB.color(Math.round(HALO_ALPHA * 255), color), v * 0.5F);
        box(pose, buffer, BODY_HALF, bottom, surface, ARGB.color(Math.round(BODY_ALPHA * 255), color), v);
        var core = ARGB.srgbLerp(CORE_WHITE, color, 0xFFFFFF);
        box(pose, buffer, CORE_HALF, bottom, Math.min(ceiling, surface + CORE_HALF), ARGB.color(Math.round(CORE_ALPHA * 255), core), v * 1.5F);
    }

    /**
     * Box centered on the block, from the rim up, its sides scrolling upward. Its bottom sits on the rim and is left out.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float half, float bottom, float top, int color, float scroll)
    {
        float x0 = 0.5F - half, x1 = 0.5F + half, z0 = 0.5F - half, z1 = 0.5F + half;
        float y0 = bottom, y1 = top;
        float v0 = -scroll, v1 = (y1 - y0) - scroll;

        quad(pose, buffer, color, Direction.NORTH, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, v0, v1);
        quad(pose, buffer, color, Direction.SOUTH, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, v0, v1);
        quad(pose, buffer, color, Direction.WEST, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, v0, v1);
        quad(pose, buffer, color, Direction.EAST, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, v0, v1);
        quad(pose, buffer, color, Direction.UP, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, v0, v0 + 2 * half);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, Direction facing,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float vTop, float vBottom)
    {
        var normal = facing.getUnitVec3i();
        vertex(pose, buffer, color, ax, ay, az, 0, vTop, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, color, bx, by, bz, 0, vBottom, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, color, cx, cy, cz, 1, vBottom, normal.getX(), normal.getY(), normal.getZ());
        vertex(pose, buffer, color, dx, dy, dz, 1, vTop, normal.getX(), normal.getY(), normal.getZ());
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
