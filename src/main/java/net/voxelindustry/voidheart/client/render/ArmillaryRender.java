package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Three nested gimbal rings: the outer turns on the vertical, the middle on a pin through the outer, the inner on a pin through the middle.
 * Rings are chains of plain cuboid links, and write depth, so that the portal in their middle sits correctly between their near and far sides.
 */
public class ArmillaryRender implements BlockEntityRenderer<ArmillaryTile, ArmillaryRender.State>
{
    private static final Identifier OUTER_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/block/cut_ravenous_gold.png");
    private static final Identifier MIDDLE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/voidstone_polished.png");
    private static final Identifier INNER_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/block/cut_listless_lead.png");

    private static final float CENTER_HEIGHT  = 2.5F;
    private static final float RISE_FROM      = 1.15F;
    private static final float OUTER_TILT     = 0.24F;

    private static final float[] RADII        = { 1.42F, 1.08F, 0.78F };
    private static final float[] THICKNESS    = { 0.15F, 0.13F, 0.11F };
    private static final int[]   LINKS        = { 24, 20, 16 };
    private static final int     BAND_EVERY   = 3;
    private static final float   BAND_GROWTH  = 1.35F;
    private static final float   PIN_SIZE     = 0.075F;

    private static final float PORTAL_RADIUS   = 0.5F;
    private static final float PORTAL_OPEN_TICKS = 45;

    private static final int   INNER_TINT      = 0xFFE9E4FF;
    /** The inner ring gone cold: plain lead, lit by the world around it. */
    private static final int   INNER_COLD_TINT = 0xFFFFFFFF;
    private static final int   FULL_BRIGHT     = LightCoordsUtil.FULL_BRIGHT;
    private static final int   INNER_HALO_RGB  = 0xB9A6F2;
    private static final int   INNER_HALO_ALPHA = 46;
    private static final float INNER_HALO_GROWTH = 1.9F;

    private static final float FALTER_JITTER   = 0.16F;
    private static final float FALTER_SAG      = 0.09F;

    private static final int GHOST_COLOR    = ARGB.color(150, 255, 255, 255);
    private static final int CONFLICT_COLOR = ARGB.color(170, 255, 60, 60);

    private static final int VIEW_DISTANCE = 128;

    private record Hint(BlockPos offset, BlockState block, boolean conflict)
    {
    }

    public static class State extends BlockEntityRenderState
    {
        final float[] angles = new float[ArmillaryTile.RINGS];
        float   formation;
        float   falter;
        float   warmth;
        float   time;
        int     ringLight;
        boolean portal;
        float   portalOpened;
        int     portalRgb;
        Vec3    toCamera = Vec3.ZERO;
        final List<Hint> hints = new ArrayList<>();
        final ArmillaryDialRender.Dial dial = new ArmillaryDialRender.Dial();
    }

    public ArmillaryRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ArmillaryTile armillary, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(armillary, state, partialTicks, cameraPosition, breakProgress);

        var level = armillary.getLevel();
        var hub = ArmillaryLayout.hub(armillary.getBlockPos());
        state.time = ArmillaryPortalRender.animationTime(level.getGameTime(), partialTicks);
        state.formation = armillary.formation(partialTicks);
        state.falter = armillary.falter();
        state.warmth = armillary.warmth(partialTicks);
        state.ringLight = LightCoordsUtil.getLightCoords(level, hub);
        for (int ring = 0; ring < ArmillaryTile.RINGS; ring++)
            state.angles[ring] = armillary.ringAngle(ring, partialTicks);

        state.portal = armillary.isConnected() && armillary.isFormed();
        state.portalOpened = Mth.clamp((level.getGameTime() - armillary.getPortalOpenedAt() + partialTicks) / PORTAL_OPEN_TICKS, 0, 1);
        var daedalusRgb = armillary.portalDaedalusRgb();
        state.portalRgb = daedalusRgb != null ? daedalusRgb : ArmillaryPortalRender.themeColor(armillary.portalTheme());
        state.toCamera = cameraPosition.subtract(Vec3.atCenterOf(hub));

        ArmillaryDialRender.extract(armillary, state.dial, state.time, partialTicks, cameraPosition);

        state.hints.clear();
        if (armillary.isPreviewActive())
            collectHints(armillary, state.hints);
    }

    private static void collectHints(ArmillaryTile armillary, List<Hint> hints)
    {
        var level = armillary.getLevel();
        var origin = armillary.getBlockPos();
        for (var part : ArmillaryLayout.base(origin))
        {
            if (!part.isPresentIn(level))
                hints.add(new Hint(part.pos().subtract(origin), part.block().defaultBlockState(), !level.getBlockState(part.pos()).canBeReplaced()));
        }
        for (var blocker : ArmillaryLayout.blockers(level, origin))
            hints.add(new Hint(blocker.subtract(origin), level.getBlockState(blocker), true));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        submitHints(state, poseStack, collector);
        submitRings(poseStack, collector, state.formation, state.angles, state.falter, state.warmth, state.time, state.ringLight,
                state.portal ? state.portalOpened : 0, state.portalRgb, state.toCamera);
        if (state.formation > 0 && state.dial.alpha > 0)
            ArmillaryDialRender.submit(state.dial, poseStack, collector);
    }

    /**
     * The rings and the portal between them, over a core at the pose's origin. Research scenes draw them through here too.
     *
     * @param portalOpened how open the portal is, 0 for none
     * @param toCamera     from the middle of the rings to the camera
     */
    public static void submitRings(PoseStack poseStack, SubmitNodeCollector collector, float formation, float[] angles, float falter, float warmth, float time,
                                   int ringLight, float portalOpened, int portalRgb, Vec3 toCamera)
    {
        if (formation <= 0)
            return;

        float eased = formation * formation * (3 - 2 * formation);
        float sag = falter * falter * FALTER_SAG * (1 + Mth.sin(time * 0.13F));
        float height = Mth.lerp(eased, RISE_FROM, CENTER_HEIGHT) - sag;

        poseStack.pushPose();
        poseStack.translate(0.5F, height, 0.5F);
        poseStack.scale(eased, eased, eased);

        poseStack.rotate(new Quaternionf().rotationY(angle(angles, falter, time, 0)).rotateZ(OUTER_TILT));
        ring(poseStack, collector, RenderTypes.entitySolid(OUTER_TEXTURE), 0, -1, ringLight);
        pins(poseStack, collector, RenderTypes.entitySolid(OUTER_TEXTURE), RADII[1] + THICKNESS[1] / 2, RADII[0] - THICKNESS[0] / 2, Direction.Axis.X, ringLight);

        poseStack.rotate(new Quaternionf().rotationX(angle(angles, falter, time, 1)));
        ring(poseStack, collector, RenderTypes.entitySolid(MIDDLE_TEXTURE), 1, -1, ringLight);
        pins(poseStack, collector, RenderTypes.entitySolid(MIDDLE_TEXTURE), RADII[2] + THICKNESS[2] / 2, RADII[1] - THICKNESS[1] / 2, Direction.Axis.Y, ringLight);

        poseStack.rotate(new Quaternionf().rotationY(angle(angles, falter, time, 2)));
        ring(poseStack, collector, RenderTypes.entitySolid(INNER_TEXTURE), 2, lerpColor(warmth, INNER_COLD_TINT, INNER_TINT),
                lerpLight(warmth, ringLight, FULL_BRIGHT));
        int haloAlpha = Math.round(INNER_HALO_ALPHA * warmth);
        if (haloAlpha > 0)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> innerHalo(pose, buffer, haloAlpha));
        poseStack.popPose();

        if (portalOpened > 0)
            ArmillaryPortalRender.submit(poseStack, collector, new Vec3(0.5, CENTER_HEIGHT - sag, 0.5), toCamera, PORTAL_RADIUS, portalOpened, portalRgb, time);
    }

    /**
     * A ring's own angle, with a stutter that grows as a connection fails.
     */
    private static float angle(float[] angles, float falter, float time, int ring)
    {
        float wobble = Mth.sin(time * (0.31F + ring * 0.17F) + ring * 2) * Mth.sin(time * 0.047F + ring);
        return angles[ring] + wobble * falter * falter * FALTER_JITTER;
    }

    private static void ring(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int ring, int tint, int light)
    {
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) ->
        {
            int links = LINKS[ring];
            for (int link = 0; link < links; link++)
            {
                float half = THICKNESS[ring] / 2 * (link % BAND_EVERY == 0 ? BAND_GROWTH : 1);
                link(pose, buffer, link * Mth.TWO_PI / links, (link + 1) * Mth.TWO_PI / links, RADII[ring], half, tint, light);
            }
        });
    }

    /**
     * One link: a cuboid bent to follow the ring, in the plane z = 0.
     */
    private static void link(PoseStack.Pose pose, VertexConsumer buffer, float from, float to, float radius, float half, int tint, int light)
    {
        float in = radius - half;
        float out = radius + half;
        float[][] start = { { Mth.cos(from) * in, Mth.sin(from) * in }, { Mth.cos(from) * out, Mth.sin(from) * out } };
        float[][] end = { { Mth.cos(to) * in, Mth.sin(to) * in }, { Mth.cos(to) * out, Mth.sin(to) * out } };
        float along = (to - from) * radius;
        float vMax = Math.min(1, 2 * half / along);

        float mid = (from + to) / 2;
        float nx = Mth.cos(mid), ny = Mth.sin(mid);

        // Outer, inner, front and back faces, then the two ends which show where the bands are thicker
        quad(pose, buffer, end[1][0], end[1][1], half, end[1][0], end[1][1], -half, start[1][0], start[1][1], -half, start[1][0], start[1][1], half, 1, vMax, tint, light, nx, ny, 0);
        quad(pose, buffer, start[0][0], start[0][1], half, start[0][0], start[0][1], -half, end[0][0], end[0][1], -half, end[0][0], end[0][1], half, 1, vMax, tint, light, -nx, -ny, 0);
        quad(pose, buffer, start[0][0], start[0][1], half, end[0][0], end[0][1], half, end[1][0], end[1][1], half, start[1][0], start[1][1], half, 1, vMax, tint, light, 0, 0, 1);
        quad(pose, buffer, end[0][0], end[0][1], -half, start[0][0], start[0][1], -half, start[1][0], start[1][1], -half, end[1][0], end[1][1], -half, 1, vMax, tint, light, 0, 0, -1);
        quad(pose, buffer, start[1][0], start[1][1], half, start[1][0], start[1][1], -half, start[0][0], start[0][1], -half, start[0][0], start[0][1], half, vMax, vMax, tint, light, ny, -nx, 0);
        quad(pose, buffer, end[0][0], end[0][1], half, end[0][0], end[0][1], -half, end[1][0], end[1][1], -half, end[1][0], end[1][1], half, vMax, vMax, tint, light, -ny, nx, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float uMax, float vMax, int tint, int light, float nx, float ny, float nz)
    {
        vertex(pose, buffer, x0, y0, z0, 0, 0, tint, light, nx, ny, nz);
        vertex(pose, buffer, x1, y1, z1, 0, vMax, tint, light, nx, ny, nz);
        vertex(pose, buffer, x2, y2, z2, uMax, vMax, tint, light, nx, ny, nz);
        vertex(pose, buffer, x3, y3, z3, uMax, 0, tint, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int tint, int light, float nx, float ny, float nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * The two short axles joining a ring to the next one out.
     */
    private static void pins(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, float from, float to, Direction.Axis axis, int light)
    {
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) ->
        {
            for (int side = -1; side <= 1; side += 2)
            {
                float near = side * from;
                float far = side * to;
                float min = Math.min(near, far);
                float max = Math.max(near, far);
                if (axis == Direction.Axis.X)
                    box(pose, buffer, min, -PIN_SIZE, -PIN_SIZE, max, PIN_SIZE, PIN_SIZE, light);
                else
                    box(pose, buffer, -PIN_SIZE, min, -PIN_SIZE, PIN_SIZE, max, PIN_SIZE, light);
            }
        });
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int light)
    {
        quad(pose, buffer, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0.25F, 0.25F, -1, light, 0, 0, 1);
        quad(pose, buffer, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0.25F, 0.25F, -1, light, 0, 0, -1);
        quad(pose, buffer, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, 0.25F, 0.25F, -1, light, -1, 0, 0);
        quad(pose, buffer, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 0.25F, 0.25F, -1, light, 1, 0, 0);
        quad(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0.25F, 0.25F, -1, light, 0, 1, 0);
        quad(pose, buffer, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0.25F, 0.25F, -1, light, 0, -1, 0);
    }

    /**
     * Soft light around the inner ring, so it reads as glowing rather than merely pale.
     */
    private static void innerHalo(PoseStack.Pose pose, VertexConsumer buffer, int alpha)
    {
        int links = LINKS[2];
        float half = THICKNESS[2] / 2;
        int core = ARGB.color(alpha, INNER_HALO_RGB);
        int rim = ARGB.color(0, INNER_HALO_RGB);
        for (int link = 0; link < links; link++)
        {
            float from = link * Mth.TWO_PI / links;
            float to = (link + 1) * Mth.TWO_PI / links;
            for (int side = -1; side <= 1; side += 2)
            {
                float edge = RADII[2] + side * half;
                float far = RADII[2] + side * half * INNER_HALO_GROWTH * 2;
                buffer.addVertex(pose, Mth.cos(from) * edge, Mth.sin(from) * edge, 0).setColor(core);
                buffer.addVertex(pose, Mth.cos(from) * far, Mth.sin(from) * far, 0).setColor(rim);
                buffer.addVertex(pose, Mth.cos(to) * far, Mth.sin(to) * far, 0).setColor(rim);
                buffer.addVertex(pose, Mth.cos(to) * edge, Mth.sin(to) * edge, 0).setColor(core);
            }
        }
    }

    private static int lerpColor(float delta, int from, int to)
    {
        return ARGB.color(Mth.lerpInt(delta, ARGB.alpha(from), ARGB.alpha(to)), Mth.lerpInt(delta, ARGB.red(from), ARGB.red(to)),
                Mth.lerpInt(delta, ARGB.green(from), ARGB.green(to)), Mth.lerpInt(delta, ARGB.blue(from), ARGB.blue(to)));
    }

    /**
     * Blends packed light coordinates half by half, so a fade between world light and full bright has no visible steps.
     */
    private static int lerpLight(float delta, int from, int to)
    {
        int block = Mth.lerpInt(delta, from & 0xFFFF, to & 0xFFFF);
        int sky = Mth.lerpInt(delta, from >>> 16, to >>> 16);
        return block | sky << 16;
    }

    private static void submitHints(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.hints.isEmpty())
            return;

        var ghost = hintInstance(GHOST_COLOR);
        var conflict = hintInstance(CONFLICT_COLOR);
        for (var hint : state.hints)
        {
            var quads = ForgePreviewRender.quadsOf(hint.block());
            var instance = hint.conflict() ? conflict : ghost;

            poseStack.pushPose();
            poseStack.translate(hint.offset().getX(), hint.offset().getY(), hint.offset().getZ());
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
            {
                for (var quad : quads)
                    buffer.putBakedQuad(pose, quad, instance);
            });
            poseStack.popPose();
        }
    }

    private static QuadInstance hintInstance(int color)
    {
        var instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        return instance;
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_DISTANCE;
    }
}
