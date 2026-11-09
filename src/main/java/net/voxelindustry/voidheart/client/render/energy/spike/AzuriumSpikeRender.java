package net.voxelindustry.voidheart.client.render.energy.spike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeTile;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.function.UnaryOperator;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An Azurium Spike driven into the land: a faceted crystal stake bound by a gold collar, leaning as it was driven in. While it converts, bands of ley
 * teal light climb into its base and turn azurium blue as they rise, and the energy leaves its tip as the pylons' leaping arc.
 * <p>
 * Render types: the stake and collar are solid, depth-writing and world-lit like a block, since the block itself draws nothing (the lean is the
 * tile's). The collar's faces stand a pixel outside the stake's, and the facets cross the stake at an angle, so no faces share a plane. Bands and the arc
 * are additive {@code GLOW}, order independent; bands sit a fraction of a pixel outside the crystal, whose depth hides their far side. While either
 * block is mined, the solid parts are drawn again with the vanilla crumbling decal, which sits on their own faces. Per spike and frame: 42 solid
 * quads (twice that while mined), up to about 40 band quads, and up to about 200 arc quads for a 16-block arc, rebuilt each frame, nothing cached.
 */
public class AzuriumSpikeRender implements BlockEntityRenderer<AzuriumSpikeTile, AzuriumSpikeRender.State>
{
    private static final RenderType CRYSTAL_TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/azurium_spike.png"));
    private static final RenderType COLLAR_TYPE  = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/cut_ravenous_gold.png"));

    // Colors from energy-looks.md: ley teal is an awake Old Anchor, azurium tones are sampled from the bundle textures
    private static final int LEY_TEAL      = 0x36C5B0;
    private static final int AZURIUM_LIGHT = 0x9EECFF;
    private static final int AZURIUM_MID   = 0x3D7ED9;

    private static final float PIXEL = 1 / 16F;

    // Stake, from the ground up: a shaft sunk a little into the ground so leaning never lifts its foot, a neck, and a point
    private static final float SHAFT_HALF   = 2 * PIXEL;
    private static final float SHAFT_BOTTOM = -2 * PIXEL;
    private static final float SHAFT_TOP    = 24 * PIXEL;
    private static final float NECK_HALF    = 1.5F * PIXEL;
    private static final float NECK_TOP     = 28 * PIXEL;
    private static final float POINT_HALF   = PIXEL;
    private static final float POINT_TOP    = 31 * PIXEL;
    // The texture runs once up the whole stake
    private static final float STAKE_HEIGHT = 32 * PIXEL;

    private static final float COLLAR_HALF   = 3 * PIXEL;
    private static final float COLLAR_BOTTOM = -PIXEL;
    private static final float COLLAR_TOP    = 3 * PIXEL;

    // Facets: two short crystals growing out of the stake, like those of a bundle
    private static final float FACET_HALF = PIXEL;
    private static final float[][] FACETS = {
            // height on the stake, length, yaw in degrees, tilt from upright in degrees
            {9 * PIXEL, 6 * PIXEL, 20, 28},
            {16 * PIXEL, 5 * PIXEL, 200, 32},
    };

    // Bands: rings of light climbing from the collar to the neck
    private static final float BAND_BOTTOM     = COLLAR_TOP;
    private static final float BAND_TOP        = NECK_TOP;
    private static final float BAND_HEIGHT     = 2.5F * PIXEL;
    private static final float BAND_OFFSET     = 0.15F * PIXEL;
    private static final float HALO_OFFSET     = 0.6F * PIXEL;
    private static final float BAND_ALPHA      = 0.85F;
    private static final float HALO_ALPHA      = 0.3F;
    // Fraction of the climb over which a band fades in at the base and out at the neck
    private static final float BAND_EDGE       = 0.15F;
    // The faint teal at the foot while converting, where the ley enters
    private static final float FOOT_HEIGHT     = 4 * PIXEL;
    private static final float FOOT_ALPHA      = 0.22F;

    // Arc: from the point to the bundle receiving the energy, as the pylons draw theirs
    private static final float JOINT_SPACING  = 1.5F;
    private static final int   MIN_JOINTS     = 6;
    private static final float JOINT_OFFSET   = 0.25F;
    private static final int   SUBDIVISIONS   = 4;
    private static final float CORE_WIDTH     = 0.035F;
    private static final float HALO_WIDTH     = 0.09F;
    private static final float ARC_CORE_ALPHA = 0.75F;
    private static final float ARC_HALO_ALPHA = 0.25F;
    private static final float LEAP_LENGTH    = 1.5F;
    private static final float LEAP_WIDEN     = 1.6F;

    public static class State extends BlockEntityRenderState
    {
        public float leanDegrees;
        public float leanYaw;
        public float fade;
        public final List<Float> bandHeads = new ArrayList<>();
        @Nullable
        public Vec3  arcEnd;
        public final Vector3f arcStart = new Vector3f();
        public long  jagSeed;
        public final List<Float> leapHeads = new ArrayList<>();
    }

    public AzuriumSpikeRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(AzuriumSpikeTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress != null ? breakProgress : crumbling(tile, cameraPosition));

        var animation = tile.animation();
        state.leanDegrees = tile.leanDegrees();
        state.leanYaw = tile.leanYaw();
        state.fade = animation.fade(partialTicks);
        var time = animation.time() + partialTicks;

        state.bandHeads.clear();
        for (var band : animation.bands())
            state.bandHeads.add(band.progress(time));

        lean(state.leanYaw, state.leanDegrees).transform(state.arcStart.set(0, POINT_TOP, 0)).add(0.5F, 0, 0.5F);
        var target = animation.displayedTarget();
        state.arcEnd = target == null ? null : Vec3.atCenterOf(target).subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
        state.jagSeed = animation.jagSeed();
        state.leapHeads.clear();
        if (state.arcEnd != null)
        {
            for (var leap : animation.leaps())
                state.leapHeads.add(leap.progress(time));
        }
    }

    /**
     * The game never hands cracks to a block entity drawn off screen, as this one must be for its arc, so it looks for its own, on the base or the tip.
     */
    private static ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling(AzuriumSpikeTile tile, Vec3 cameraPosition)
    {
        if (!(tile.getLevel() instanceof ClientLevel level))
            return null;

        var destruction = level.destructionProgress();
        if (destruction.isEmpty())
            return null;

        var pos = tile.getBlockPos();
        var furthest = Math.max(progressAt(destruction.get(pos.asLong())), progressAt(destruction.get(pos.above().asLong())));
        if (furthest < 0)
            return null;

        var cameraPose = new PoseStack();
        cameraPose.translate(pos.getX() - cameraPosition.x, pos.getY() - cameraPosition.y, pos.getZ() - cameraPosition.z);
        return new ModelFeatureRenderer.CrumblingOverlay(furthest, cameraPose.last());
    }

    private static int progressAt(@Nullable SortedSet<BlockDestructionProgress> progresses)
    {
        return progresses == null || progresses.isEmpty() ? -1 : progresses.last().getProgress();
    }

    private static Quaternionf lean(float yawDegrees, float leanDegrees)
    {
        return new Quaternionf().rotateY(yawDegrees * Mth.DEG_TO_RAD).rotateX(leanDegrees * Mth.DEG_TO_RAD);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotate(lean(state.leanYaw, state.leanDegrees));

        solid(state, poseStack, collector, CRYSTAL_TYPE, COLLAR_TYPE, UnaryOperator.identity());
        var breaking = state.breakProgress;
        if (breaking != null)
        {
            var crumbling = ModelBakery.DESTROY_TYPES.get(breaking.progress());
            solid(state, poseStack, collector, crumbling, crumbling, buffer -> new SheetedDecalTextureGenerator(buffer, breaking.cameraPose(), 1));
        }
        if (state.fade > 0)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> bands(pose, buffer, state));
        poseStack.popPose();

        if (state.fade > 0 && state.arcEnd != null)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> arc(pose, buffer, state));
    }

    /**
     * The stake, its facets and its collar: once in their own textures, and again under the cracks while mined.
     */
    private static void solid(State state, PoseStack poseStack, SubmitNodeCollector collector, RenderType crystalType, RenderType collarType,
                              UnaryOperator<VertexConsumer> wrap)
    {
        collector.submitCustomGeometry(poseStack, crystalType, (pose, buffer) -> stake(pose, wrap.apply(buffer), state.lightCoords));
        for (var facet : FACETS)
        {
            poseStack.pushPose();
            poseStack.translate(0, facet[0], 0);
            poseStack.rotateDegrees(Axis.YP, facet[2]);
            poseStack.rotateDegrees(Axis.ZP, -facet[3]);
            // Starts inside the stake, so its foot is hidden in the crystal
            collector.submitCustomGeometry(poseStack, crystalType, (pose, buffer) ->
                    box(pose, wrap.apply(buffer), state.lightCoords, -FACET_HALF, -FACET_HALF, -FACET_HALF, FACET_HALF, facet[1], FACET_HALF, STAKE_HEIGHT));
            poseStack.popPose();
        }
        collector.submitCustomGeometry(poseStack, collarType, (pose, buffer) ->
                box(pose, wrap.apply(buffer), state.lightCoords, -COLLAR_HALF, COLLAR_BOTTOM, -COLLAR_HALF, COLLAR_HALF, COLLAR_TOP, COLLAR_HALF, 1));
    }

    private static void stake(PoseStack.Pose pose, VertexConsumer buffer, int light)
    {
        box(pose, buffer, light, -SHAFT_HALF, SHAFT_BOTTOM, -SHAFT_HALF, SHAFT_HALF, SHAFT_TOP, SHAFT_HALF, STAKE_HEIGHT);
        box(pose, buffer, light, -NECK_HALF, SHAFT_TOP, -NECK_HALF, NECK_HALF, NECK_TOP, NECK_HALF, STAKE_HEIGHT);
        box(pose, buffer, light, -POINT_HALF, NECK_TOP, -POINT_HALF, POINT_HALF, POINT_TOP, POINT_HALF, STAKE_HEIGHT);
    }

    /**
     * A box centered on the stake, textured by its position: across the texture for its width, and up it over {@code textureHeight}.
     * Its bottom is always in the ground or the crystal, and is left out.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1, float textureHeight)
    {
        float u0x = x0 + 0.5F, u1x = x1 + 0.5F, u0z = z0 + 0.5F, u1z = z1 + 0.5F;
        float vTop = v(y1, textureHeight), vBottom = v(y0, textureHeight);

        quad(pose, buffer, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0x, u0z, u1x, u1z, 0, 1, 0);
        quad(pose, buffer, light, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 1 - u1x, vTop, 1 - u0x, vBottom, 0, 0, -1);
        quad(pose, buffer, light, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, u0x, vTop, u1x, vBottom, 0, 0, 1);
        quad(pose, buffer, light, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, u0z, vTop, u1z, vBottom, -1, 0, 0);
        quad(pose, buffer, light, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1 - u1z, vTop, 1 - u0z, vBottom, 1, 0, 0);
    }

    private static float v(float y, float textureHeight)
    {
        return Mth.clamp(1 - y / textureHeight, 0, 1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int nx, int ny, int nz)
    {
        vertex(pose, buffer, light, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(pose, buffer, light, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(pose, buffer, light, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(pose, buffer, light, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Each band is a ring hugging the crystal, ley teal where it enters at the foot and azurium as it nears the neck, fading in and out at both ends.
     */
    private static void bands(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        ring(pose, buffer, BAND_BOTTOM, BAND_BOTTOM + FOOT_HEIGHT, SHAFT_HALF + BAND_OFFSET, alpha(FOOT_ALPHA * state.fade, LEY_TEAL));

        for (var head : state.bandHeads)
        {
            var y0 = Mth.lerp(head, BAND_BOTTOM, BAND_TOP - BAND_HEIGHT);
            var y1 = y0 + BAND_HEIGHT;
            var edge = Math.min(Mth.clamp(head / BAND_EDGE, 0, 1), Mth.clamp((1 - head) / BAND_EDGE, 0, 1));
            var color = head < 0.5F
                    ? ARGB.srgbLerp(head * 2, LEY_TEAL, AZURIUM_MID)
                    : ARGB.srgbLerp((head - 0.5F) * 2, AZURIUM_MID, AZURIUM_LIGHT);
            var half = (y1 > SHAFT_TOP ? NECK_HALF : SHAFT_HALF);
            ring(pose, buffer, y0, y1, half + BAND_OFFSET, alpha(BAND_ALPHA * edge * state.fade, color));
            ring(pose, buffer, y0 - 0.5F * PIXEL, y1 + 0.5F * PIXEL, half + HALO_OFFSET, alpha(HALO_ALPHA * edge * state.fade, color));
        }
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float y0, float y1, float half, int color)
    {
        glowQuad(pose, buffer, color, -half, y0, -half, half, y1, -half);
        glowQuad(pose, buffer, color, half, y0, -half, half, y1, half);
        glowQuad(pose, buffer, color, half, y0, half, -half, y1, half);
        glowQuad(pose, buffer, color, -half, y0, half, -half, y1, -half);
    }

    private static void glowQuad(PoseStack.Pose pose, VertexConsumer buffer, int color, float x0, float y0, float z0, float x1, float y1, float z1)
    {
        buffer.addVertex(pose, x0, y0, z0).setColor(color);
        buffer.addVertex(pose, x1, y0, z1).setColor(color);
        buffer.addVertex(pose, x1, y1, z1).setColor(color);
        buffer.addVertex(pose, x0, y1, z0).setColor(color);
    }

    private static int alpha(float alpha, int color)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), color);
    }

    /**
     * A jagged leaping arc in the pylons' style: an azurium-light core in an azurium-mid halo, with brighter stretches running away from the spike.
     */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var start = new Vector3f(state.arcStart);
        var end = new Vector3f((float) state.arcEnd.x, (float) state.arcEnd.y, (float) state.arcEnd.z);
        var axis = new Vector3f(end).sub(start);
        var length = axis.length();
        if (length < 0.5F)
            return;
        axis.div(length);

        // Two directions across the arc to offset its joints and cross its planes
        var side = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        var up = new Vector3f(side).cross(axis).normalize();

        var joints = Math.max(MIN_JOINTS, Mth.ceil(length / JOINT_SPACING));
        var random = RandomSource.create(state.jagSeed);
        var points = new Vector3f[joints + 1];
        for (int i = 0; i <= joints; i++)
        {
            var point = new Vector3f(start).lerp(end, i / (float) joints);
            if (i > 0 && i < joints)
            {
                point.add(new Vector3f(side).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
                point.add(new Vector3f(up).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
            }
            points[i] = point;
        }

        var fade = state.fade;
        var pieces = joints * SUBDIVISIONS;
        for (int piece = 0; piece < pieces; piece++)
        {
            var joint = piece / SUBDIVISIONS;
            var from = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS) / (float) SUBDIVISIONS);
            var to = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS + 1) / (float) SUBDIVISIONS);

            var along = (piece + 0.5F) / pieces * length;
            var leap = 0F;
            for (var head : state.leapHeads)
            {
                var center = head * (length + LEAP_LENGTH) - LEAP_LENGTH / 2;
                leap = Math.max(leap, 1 - Math.abs(along - center) / (LEAP_LENGTH / 2));
            }

            var widen = 1 + (LEAP_WIDEN - 1) * leap;
            ribbon(pose, buffer, from, to, side, up, HALO_WIDTH * widen, alpha(ARC_HALO_ALPHA * fade * (1 + leap), AZURIUM_MID));
            ribbon(pose, buffer, from, to, side, up, CORE_WIDTH * widen, alpha(Math.min(1, ARC_CORE_ALPHA * (1 + leap)) * fade,
                    ARGB.srgbLerp(0.6F * leap, AZURIUM_LIGHT, 0xFFFFFF)));
        }
    }

    /**
     * Two crossed planes along a piece of the arc, so it reads from every side but straight down its length.
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f from, Vector3f to, Vector3f side, Vector3f up, float width, int color)
    {
        for (var across : new Vector3f[]{side, up})
        {
            var offset = new Vector3f(across).mul(width);
            buffer.addVertex(pose, from.x - offset.x, from.y - offset.y, from.z - offset.z).setColor(color);
            buffer.addVertex(pose, from.x + offset.x, from.y + offset.y, from.z + offset.z).setColor(color);
            buffer.addVertex(pose, to.x + offset.x, to.y + offset.y, to.z + offset.z).setColor(color);
            buffer.addVertex(pose, to.x - offset.x, to.y - offset.y, to.z - offset.z).setColor(color);
        }
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
