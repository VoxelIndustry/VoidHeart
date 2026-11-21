package net.voxelindustry.voidheart.client.render.energy.engine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.engine.EngineFuel;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineTile;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.voxelindustry.voidheart.client.render.SolidQuads;
import net.voxelindustry.voidheart.client.render.energy.LeapingArc;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A Ravenous Engine devouring essence: the gold plates of its maw, the burning essence pulled down the chamber into it, and a leaping arc to what takes
 * its energy.
 * <p>
 * Render types: the plates are solid, depth-writing and world-lit like the block, in two layers a pixel apart so the closing plates never share a plane.
 * The column is the storages' translucent beacon veil, narrower than the pit so its sides never lie on the pit walls. The arc is additive {@code GLOW},
 * order independent where its crossed planes overlap. Per engine and frame: 24 plate quads, 15 column quads, and up to about 200 arc quads for a 16-block
 * arc, rebuilt each frame from the tile's animation, nothing cached.
 */
public class RavenousEngineRender implements BlockEntityRenderer<RavenousEngineTile, RavenousEngineRender.State>
{
    private static final int FULL_BRIGHT = 15728880;

    private static final RenderType PLATE_TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/ravenous_gold_block.png"));
    private static final Identifier VEIL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_veil.png");

    // Aspect colors from VoidBeamRender, which energy-looks.md names
    private static final int STILLNESS_COLOR = 0x7FB8FF;
    private static final int FIERY_COLOR     = 0xFF8A3D;
    private static final int SUFFERING_COLOR = 0xC0182F;

    private static final float PIXEL = 1 / 16F;

    // Plates: the lower pair slides along x, the upper pair along z, each by a quarter block
    private static final float PLATE_TRAVEL     = 4 * PIXEL;
    private static final float LOWER_PLATE_Y0   = 13 * PIXEL;
    private static final float LOWER_PLATE_Y1   = 14 * PIXEL;
    private static final float UPPER_PLATE_Y1   = 15 * PIXEL;
    // How far each gulp of Suffering pulls the plates closed
    private static final float GULP_CLOSE       = 0.35F;

    // Column: from the pit floor up to just under the chamber's caps
    private static final float COLUMN_BOTTOM    = 6 * PIXEL;
    private static final float COLUMN_TOP       = 1 + 15 * PIXEL;
    private static final float HALO_HALF        = 3.5F * PIXEL;
    private static final float BODY_HALF        = 2.5F * PIXEL;
    private static final float CORE_HALF        = 1.25F * PIXEL;
    private static final float HALO_ALPHA       = 0.24F;
    private static final float BODY_ALPHA       = 0.72F;
    private static final float CORE_ALPHA       = 1F;
    private static final float CORE_WHITE       = 0.45F;

    private static final float STILLNESS_SCROLL = 0.025F;
    private static final float FIERY_SCROLL     = 0.07F;
    private static final float FLARE_ALPHA      = 0.45F;
    private static final float FLARE_WIDTH      = 0.75F * PIXEL;
    private static final float FIERY_FLICKER    = 0.18F;
    private static final float SUFFERING_BODY   = 3 * PIXEL;
    private static final float SUFFERING_CORE   = 1.5F * PIXEL;
    // Suffering is drawn darker than its beam, and darker still in each gulp
    private static final float SUFFERING_DARK   = 0.3F;
    // Dark on dark voidstone, so it is drawn thicker to still read
    private static final float SUFFERING_ALPHA  = 0.15F;
    private static final float GULP_DARK        = 0.35F;
    private static final float GULP_DISTANCE    = 0.6F;

    // Arc: from the top of the chamber to the bundle receiving the energy
    private static final float ARC_START_Y      = 31 * PIXEL;

    public static class State extends BlockEntityRenderState
    {
        public float open;
        public float squeeze;
        @Nullable
        public EngineFuel fuel;
        public float      time;
        public float      pulse;
        public int        pulses;
        @Nullable
        public Vec3       arcEnd;
        public long       jagSeed;
        public final List<Float> leapHeads = new ArrayList<>();
    }

    public RavenousEngineRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(RavenousEngineTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var animation = tile.animation();
        state.open = animation.open(partialTicks);
        state.fuel = animation.displayedFuel();
        state.time = animation.time() + partialTicks;
        state.pulse = animation.pulse(partialTicks);
        state.pulses = animation.pulses();
        state.squeeze = state.fuel == EngineFuel.SUFFERING ? GULP_CLOSE * Mth.sin(state.pulse * Mth.PI) : 0;
        state.jagSeed = animation.jagSeed();

        var target = animation.displayedTarget();
        state.arcEnd = target == null ? null : Vec3.atCenterOf(target).subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
        state.leapHeads.clear();
        if (state.arcEnd != null)
        {
            for (var leap : animation.leaps())
                state.leapHeads.add(Mth.clamp((state.time - leap.start()) / leap.duration(), 0, 1));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        var closed = Mth.clamp(1 - state.open + state.squeeze, 0, 1);
        collector.submitCustomGeometry(poseStack, PLATE_TYPE, (pose, buffer) -> plates(pose, buffer, closed * PLATE_TRAVEL, state.lightCoords));

        if (state.fuel == null || state.open <= 0)
            return;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VEIL_TEXTURE, true), (pose, buffer) -> column(pose, buffer, state));
        if (state.arcEnd != null)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
                    LeapingArc.draw(pose, buffer, new Vector3f(0.5F, ARC_START_Y, 0.5F), state.arcEnd, state.jagSeed, state.leapHeads, state.open));
    }

    private static void plates(PoseStack.Pose pose, VertexConsumer buffer, float slide, int light)
    {
        // Lower pair, sliding in from the west and east sides
        cuboid(pose, buffer, light, slide, LOWER_PLATE_Y0, 4 * PIXEL, 4 * PIXEL + slide, LOWER_PLATE_Y1, 12 * PIXEL);
        cuboid(pose, buffer, light, 12 * PIXEL - slide, LOWER_PLATE_Y0, 4 * PIXEL, 1 - slide, LOWER_PLATE_Y1, 12 * PIXEL);
        // Upper pair, sliding in from the north and south over the lower one
        cuboid(pose, buffer, light, 4 * PIXEL, LOWER_PLATE_Y1, slide, 12 * PIXEL, UPPER_PLATE_Y1, 4 * PIXEL + slide);
        cuboid(pose, buffer, light, 4 * PIXEL, LOWER_PLATE_Y1, 12 * PIXEL - slide, 12 * PIXEL, UPPER_PLATE_Y1, 1 - slide);
    }

    /**
     * A box textured as a block model would be, from its position in the block. Its bottom rests on the maw and is left out.
     */
    private static void cuboid(PoseStack.Pose pose, VertexConsumer buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1)
    {
        SolidQuads.quad(pose, buffer, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1, 0, 1, 0);
        SolidQuads.quad(pose, buffer, light, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 1 - x1, 1 - y1, 1 - x0, 1 - y0, 0, 0, -1);
        SolidQuads.quad(pose, buffer, light, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, 1 - y1, x1, 1 - y0, 0, 0, 1);
        SolidQuads.quad(pose, buffer, light, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, z0, 1 - y1, z1, 1 - y0, -1, 0, 0);
        SolidQuads.quad(pose, buffer, light, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1 - z1, 1 - y1, 1 - z0, 1 - y0, 1, 0, 0);
    }

    /**
     * The essence burning, pulled down into the maw: Stillness in a slow steady draw, Fiery quick and flaring, Suffering thick and dark in heavy gulps.
     */
    private static void column(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var fade = state.open;
        float body = BODY_HALF;
        float core = CORE_HALF;
        float glow = 0;
        float scroll;
        int color;
        switch (state.fuel)
        {
            case FIERY ->
            {
                var flare = (1 - state.pulse) * (1 - state.pulse);
                glow = FLARE_ALPHA * flare;
                body += FLARE_WIDTH * flare;
                fade *= 1 - FIERY_FLICKER * (0.5F + 0.5F * Mth.sin(state.time * 2.3F) * Mth.sin(state.time * 0.7F + 1));
                scroll = state.time * FIERY_SCROLL;
                color = FIERY_COLOR;
            }
            case SUFFERING ->
            {
                body = SUFFERING_BODY;
                core = SUFFERING_CORE;
                var gulp = state.pulse * state.pulse * (3 - 2 * state.pulse);
                scroll = (state.pulses - 1 + gulp) * GULP_DISTANCE;
                color = ARGB.srgbLerp(SUFFERING_DARK + GULP_DARK * Mth.sin(state.pulse * Mth.PI), SUFFERING_COLOR, 0x000000);
                glow = SUFFERING_ALPHA;
            }
            default ->
            {
                scroll = state.time * STILLNESS_SCROLL;
                color = STILLNESS_COLOR;
            }
        }

        veilBox(pose, buffer, HALO_HALF, alpha(HALO_ALPHA * fade, color), scroll * 0.6F);
        veilBox(pose, buffer, Math.min(body, HALO_HALF - 0.25F * PIXEL), alpha(Math.min(1, (BODY_ALPHA + glow) * fade), color), scroll);
        veilBox(pose, buffer, core, alpha(Math.min(1, (CORE_ALPHA + glow) * fade), ARGB.srgbLerp(CORE_WHITE, color, 0xFFFFFF)), scroll * 1.4F);
    }

    private static int alpha(float alpha, int color)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), color);
    }

    /**
     * A box centered on the block from the pit floor up the chamber, its sides scrolling down into the maw.
     */
    private static void veilBox(PoseStack.Pose pose, VertexConsumer buffer, float half, int color, float scroll)
    {
        float x0 = 0.5F - half, x1 = 0.5F + half, z0 = 0.5F - half, z1 = 0.5F + half;
        float y0 = COLUMN_BOTTOM, y1 = COLUMN_TOP;
        float v0 = -scroll, v1 = (y1 - y0) - scroll;

        veil(pose, buffer, color, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, v0, v1, 0, 0, -1);
        veil(pose, buffer, color, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, v0, v1, 0, 0, 1);
        veil(pose, buffer, color, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, v0, v1, -1, 0, 0);
        veil(pose, buffer, color, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, v0, v1, 1, 0, 0);
        veil(pose, buffer, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, v0, v0 + 2 * half, 0, 1, 0);
    }

    private static void veil(PoseStack.Pose pose, VertexConsumer buffer, int color,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float vTop, float vBottom, int nx, int ny, int nz)
    {
        veilVertex(pose, buffer, color, ax, ay, az, 0, vTop, nx, ny, nz);
        veilVertex(pose, buffer, color, bx, by, bz, 0, vBottom, nx, ny, nz);
        veilVertex(pose, buffer, color, cx, cy, cz, 1, vBottom, nx, ny, nz);
        veilVertex(pose, buffer, color, dx, dy, dz, 1, vTop, nx, ny, nz);
    }

    private static void veilVertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * The arc reaches up to a pylon's field away, far beyond the block.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
