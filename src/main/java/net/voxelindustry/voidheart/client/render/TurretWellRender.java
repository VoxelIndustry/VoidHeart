package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.security.TurretWell;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The mortar of a turret well, built from boxes in the copper and brass of its well: a drum that rises out of the plate, a head that turns on it with an eye of amber glass, and a short fat barrel pointing up. Nothing is drawn while it is down, when the well's own cap stands for it.
 * <p>
 * While a shell flies, the circle it falls on shows on the ground: a steady amber rim as wide as the burst, and a second ring that closes on its middle as the shell comes down, so the time left reads at a glance. Both are additive glow lying flat, which writes no depth. The shell itself is a small dark box on its arc. About forty quads and two rings of sixteen.
 */
public class TurretWellRender extends EntityRenderer<TurretWell, TurretWellRender.State>
{
    public static class State extends EntityRenderState
    {
        public boolean awake;
        /** How far it has risen, from 0 to 1. */
        public float out;
        public float yaw;
        /** Where its shell falls and where the shell is, from its position, or null with none in the air. */
        public @Nullable Vec3 mark;
        public Vec3 shell = Vec3.ZERO;
        /** How far through its flight the shell is, from 0 to 1. */
        public float flown;
    }

    private static final RenderType COPPER_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/copper.png"));
    private static final RenderType COPPER_DARK_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/copper_dark.png"));
    private static final RenderType BRASS_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/brass.png"));
    private static final RenderType EYE_LIT_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/eye_small.png"));
    private static final RenderType EYE_DARK_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/glass_dark.png"));

    private static final float PIXEL = 1 / 16F;
    /** Keeps boxes that meet from sharing a plane. */
    private static final float GAP = 0.004F;
    // The drum stands on the ring of the well and rises ten pixels out of it
    private static final float WELL_TOP = 4 * PIXEL + GAP;
    private static final float DRUM_HALF = 3 * PIXEL;
    private static final float DRUM_RISE = 10 * PIXEL;
    private static final float HEAD_HALF = 2.5F * PIXEL;
    private static final float HEAD_HEIGHT = 3 * PIXEL;
    private static final float BARREL_HALF = PIXEL;
    private static final float BARREL_LENGTH = 7 * PIXEL;
    private static final float BARREL_ELEVATION = (float) Math.toRadians(55);
    private static final float EYE_HALF = PIXEL;
    private static final float SHELL_HALF = 1.5F * PIXEL;
    private static final float SCAN_RATE = 0.03F;
    // The amber of the Underway's machinery
    private static final int MARK_COLOR = 0xF0A030;
    private static final int RIM_ALPHA = 170;
    private static final int CLOSING_ALPHA = 120;
    private static final int FILL_ALPHA = 26;
    private static final float RIM_WIDTH = 0.14F;
    private static final float MARK_LIFT = 0.03F;
    private static final int RING_SEGMENTS = 16;

    public TurretWellRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(TurretWell well, State state, float partialTicks)
    {
        super.extractRenderState(well, state, partialTicks);
        var origin = well.position();
        state.awake = well.isAwake();
        state.out = Mth.lerp(partialTicks, well.outO, well.out);
        boolean flying = well.flight >= 0;
        var phase = well.phase();
        if (flying || phase == TurretWell.Phase.FIRING)
        {
            var way = well.mark().subtract(origin);
            state.yaw = (float) Mth.atan2(way.x, way.z);
        }
        else
            state.yaw = state.ageInTicks * SCAN_RATE;
        state.flown = flying ? Mth.clamp((well.flight + partialTicks) / SecurityBalance.WELL_FLIGHT_TICKS, 0, 1) : 0;
        state.mark = flying ? well.mark().subtract(origin) : null;
        state.shell = flying ? well.shellAt(state.flown).subtract(origin) : Vec3.ZERO;
    }

    /** Its circles show as far as it shoots. */
    @Override
    protected AABB getBoundingBoxForCulling(TurretWell well, float partialTicks)
    {
        return well.getBoundingBox().inflate(SecurityBalance.WELL_RANGE + 4);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.out > 0)
            submitMortar(state, poseStack, collector);
        if (state.mark != null)
            submitShell(state, poseStack, collector);
        super.submit(state, poseStack, collector, camera);
    }

    private static void submitMortar(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        int light = state.lightCoords;
        float top = WELL_TOP + state.out * DRUM_RISE;
        collector.submitCustomGeometry(poseStack, COPPER_DARK_TYPE, (pose, buffer) -> UnderwayKeyRender.box(pose, buffer, -DRUM_HALF, WELL_TOP, -DRUM_HALF, DRUM_HALF, top, DRUM_HALF, light));

        poseStack.pushPose();
        poseStack.translate(0, top + GAP, 0);
        poseStack.rotate(Axis.YP.rotation(state.yaw));
        collector.submitCustomGeometry(poseStack, COPPER_TYPE, (pose, buffer) -> UnderwayKeyRender.box(pose, buffer, -HEAD_HALF, 0, -HEAD_HALF, HEAD_HALF, HEAD_HEIGHT, HEAD_HALF, light));
        collector.submitCustomGeometry(poseStack, state.awake ? EYE_LIT_TYPE : EYE_DARK_TYPE, (pose, buffer) ->
                UnderwayKeyRender.box(pose, buffer, -EYE_HALF, PIXEL / 2, HEAD_HALF + GAP, EYE_HALF, PIXEL / 2 + 2 * EYE_HALF, HEAD_HALF + PIXEL / 2, state.awake ? LightCoordsUtil.FULL_BRIGHT : light));
        // The barrel leaves the top of the head, tilted up toward where it throws
        poseStack.translate(0, HEAD_HEIGHT + GAP, 0);
        poseStack.rotate(Axis.XP.rotation(-BARREL_ELEVATION));
        collector.submitCustomGeometry(poseStack, BRASS_TYPE, (pose, buffer) -> UnderwayKeyRender.box(pose, buffer, -BARREL_HALF, 0, -BARREL_HALF, BARREL_HALF, 2 * BARREL_HALF, BARREL_LENGTH, light));
        poseStack.popPose();
    }

    private static void submitShell(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var shell = state.shell;
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.translate(shell.x, shell.y, shell.z);
        poseStack.rotate(Axis.YP.rotation(state.ageInTicks * 0.6F));
        collector.submitCustomGeometry(poseStack, COPPER_DARK_TYPE, (pose, buffer) -> UnderwayKeyRender.box(pose, buffer, -SHELL_HALF, -SHELL_HALF, -SHELL_HALF, SHELL_HALF, SHELL_HALF, SHELL_HALF, light));
        poseStack.popPose();

        var mark = state.mark;
        float radius = (float) SecurityBalance.WELL_BLAST_RADIUS;
        float closing = radius * (1 - state.flown);
        int rim = ARGB.color(RIM_ALPHA, MARK_COLOR);
        int inner = ARGB.color(CLOSING_ALPHA, MARK_COLOR);
        int fill = ARGB.color(Math.round(FILL_ALPHA * (1 + 2 * state.flown)), MARK_COLOR);
        poseStack.pushPose();
        poseStack.translate(mark.x, mark.y + MARK_LIFT, mark.z);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            ring(pose, buffer, 0, radius, fill, fill);
            ring(pose, buffer, radius - RIM_WIDTH, radius, rim, rim);
            if (closing > RIM_WIDTH)
                ring(pose, buffer, closing - RIM_WIDTH, closing, ARGB.color(0, inner), inner);
        });
        poseStack.popPose();
    }

    /** A flat ring on the ground, from one radius to another, each with its own colour. */
    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float from, float to, int fromColor, int toColor)
    {
        for (int segment = 0; segment < RING_SEGMENTS; segment++)
        {
            float a0 = segment * Mth.TWO_PI / RING_SEGMENTS;
            float a1 = (segment + 1) * Mth.TWO_PI / RING_SEGMENTS;
            buffer.addVertex(pose, Mth.cos(a0) * from, 0, Mth.sin(a0) * from).setColor(fromColor);
            buffer.addVertex(pose, Mth.cos(a1) * from, 0, Mth.sin(a1) * from).setColor(fromColor);
            buffer.addVertex(pose, Mth.cos(a1) * to, 0, Mth.sin(a1) * to).setColor(toColor);
            buffer.addVertex(pose, Mth.cos(a0) * to, 0, Mth.sin(a0) * to).setColor(toColor);
        }
    }
}
