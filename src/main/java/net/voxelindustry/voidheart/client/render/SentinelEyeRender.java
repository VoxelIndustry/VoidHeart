package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.SentinelEyeRenderState;
import net.voxelindustry.voidheart.common.content.underway.security.SentinelEye;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What watches from a housing of copper and brass, and the cone it watches through, which is how a visitor knows where not to stand.
 * <p>
 * A sentinel eye shows an amber iris behind its glass, whose slit wanders with its gaze, and a faint red cone. A searchlight shows its lamp lit and a long pale beam, and nothing at all while its section has no power.
 * <p>
 * What lies on the glass is full bright cutout quads, a pixel to a sixteenth like the housing, each a hair in front of the last so no two share a plane. The cone and the halo are additive glow: nothing writes depth, so the cone's far side and near side add up the same from every angle instead of hiding each other. Each line of the cone's side stops at the first block in its way, as the watcher's sight does, so the cone never reaches into a room past a wall. About thirty quads each, rebuilt each frame.
 */
public class SentinelEyeRender<T extends SentinelEye> extends EntityRenderer<T, SentinelEyeRenderState>
{
    /**
     * How a watcher looks.
     *
     * @param watchColor the colour of its cone while it only watches
     * @param iris       whether an iris with a wandering slit lies on its glass, rather than a lit lamp
     * @param plateHalf  half the side of what lies on its glass
     */
    private record Look(int watchColor, int coneAlpha, int coneNoticeAlpha, int haloAlpha, float haloRadius, boolean iris, float plateHalf)
    {
    }

    private static final float PIXEL = 1 / 16F;
    // A little brighter than they were when the cone stopped short, since it now thins out over its whole reach
    private static final Look EYE = new Look(0xE03426, 44, 84, 56, 0.16F, true, 3 * PIXEL);
    private static final Look SEARCHLIGHT = new Look(0xFFE2A8, 36, 68, 110, 0.34F, false, 3 * PIXEL);

    private static final RenderType IRIS_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/eye_iris.png"));
    private static final RenderType SLIT_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/eye_slit.png"));
    private static final RenderType LAMP_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/lens_warm.png"));

    // Amber as it makes someone out, near white once it has
    private static final int NOTICE_COLOR = 0xFFB030;
    private static final int ALARM_COLOR = 0xFFE8C0;
    private static final float SLIT_HALF_WIDTH = PIXEL;
    private static final float SLIT_HALF_HEIGHT = 2 * PIXEL;
    /** How far the slit wanders to each side of the middle of the iris. */
    private static final float SLIT_REACH = PIXEL;
    private static final float SLIT_FORWARD = 0.003F;
    private static final float HALO_FORWARD = 0.02F;
    private static final int CONE_SEGMENTS = SentinelEyeRenderState.CONE_LINES;
    private static final int DISC_SEGMENTS = 12;
    private static final float ALARM_FLASH_RATE = 0.9F;

    private final Look look;

    public SentinelEyeRender(EntityRendererProvider.Context context, boolean searchlight)
    {
        super(context);
        look = searchlight ? SEARCHLIGHT : EYE;
    }

    @Override
    public SentinelEyeRenderState createRenderState()
    {
        return new SentinelEyeRenderState();
    }

    @Override
    public void extractRenderState(T eye, SentinelEyeRenderState state, float partialTicks)
    {
        super.extractRenderState(eye, state, partialTicks);
        var facing = eye.facing();
        // An eye watches on the emergency circuit, a lamp only burns on power
        state.lit = look.iris || eye.isAwake();
        state.lens = eye.lens().subtract(eye.position());
        state.gaze = eye.gaze(partialTicks);
        state.facingYaw = (float) Mth.atan2(facing.x, facing.z);
        state.notice = Mth.clamp(eye.notice() / (float) eye.noticeTicks(), 0, 1);
        state.noticed = eye.hasNoticed();
        // The cone reaches exactly as far as the watcher sees, so nobody is made out from outside it
        state.coneLength = (float) eye.range();
        state.coneSpread = (float) Math.tan(Math.toRadians(eye.cone()));
        if (state.lit)
            clipCone(eye, state);
    }

    /** Casts each line of the cone's side from the lens, as far as the first block in its way. */
    private static void clipCone(SentinelEye eye, SentinelEyeRenderState state)
    {
        var gaze = state.gaze;
        var lens = eye.lens();
        var side = side(gaze);
        var up = side.cross(gaze);
        float radius = state.coneLength * state.coneSpread;
        var middle = lens.add(gaze.scale(state.coneLength));
        for (int segment = 0; segment < CONE_SEGMENTS; segment++)
        {
            float angle = segment * Mth.TWO_PI / CONE_SEGMENTS;
            var rim = middle.add(side.scale(Mth.cos(angle) * radius)).add(up.scale(Mth.sin(angle) * radius));
            var hit = eye.level().clip(new ClipContext(lens, rim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, eye));
            state.coneReach[segment] = hit.getType() == HitResult.Type.MISS ? 1 : (float) (hit.getLocation().distanceTo(lens) / rim.distanceTo(lens));
        }
    }

    /** @return a direction square to the gaze */
    private static Vec3 side(Vec3 gaze)
    {
        return Math.abs(gaze.y) > 0.99 ? new Vec3(1, 0, 0) : gaze.cross(new Vec3(0, 1, 0)).normalize();
    }

    /** The cone reaches far past the watcher's own small box. */
    @Override
    protected AABB getBoundingBoxForCulling(T eye, float partialTicks)
    {
        return eye.getBoundingBox().inflate(eye.range());
    }

    @Override
    public void submit(SentinelEyeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.lit)
        {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        float flash = state.noticed ? 0.5F + 0.5F * Mth.sin(state.ageInTicks * ALARM_FLASH_RATE) : 0;
        int hue = ARGB.srgbLerp(flash, ARGB.srgbLerp(state.notice, look.watchColor, NOTICE_COLOR), ALARM_COLOR);
        int coneColor = ARGB.color(Math.round(Mth.lerp(state.notice, look.coneAlpha, look.coneNoticeAlpha)), hue);
        int haloColor = ARGB.color(look.haloAlpha, hue);
        var gaze = state.gaze;
        float length = state.coneLength;
        float radius = length * state.coneSpread;
        // The state is filled again for the next frame, perhaps before this one's geometry is built
        float[] reach = state.coneReach.clone();

        poseStack.pushPose();
        poseStack.translate(state.lens.x, state.lens.y, state.lens.z);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> cone(pose, buffer, gaze, length, radius, reach, coneColor));

        // What lies on the glass looks the way the housing faces, and only an eye's slit follows the gaze
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotation(state.facingYaw));
        float half = look.plateHalf;
        if (look.iris)
        {
            float aside = (float) (gaze.x * Mth.cos(state.facingYaw) - gaze.z * Mth.sin(state.facingYaw));
            float slit = Mth.clamp(aside * SLIT_REACH * 1.25F, -SLIT_REACH, SLIT_REACH);
            collector.submitCustomGeometry(poseStack, IRIS_TYPE, (pose, buffer) -> plate(pose, buffer, -half, -half, half, half, 0));
            collector.submitCustomGeometry(poseStack, SLIT_TYPE, (pose, buffer) -> plate(pose, buffer, slit - SLIT_HALF_WIDTH, -SLIT_HALF_HEIGHT, slit + SLIT_HALF_WIDTH, SLIT_HALF_HEIGHT, SLIT_FORWARD));
        }
        else
            collector.submitCustomGeometry(poseStack, LAMP_TYPE, (pose, buffer) -> plate(pose, buffer, -half, -half, half, half, 0));
        poseStack.popPose();

        float haloRadius = look.haloRadius;
        poseStack.translate(gaze.x * HALO_FORWARD, gaze.y * HALO_FORWARD, gaze.z * HALO_FORWARD);
        poseStack.rotate(camera.orientation);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> disc(pose, buffer, haloRadius, haloColor));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** A flat piece on the glass facing +z, corners counter-clockwise seen from in front, showing its texture a pixel to a sixteenth from the corner. */
    private static void plate(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z)
    {
        float u = x1 - x0;
        float v = y1 - y0;
        vertex(pose, buffer, x0, y1, z, 0, 0);
        vertex(pose, buffer, x0, y0, z, 0, v);
        vertex(pose, buffer, x1, y0, z, u, v);
        vertex(pose, buffer, x1, y1, z, u, 0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }

    /**
     * The side of a cone from the lens along the gaze, full colour at the lens and gone at its far rim.
     *
     * @param reach how far each line of the side gets before a block stops it, as a share of its whole length
     */
    private static void cone(PoseStack.Pose pose, VertexConsumer buffer, Vec3 gaze, float length, float radius, float[] reach, int color)
    {
        // Two directions square to the gaze and to each other
        var side = side(gaze);
        var up = side.cross(gaze);
        for (int segment = 0; segment < CONE_SEGMENTS; segment++)
        {
            int next = (segment + 1) % CONE_SEGMENTS;
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            rim(pose, buffer, gaze, side, up, length, radius, next, reach[next], color);
            rim(pose, buffer, gaze, side, up, length, radius, segment, reach[segment], color);
        }
    }

    /** Where a line of the side stops, as faded as the whole cone is that far from the lens. */
    private static void rim(PoseStack.Pose pose, VertexConsumer buffer, Vec3 gaze, Vec3 side, Vec3 up, float length, float radius, int segment, float reach, int color)
    {
        float angle = segment * Mth.TWO_PI / CONE_SEGMENTS;
        float along = length * reach;
        float c = Mth.cos(angle) * radius * reach;
        float s = Mth.sin(angle) * radius * reach;
        buffer.addVertex(pose, (float) (gaze.x * along + side.x * c + up.x * s), (float) (gaze.y * along + side.y * c + up.y * s), (float) (gaze.z * along + side.z * c + up.z * s))
                .setColor(ARGB.color(Math.round(ARGB.alpha(color) * (1 - reach)), color));
    }

    /** A soft disc facing the camera: the light the watcher gives off. */
    private static void disc(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color)
    {
        int clear = ARGB.color(0, color);
        for (int segment = 0; segment < DISC_SEGMENTS; segment++)
        {
            float a0 = segment * Mth.TWO_PI / DISC_SEGMENTS;
            float a1 = (segment + 1) * Mth.TWO_PI / DISC_SEGMENTS;
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            buffer.addVertex(pose, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0).setColor(clear);
            buffer.addVertex(pose, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0).setColor(clear);
        }
    }
}
