package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.fissure.FissureAnimation;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBlock;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureTile;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.energy.fissure.FissureLook.*;

/**
 * A Void Fissure or Void Wound lying on the ground: the crack, and a fissure's leaping arc to what takes its energy.
 * <p>
 * Render types: the crack's black depths and a wound's dull rim are blended ({@link FissureRenderTypes#DARK}), the violet light inside a fissure and its
 * lavender edges are added over them ({@link FissureRenderTypes#LIGHT}); both are decals without depth writes, so they never fight the ground or each
 * other. The arc is additive {@code GLOW}. Per crack and frame: about 13 depth quads, 26 light quads and 52 edge quads, plus up to about 200 arc quads,
 * rebuilt from the animation each frame, nothing cached.
 */
public class VoidFissureRender implements BlockEntityRenderer<VoidFissureTile, VoidFissureRender.State>
{
    private static final float DEPTH_Y   = 0.02F;
    private static final float LIGHT_Y   = 0.024F;
    private static final float EDGE_Y    = 0.028F;

    private static final float DEPTH_ALPHA       = 0.94F;
    private static final float INNER_ALPHA       = 0.8F;
    // How much of the gap between the centre line and an edge the violet fills
    private static final float INNER_REACH       = 0.45F;
    private static final float INNER_FLICKER     = 0.18F;
    private static final float EDGE_ALPHA        = 0.45F;
    private static final float EDGE_INSIDE       = 0.02F;
    private static final float EDGE_OUTSIDE      = 0.05F;
    private static final float TEAR_FLARE        = 0.8F;
    // While closing, the edges brighten toward Contravoid lavender, and the closed seam glows behind the front
    private static final float CLOSE_GLOW        = 1.2F;
    private static final float SEAM_HALF         = 0.035F;
    private static final float SEAM_ALPHA        = 0.8F;

    private static final float WOUND_RIM_OUTSIDE = 0.16F;
    private static final float WOUND_RIM_ALPHA   = 0.75F;
    private static final float TWITCH_SHIFT      = 0.07F;

    // Arc: from the crack to the bundle receiving the energy
    private static final float ARC_START_Y    = 0.15F;
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
        public boolean axisX;
        public boolean wound;
        public boolean confined;
        public long    seed;
        public float   length;
        public float   half;
        public float   closed;
        public float   flare;
        public float   time;
        public long    twitch;
        public final float[] jag = new float[FissureAnimation.JOINTS * 2];
        @Nullable
        public Vec3    arcEnd;
        public long    arcSeed;
        public float   arcFade;
        public final List<Float> leapHeads = new ArrayList<>();
    }

    public VoidFissureRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VoidFissureTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var blockState = tile.getBlockState();
        state.axisX = !blockState.hasProperty(VoidFissureBlock.AXIS) || blockState.getValue(VoidFissureBlock.AXIS) == Direction.Axis.X;
        state.wound = tile.isWound();
        state.confined = state.wound && tile.isConfined();
        state.seed = tile.seed();

        var animation = tile.animation();
        long now = tile.getLevel() == null ? 0 : tile.getLevel().getGameTime();
        float time = now + partialTicks;
        state.time = time;

        float tear = Mth.clamp((time - tile.tornAt()) / 20F, 0, 1);
        if (tile.tornAt() == Long.MIN_VALUE)
            tear = 1;
        tear = tear * tear * (3 - 2 * tear);
        if (state.wound)
        {
            state.length = CrackShape.woundLength(state.confined) * tear;
            state.half = CrackShape.woundHalf(state.confined) * tear;
        }
        else
        {
            float width = tile.width();
            float from = tear < 1 ? width - 1 : width;
            state.length = Mth.lerp(tear, CrackShape.fissureLength(from), CrackShape.fissureLength(width));
            state.half = Mth.lerp(tear, CrackShape.fissureHalf(from), CrackShape.fissureHalf(width));
        }
        state.flare = 1 - tear;
        state.closed = tile.isClosing() ? Mth.clamp((time - tile.closingSince()) / (float) VoidFissureBalance.CLOSE_TICKS, 0, 1) : 0;
        state.twitch = animation.twitch();

        if (animation.isSeeded())
        {
            for (int joint = 0; joint < FissureAnimation.JOINTS; joint++)
            {
                state.jag[joint * 2] = animation.jag(joint, 0, partialTicks);
                state.jag[joint * 2 + 1] = animation.jag(joint, 1, partialTicks);
            }
        }

        var target = state.wound ? null : animation.displayedTarget();
        state.arcEnd = target == null ? null : Vec3.atCenterOf(target).subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
        state.arcSeed = animation.arcSeed();
        state.arcFade = tile.outputTarget() != null ? 1 : 0.5F;
        state.leapHeads.clear();
        if (state.arcEnd != null)
        {
            for (var leap : animation.leaps())
                state.leapHeads.add(Mth.clamp((time - leap.start()) / leap.duration(), 0, 1));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.length <= 0.01F)
            return;

        var shape = new CrackShape();
        shape.build(state.seed, state.length, state.half, state.wound, (joint, edge) -> state.jag[joint * 2 + edge], state.closed);

        collector.submitCustomGeometry(poseStack, FissureRenderTypes.DARK, (pose, buffer) ->
        {
            depths(pose, buffer, state, shape);
            if (state.wound)
                woundRim(pose, buffer, state, shape);
        });
        if (!state.wound || state.closed > 0)
            collector.submitCustomGeometry(poseStack, FissureRenderTypes.LIGHT, (pose, buffer) -> light(pose, buffer, state, shape));
        if (state.arcEnd != null && state.closed <= 0)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> arc(pose, buffer, state));
    }

    private static void point(State state, float along, float across, float y, Vector3f out)
    {
        if (state.confined)
        {
            // Inside a shell nothing may reach past the block, or it would show through the walls
            along = Mth.clamp(along, -CrackShape.CONFINED_REACH, CrackShape.CONFINED_REACH);
            across = Mth.clamp(across, -CrackShape.CONFINED_REACH, CrackShape.CONFINED_REACH);
        }
        if (state.axisX)
            out.set(0.5F + along, y, 0.5F + across);
        else
            out.set(0.5F + across, y, 0.5F + along);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, State state, float y,
                             float alongA, float acrossA, int colorA, float alongB, float acrossB, int colorB,
                             float alongC, float acrossC, int colorC, float alongD, float acrossD, int colorD)
    {
        var at = new Vector3f();
        point(state, alongA, acrossA, y, at);
        vertex(buffer, pose, at, colorA);
        point(state, alongB, acrossB, y, at);
        vertex(buffer, pose, at, colorB);
        point(state, alongC, acrossC, y, at);
        vertex(buffer, pose, at, colorC);
        point(state, alongD, acrossD, y, at);
        vertex(buffer, pose, at, colorD);
    }

    /**
     * The black of the void between the two edges.
     */
    private static void depths(PoseStack.Pose pose, VertexConsumer buffer, State state, CrackShape shape)
    {
        int black = color(0x000000, DEPTH_ALPHA);
        for (int i = 0; i < CrackShape.JOINTS - 1; i++)
        {
            if (shape.open[i] <= 0 && shape.open[i + 1] <= 0)
                continue;
            quad(pose, buffer, state, DEPTH_Y,
                    shape.along[i], shape.left[i], black, shape.along[i + 1], shape.left[i + 1], black,
                    shape.along[i + 1], shape.right[i + 1], black, shape.along[i], shape.right[i], black);
        }
    }

    /**
     * Violet light welling up along the middle of a fissure, and Contravoid lavender along its edges. While it closes, the edges brighten, and the seam
     * left behind the closing fronts glows lavender and fades.
     */
    private static void light(PoseStack.Pose pose, VertexConsumer buffer, State state, CrackShape shape)
    {
        float closing = state.closed;
        float fade = 1 - closing * closing;
        float flare = 1 + TEAR_FLARE * state.flare;

        for (int i = 0; i < CrackShape.JOINTS - 1; i++)
        {
            float a0 = shape.along[i], a1 = shape.along[i + 1];
            float open0 = shape.open[i], open1 = shape.open[i + 1];

            if (!state.wound && (open0 > 0 || open1 > 0))
            {
                float flicker0 = 1 - INNER_FLICKER * (0.5F + 0.5F * Mth.sin(state.time * 0.13F + i * 1.7F));
                float flicker1 = 1 - INNER_FLICKER * (0.5F + 0.5F * Mth.sin(state.time * 0.13F + (i + 1) * 1.7F));
                int core0 = color(FISSURE_VIOLET, Math.min(1, INNER_ALPHA * flicker0 * flare * open0) * fade);
                int core1 = color(FISSURE_VIOLET, Math.min(1, INNER_ALPHA * flicker1 * flare * open1) * fade);
                int none = color(FISSURE_VIOLET, 0);
                float l0 = Mth.lerp(INNER_REACH, shape.center[i], shape.left[i]), l1 = Mth.lerp(INNER_REACH, shape.center[i + 1], shape.left[i + 1]);
                float r0 = Mth.lerp(INNER_REACH, shape.center[i], shape.right[i]), r1 = Mth.lerp(INNER_REACH, shape.center[i + 1], shape.right[i + 1]);
                quad(pose, buffer, state, LIGHT_Y, a0, shape.center[i], core0, a1, shape.center[i + 1], core1, a1, l1, none, a0, l0, none);
                quad(pose, buffer, state, LIGHT_Y, a0, shape.center[i], core0, a1, shape.center[i + 1], core1, a1, r1, none, a0, r0, none);
            }

            if (open0 > 0 || open1 > 0)
            {
                int rgb = CONTRAVOID_LAVENDER;
                float edge = (state.wound ? 0 : EDGE_ALPHA * flare) + CLOSE_GLOW * closing * (1 - closing);
                int on0 = color(rgb, Math.min(1, edge * open0));
                int on1 = color(rgb, Math.min(1, edge * open1));
                int off = color(rgb, 0);
                edgeBand(pose, buffer, state, a0, a1, shape.left[i], shape.left[i + 1], 1, on0, on1, off);
                edgeBand(pose, buffer, state, a0, a1, shape.right[i], shape.right[i + 1], -1, on0, on1, off);
            }

            if (closing > 0 && (open0 < 1 || open1 < 1))
            {
                float seam = SEAM_ALPHA * (1 - closing * 0.6F);
                int s0 = color(CONTRAVOID_LAVENDER, seam * (1 - open0));
                int s1 = color(CONTRAVOID_LAVENDER, seam * (1 - open1));
                int off = color(CONTRAVOID_LAVENDER, 0);
                float c0 = shape.center[i], c1 = shape.center[i + 1];
                quad(pose, buffer, state, EDGE_Y, a0, c0, s0, a1, c1, s1, a1, c1 + SEAM_HALF, off, a0, c0 + SEAM_HALF, off);
                quad(pose, buffer, state, EDGE_Y, a0, c0, s0, a1, c1, s1, a1, c1 - SEAM_HALF, off, a0, c0 - SEAM_HALF, off);
            }
        }
    }

    /**
     * A glowing band along one edge, brightest on the edge and fading a little inward and further outward onto the ground.
     *
     * @param outward 1 for the left edge, -1 for the right
     */
    private static void edgeBand(PoseStack.Pose pose, VertexConsumer buffer, State state, float a0, float a1, float e0, float e1, int outward,
                                 int on0, int on1, int off)
    {
        quad(pose, buffer, state, EDGE_Y, a0, e0, on0, a1, e1, on1, a1, e1 + outward * EDGE_OUTSIDE, off, a0, e0 + outward * EDGE_OUTSIDE, off);
        quad(pose, buffer, state, EDGE_Y, a0, e0, on0, a1, e1, on1, a1, e1 - outward * EDGE_INSIDE, off, a0, e0 - outward * EDGE_INSIDE, off);
    }

    /**
     * A wound's dull rim of bruise and ley mauve, with no light, twitching now and then.
     */
    private static void woundRim(PoseStack.Pose pose, VertexConsumer buffer, State state, CrackShape shape)
    {
        var twitch = state.twitch != 0 ? RandomSource.create(state.twitch) : null;
        float fade = 1 - state.closed;
        for (int i = 0; i < CrackShape.JOINTS - 1; i++)
        {
            if (shape.open[i] <= 0 && shape.open[i + 1] <= 0)
                continue;
            float shift0 = twitch == null ? 0 : twitch.nextFloat() * TWITCH_SHIFT;
            float shift1 = twitch == null ? 0 : twitch.nextFloat() * TWITCH_SHIFT;
            float mauve0 = 0.35F + 0.4F * FissureLook.hash(state.seed, i, 7) + (twitch == null ? 0 : 0.3F);
            float mauve1 = 0.35F + 0.4F * FissureLook.hash(state.seed, i + 1, 7) + (twitch == null ? 0 : 0.3F);
            int on0 = color(lerp(BRUISE, LEY_MAUVE, mauve0), WOUND_RIM_ALPHA * shape.open[i] * fade);
            int on1 = color(lerp(BRUISE, LEY_MAUVE, mauve1), WOUND_RIM_ALPHA * shape.open[i + 1] * fade);
            int off = color(BRUISE, 0);
            float a0 = shape.along[i], a1 = shape.along[i + 1];
            quad(pose, buffer, state, EDGE_Y, a0, shape.left[i] + shift0, on0, a1, shape.left[i + 1] + shift1, on1,
                    a1, shape.left[i + 1] + shift1 + WOUND_RIM_OUTSIDE, off, a0, shape.left[i] + shift0 + WOUND_RIM_OUTSIDE, off);
            quad(pose, buffer, state, EDGE_Y, a0, shape.right[i] - shift0, on0, a1, shape.right[i + 1] - shift1, on1,
                    a1, shape.right[i + 1] - shift1 - WOUND_RIM_OUTSIDE, off, a0, shape.right[i] - shift0 - WOUND_RIM_OUTSIDE, off);
        }
    }

    private static int alpha(float alpha, int color)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), color);
    }

    /**
     * A jagged leaping arc in the pylons' style, from the crack to what takes its energy: an azurium-light core in an azurium-mid halo, with brighter
     * stretches running away from the fissure.
     */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var start = new Vector3f(0.5F, ARC_START_Y, 0.5F);
        var end = new Vector3f((float) state.arcEnd.x, (float) state.arcEnd.y, (float) state.arcEnd.z);
        var axis = new Vector3f(end).sub(start);
        var length = axis.length();
        if (length < 0.5F)
            return;
        axis.div(length);

        var side = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        var up = new Vector3f(side).cross(axis).normalize();

        var joints = Math.max(MIN_JOINTS, Mth.ceil(length / JOINT_SPACING));
        var random = RandomSource.create(state.arcSeed);
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

        var fade = state.arcFade;
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
     * The crack reaches past its block once widened, and the arc to a pylon's field away.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
