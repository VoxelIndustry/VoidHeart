package net.voxelindustry.voidheart.client.render.energy.reactor;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorTile;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorEssence;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Part;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A Contravoid reactor, after energy-looks.md: two streams meeting head-on in a burning point, seen only through the slits of the containment; the
 * leaping arcs to the pylons it feeds; the warning over a mistake's countdown; and the ghosts of the shell still to build.
 * <p>
 * Render types: a solid dark backdrop facing the camera behind the point, so the chamber reads closed; the point, streams, slit light, cracks, breach light, sparks and arcs are additive {@code GLOW}, which writes no depth, so their
 * crossed planes overlap in any order without flicker. The band pulses are {@code MARKER}, blended rather than added, so their violet still reads over
 * bright gold. Cracks and pulses lie a few thousandths outside the shell's faces, never on them. Ghosts are the preview's translucent moving blocks.
 * <p>
 * Per reactor and frame, at worst: about 150 quads for the point and streams, 12 for band pulses, about 250 for cracks, 30 a breached block, and about
 * 100 for each of up to six arcs, rebuilt from the tile's animation each frame. Only ghost quads are cached, per block state.
 */
public class ContravoidReactorRender implements BlockEntityRenderer<ContravoidReactorTile, ContravoidReactorRender.State>
{
    // energy-looks.md colors: Contravoid lavender and the aspects from VoidBeamRender, fissure violet, azurium tones from the bundle textures
    private static final int CONTRAVOID     = 0xE9DDFF;
    private static final int STILLNESS      = 0x7FB8FF;
    private static final int FIERY          = 0xFF8A3D;
    private static final int SUFFERING      = 0xC0182F;
    private static final int POINT_CORE     = 0xF6F0FF;
    private static final int POINT_HALO     = 0xB49AFF;
    private static final int FISSURE_VIOLET = 0x7A3FD0;
    private static final int CRACK_VIOLET   = 0x9C62F5;
    private static final int BREACH_VIOLET  = 0xA674FF;
    private static final int AZURIUM_LIGHT  = 0x9EECFF;
    private static final int AZURIUM_MID    = 0x3D7ED9;
    private static final int SPARK          = 0xDCE6FF;

    private static final int BACKDROP_COLOR = 0xFF0B0710;

    private static final RenderType BACKDROP_TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/voidstone.png"));

    private static final int GHOST_COLOR    = ARGB.color(150, 255, 255, 255);
    private static final int CONFLICT_COLOR = ARGB.color(170, 255, 60, 60);

    private static final float PIXEL = 1 / 16F;

    // Point
    private static final float POINT_MIN       = 0.035F;
    private static final float POINT_GROWTH    = 0.05F;
    private static final float HALO_SCALE      = 4.5F;
    private static final float FLARE_RADIUS    = 0.16F;
    private static final float POINT_ALPHA     = 1F;
    private static final float HALO_ALPHA      = 0.45F;
    private static final float WOBBLE          = 0.13F;
    private static final float FLICKER         = 0.55F;
    private static final int   DISK_SEGMENTS   = 8;

    // Streams, from an intake's inner face to the point
    private static final int   STREAM_JOINTS   = 6;
    private static final float STREAM_CORE     = 0.022F;
    private static final float STREAM_HALO     = 0.06F;
    private static final float STREAM_CORE_A   = 0.9F;
    private static final float STREAM_HALO_A   = 0.32F;
    private static final float FRAY            = 0.045F;
    private static final float DRY_THIN        = 0.8F;
    private static final float SUFFERING_BOOST = 1.6F;

    // Slits, from the hand-written slit models
    private static final float SLIT_ALONG_0    = 2 * PIXEL;
    private static final float SLIT_ALONG_1    = 14 * PIXEL;
    private static final float SLIT_THIN_0     = 6.5F * PIXEL;
    private static final float SLIT_THIN_1     = 9.5F * PIXEL;
    private static final float SLIT_INSET      = 0.03F;
    private static final float SLIT_ALPHA      = 0.3F;
    private static final float BLOOM_ALPHA     = 0.18F;
    private static final float BLOOM_SPREAD    = 0.07F;

    // Warning
    private static final float SURFACE_OFFSET  = 0.006F;
    private static final float BAND_TOP       = 1;
    private static final float BAND_BOTTOM     = 14 * PIXEL;
    private static final float BAND_ALPHA      = 0.8F;
    private static final int   CRACKS_PER_FACE = 3;
    private static final int   CRACK_SEGMENTS  = 12;
    private static final float CRACK_STEP      = 0.2F;
    private static final float CRACK_TURN      = 0.65F;
    private static final float CRACK_WIDTH     = 0.03F;
    private static final float CRACK_ALPHA     = 0.85F;
    private static final float BREACH_RADIUS   = 0.85F;
    private static final float BREACH_ALPHA    = 0.6F;

    // The dark of the chamber behind the point, so opposite slits never show through to each other
    private static final float BACKDROP_BEHIND = 0.32F;
    private static final float BACKDROP_HALF   = 0.45F;

    // Lighting: sparks gathering at the core
    private static final int   SPARKS          = 8;
    private static final float SPARK_REACH     = 0.48F;
    private static final float SPARK_SIZE      = 0.018F;

    // Arcs, as the pylons' and the engine's
    private static final float CROWN_INSET     = 0.12F;
    private static final float JOINT_SPACING   = 1.5F;
    private static final int   MIN_JOINTS      = 6;
    private static final float JOINT_OFFSET    = 0.25F;
    private static final int   SUBDIVISIONS    = 4;
    private static final float ARC_CORE_WIDTH  = 0.035F;
    private static final float ARC_HALO_WIDTH  = 0.09F;
    private static final float ARC_CORE_ALPHA  = 0.75F;
    private static final float ARC_HALO_ALPHA  = 0.25F;
    private static final float LEAP_LENGTH     = 1.5F;
    private static final float LEAP_WIDEN      = 1.6F;

    public record Ghost(BlockPos offset, BlockState state, boolean conflict)
    {
    }

    public record ArcDraw(Vec3 end, long seed, List<Float> heads, boolean inward)
    {
    }

    public static class State extends BlockEntityRenderState
    {
        public Phase   phase = Phase.COLD;
        public Direction.Axis axis = Direction.Axis.X;
        public int     contravoidSide;
        public int     essenceColor;
        public boolean suffering;
        public float   time;
        public float   light;
        public float   streams;
        public boolean draining;
        public float   flare;
        public float   warning;
        public float   warnStrength;
        public float   dryContravoid;
        public float   dryEssence;
        public float   breach;
        public float   collapse;
        public float   activity;
        public float   charge;
        public float   pulse;
        public long    seed;
        public boolean backdrop;
        public final Vector3f toCamera = new Vector3f();
        public final List<Ghost>    ghosts  = new ArrayList<>();
        public final List<BlockPos> gaps    = new ArrayList<>();
        public final List<ArcDraw>  arcs    = new ArrayList<>();
    }

    public ContravoidReactorRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ContravoidReactorTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var animation = tile.animation();
        var level = tile.getLevel();
        state.phase = tile.phase();
        state.axis = tile.axisIn(level);
        state.contravoidSide = tile.contravoidSide() < 0 ? 0 : tile.contravoidSide();
        var essence = tile.burning();
        state.essenceColor = essence == null ? FIERY : colorOf(essence);
        state.suffering = essence == ReactorEssence.SUFFERING;
        state.time = animation.time(partialTicks);
        state.light = animation.light(partialTicks);
        state.streams = animation.streams(partialTicks);
        state.draining = animation.draining();
        state.flare = animation.flare(partialTicks);
        state.warning = animation.warning(partialTicks);
        state.warnStrength = animation.warnStrength(partialTicks);
        state.dryContravoid = animation.dryContravoid();
        state.dryEssence = animation.dryEssence();
        state.breach = animation.breach();
        state.collapse = animation.collapse(partialTicks);
        state.activity = animation.activity(partialTicks);
        state.charge = animation.charge(partialTicks);
        var pulse = Math.max(0, Mth.sin(animation.pulsePhase(partialTicks) * Mth.TWO_PI));
        state.pulse = pulse * pulse * pulse * state.warnStrength;
        state.seed = tile.getBlockPos().asLong();
        state.backdrop = tile.isComplete() || state.phase != Phase.COLD;
        var center = Vec3.atCenterOf(tile.getBlockPos());
        state.toCamera.set((float) (cameraPosition.x - center.x), (float) (cameraPosition.y - center.y), (float) (cameraPosition.z - center.z));
        if (state.toCamera.lengthSquared() < 1e-6F)
            state.toCamera.set(0, 1, 0);
        state.toCamera.normalize();

        // Ghosts while building, and the gap a breach left
        state.ghosts.clear();
        state.gaps.clear();
        var phase = state.phase;
        if (level != null && (phase == Phase.COLD || phase == Phase.FAILING || phase == Phase.IGNITING))
        {
            var origin = tile.getBlockPos();
            for (var expected : ReactorLayout.missing(state.axis, offset -> ContravoidReactorTile.partAt(level, origin.offset(offset))))
            {
                var actual = level.getBlockState(origin.offset(expected.offset()));
                var free = actual.isAir() || !actual.getFluidState().isEmpty() && actual.canBeReplaced();
                var ghost = expected.part() == Part.INTAKE ? ContravoidReactorContent.REACTOR_INTAKE.defaultBlockState()
                        : ContravoidReactorContent.REACTOR_CONTAINMENT.defaultBlockState();
                state.ghosts.add(new Ghost(expected.offset(), ghost, !free));
                if (phase == Phase.FAILING && free)
                    state.gaps.add(expected.offset());
            }
        }

        state.arcs.clear();
        var running = phase.isRunning();
        if (running || phase == Phase.IGNITING)
        {
            var origin = Vec3.atLowerCornerOf(tile.getBlockPos());
            for (var entry : animation.arcs().entrySet())
            {
                var end = Vec3.atCenterOf(entry.getKey()).subtract(origin);
                state.arcs.add(new ArcDraw(end, entry.getValue().seed(), entry.getValue().leapHeads(partialTicks), !running));
            }
        }
    }

    private static int colorOf(ReactorEssence essence)
    {
        return switch (essence)
        {
            case STILLNESS -> STILLNESS;
            case FIERY -> FIERY;
            case SUFFERING -> SUFFERING;
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        submitGhosts(state, poseStack, collector);
        if (state.backdrop)
            collector.submitCustomGeometry(poseStack, BACKDROP_TYPE, (pose, buffer) -> backdrop(pose, buffer, state));

        var glows = state.light > 0.001F || state.flare > 0.001F || state.phase == Phase.IGNITING || state.phase == Phase.COLLAPSING
                || state.warnStrength > 0.001F || !state.arcs.isEmpty();
        if (glows)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> glow(pose, buffer, state));
        if (state.pulse > 0.001F && state.collapse < 1)
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) -> bands(pose, buffer, state));
    }

    /**
     * A dark square facing the camera just behind the point, drawn solid so it hides whatever lies past the chamber. Two slits facing each other would
     * otherwise let the sky show straight through the reactor. The point and streams lie in front of it.
     */
    private static void backdrop(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var facing = state.toCamera;
        var right = new Vector3f(facing).cross(Math.abs(facing.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize().mul(BACKDROP_HALF);
        var up = new Vector3f(right).cross(facing).normalize().mul(BACKDROP_HALF);
        var center = new Vector3f(0.5F, 0.5F, 0.5F).sub(new Vector3f(facing).mul(BACKDROP_BEHIND));
        var corners = new Vector3f[]{
                new Vector3f(center).sub(right).sub(up), new Vector3f(center).add(right).sub(up),
                new Vector3f(center).add(right).add(up), new Vector3f(center).sub(right).add(up)};
        // Both windings, so it shows whichever way the pipeline culls
        for (var order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}})
        {
            for (var index : order)
                buffer.addVertex(pose, corners[index].x, corners[index].y, corners[index].z)
                        .setColor(BACKDROP_COLOR)
                        .setUv(index == 1 || index == 2 ? 1 : 0, index >= 2 ? 1 : 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, facing.x, facing.y, facing.z);
        }
    }

    //
    // Glow
    //

    private static void glow(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var random = RandomSource.create(state.seed ^ ((long) (state.time / 2) * 341873128712L));
        var fade = 1 - state.collapse;
        var center = pointCenter(state);

        if (state.phase == Phase.IGNITING)
            sparks(pose, buffer, state, center);

        // Flicker and point, which shrinks last as the light collapses into it
        var shaking = state.warning * state.warnStrength;
        var flicker = 1 - FLICKER * shaking * random.nextFloat();
        var lit = Math.max(state.light, state.phase == Phase.IGNITING ? 0.35F * state.charge : 0);
        var radius = (POINT_MIN + POINT_GROWTH * state.activity) * lit * (state.phase == Phase.COLLAPSING ? 1 + state.collapse * 0.6F : 1)
                + FLARE_RADIUS * easeOut(state.flare);
        if (state.collapse >= 1)
            radius = 0;
        if (radius > 0.001F)
        {
            var halo = radius * HALO_SCALE * fade + 0.0001F;
            disk3(pose, buffer, center, radius, halo, POINT_CORE, POINT_HALO, Math.min(1, POINT_ALPHA * flicker), HALO_ALPHA * flicker * (0.5F + 0.5F * lit));
        }

        streams(pose, buffer, state, center, random, fade);
        slits(pose, buffer, state, lit * flicker * fade + state.flare);

        if (state.warnStrength > 0.001F)
            cracks(pose, buffer, state, fade);
        for (var gap : state.gaps)
            breach(pose, buffer, gap, state.breach * fade * (0.75F + 0.25F * random.nextFloat()));

        for (var arc : state.arcs)
            arc(pose, buffer, state, arc, state.phase == Phase.IGNITING ? 1 : state.light * fade);
    }

    private static float easeOut(float value)
    {
        return 1 - (1 - value) * (1 - value);
    }

    /**
     * The meeting point, wobbling off center as the countdown runs.
     */
    private static Vector3f pointCenter(State state)
    {
        var amount = WOBBLE * state.warning * state.warnStrength * (1 - state.collapse);
        var t = state.time;
        return new Vector3f(0.5F + Mth.sin(t * 1.7F) * amount, 0.5F + Mth.sin(t * 2.3F + 1) * amount * 0.6F, 0.5F + Mth.cos(t * 1.3F) * amount);
    }

    private static Vector3f axisVector(Direction.Axis axis)
    {
        return axis == Direction.Axis.Z ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
    }

    /**
     * Contravoid from one intake and the essence from the other, fraying as they near the point.
     */
    private static void streams(PoseStack.Pose pose, VertexConsumer buffer, State state, Vector3f center, RandomSource random, float fade)
    {
        if (state.streams <= 0.001F)
            return;
        var axis = axisVector(state.axis);
        var up = new Vector3f(0, 1, 0);
        var side = new Vector3f(axis).cross(up).normalize();

        for (var intake = 0; intake < 2; intake++)
        {
            var contravoid = intake == state.contravoidSide;
            var dry = contravoid ? state.dryContravoid : state.dryEssence;
            var color = contravoid ? CONTRAVOID : state.essenceColor;
            var boost = !contravoid && state.suffering ? SUFFERING_BOOST : 1;
            var gutter = 1 - dry * (0.4F + 0.6F * random.nextFloat());
            var thin = 1 - DRY_THIN * dry;

            var start = new Vector3f(0.5F, 0.5F, 0.5F).add(new Vector3f(axis).mul(intake == 0 ? -0.5F : 0.5F));
            // Lighting grows the streams out of the intakes, a shutdown drains them into the point, a collapse pulls them in
            var from = state.draining ? 1 - state.streams : Math.max(0, state.collapse);
            var to = state.draining ? 1 : Math.min(1, state.streams);
            if (to - from <= 0.001F)
                continue;

            Vector3f previous = null;
            for (var joint = 0; joint <= STREAM_JOINTS; joint++)
            {
                var u = Mth.lerp(joint / (float) STREAM_JOINTS, from, to);
                var point = new Vector3f(start).lerp(center, u);
                var fray = FRAY * u * u;
                point.add(new Vector3f(up).mul((random.nextFloat() * 2 - 1) * fray)).add(new Vector3f(side).mul((random.nextFloat() * 2 - 1) * fray));
                if (previous != null)
                {
                    var widen = 1 + 0.6F * u;
                    var alpha = gutter * fade;
                    ribbon(pose, buffer, previous, point, up, side, STREAM_HALO * thin * widen, alpha(STREAM_HALO_A * boost * alpha, color));
                    ribbon(pose, buffer, previous, point, up, side, STREAM_CORE * thin * widen,
                            alpha(Math.min(1, STREAM_CORE_A * boost * alpha), ARGB.srgbLerp(0.35F, color, 0xFFFFFF)));
                }
                previous = point;
            }
        }
    }

    /**
     * Light let out through each slit: a pane just inside the opening and a faint bloom just outside it.
     */
    private static void slits(PoseStack.Pose pose, VertexConsumer buffer, State state, float strength)
    {
        if (strength <= 0.001F)
            return;
        var color = ARGB.srgbLerp(0.5F, POINT_HALO, POINT_CORE);
        for (var direction : Direction.values())
        {
            var offset = BlockPos.ZERO.relative(direction);
            var slit = ReactorLayout.slitAt(state.axis, offset);
            if (slit == Slit.NONE)
                continue;
            var outer = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 2F : -1F;
            var inward = -direction.getAxisDirection().getStep();
            slitPane(pose, buffer, slit, offset, outer + inward * SLIT_INSET, 0, alpha(SLIT_ALPHA * Math.min(1, strength), color));
            slitPane(pose, buffer, slit, offset, outer - inward * 0.004F, BLOOM_SPREAD, alpha(BLOOM_ALPHA * Math.min(1, strength), color));
        }
    }

    /**
     * Brightest at the middle of the slit, across from the point, and clear at its ends, so the streams and the point still show through it.
     */
    private static void slitPane(PoseStack.Pose pose, VertexConsumer buffer, Slit slit, BlockPos offset, float through, float spread, int color)
    {
        var along = slit.along();
        var thin = thinAxis(slit);
        float a0 = component(offset, along) + SLIT_ALONG_0 - spread, a1 = component(offset, along) + SLIT_ALONG_1 + spread;
        float n0 = component(offset, thin) + SLIT_THIN_0 - spread, n1 = component(offset, thin) + SLIT_THIN_1 + spread;
        var middle = (a0 + a1) / 2;
        var clear = ARGB.color(0, color);
        var fixed = slit.through();
        vertex(pose, buffer, fixed, along, thin, through, a0, n0, clear);
        vertex(pose, buffer, fixed, along, thin, through, middle, n0, color);
        vertex(pose, buffer, fixed, along, thin, through, middle, n1, color);
        vertex(pose, buffer, fixed, along, thin, through, a0, n1, clear);
        vertex(pose, buffer, fixed, along, thin, through, middle, n0, color);
        vertex(pose, buffer, fixed, along, thin, through, a1, n0, clear);
        vertex(pose, buffer, fixed, along, thin, through, a1, n1, clear);
        vertex(pose, buffer, fixed, along, thin, through, middle, n1, color);
    }

    private static Direction.Axis thinAxis(Slit slit)
    {
        for (var axis : Direction.Axis.values())
        {
            if (axis != slit.through() && axis != slit.along())
                return axis;
        }
        return Direction.Axis.Y;
    }

    private static float component(BlockPos pos, Direction.Axis axis)
    {
        return pos.get(axis);
    }

    /**
     * An axis-aligned quad at a fixed coordinate on one axis, spanning two others.
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Direction.Axis fixed, Direction.Axis first, Direction.Axis second, float at,
                             float f0, float s0, float f1, float s1, int color)
    {
        vertex(pose, buffer, fixed, first, second, at, f0, s0, color);
        vertex(pose, buffer, fixed, first, second, at, f1, s0, color);
        vertex(pose, buffer, fixed, first, second, at, f1, s1, color);
        vertex(pose, buffer, fixed, first, second, at, f0, s1, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Direction.Axis fixed, Direction.Axis first, Direction.Axis second,
                               float at, float f, float s, int color)
    {
        float[] xyz = new float[3];
        xyz[fixed.ordinal()] = at;
        xyz[first.ordinal()] = f;
        xyz[second.ordinal()] = s;
        buffer.addVertex(pose, xyz[0], xyz[1], xyz[2]).setColor(color);
    }

    /**
     * Fissure violet cracks spreading over the shell's outer faces as the countdown runs, foreshadowing the wound.
     */
    private static void cracks(PoseStack.Pose pose, VertexConsumer buffer, State state, float fade)
    {
        var grown = state.warning;
        if (grown <= 0.001F)
            return;
        var alphaBase = CRACK_ALPHA * state.warnStrength * fade * (0.7F + 0.3F * state.pulse);
        var random = RandomSource.create(state.seed * 31 + 7);
        for (var face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP})
        {
            var fixed = face.getAxis();
            var plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 2 + SURFACE_OFFSET : -1 - SURFACE_OFFSET;
            var first = fixed == Direction.Axis.Y ? Direction.Axis.X : fixed == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            var second = fixed == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;

            for (var crack = 0; crack < CRACKS_PER_FACE; crack++)
            {
                float u = -0.6F + random.nextFloat() * 2.2F, v = -0.6F + random.nextFloat() * 2.2F;
                var angle = random.nextFloat() * Mth.TWO_PI;
                var shown = grown * CRACK_SEGMENTS;
                for (var segment = 0; segment < CRACK_SEGMENTS; segment++)
                {
                    angle += (random.nextFloat() * 2 - 1) * CRACK_TURN;
                    var step = CRACK_STEP * (0.6F + 0.8F * random.nextFloat());
                    var visible = Math.clamp(shown - segment, 0, 1);
                    float nu = u + Mth.cos(angle) * step, nv = v + Mth.sin(angle) * step;
                    // Kept on the face: a crack meeting the edge turns back in
                    if (nu < -0.95F || nu > 1.95F)
                    {
                        angle = Mth.PI - angle;
                        nu = Math.clamp(nu, -0.95F, 1.95F);
                    }
                    if (nv < -0.95F || nv > 1.95F)
                    {
                        angle = -angle;
                        nv = Math.clamp(nv, -0.95F, 1.95F);
                    }
                    if (visible > 0)
                    {
                        float eu = Mth.lerp(visible, u, nu), ev = Mth.lerp(visible, v, nv);
                        var width = CRACK_WIDTH * (1 - 0.6F * segment / CRACK_SEGMENTS);
                        flatLine(pose, buffer, fixed, first, second, plane, u, v, eu, ev, width, alpha(alphaBase, CRACK_VIOLET));
                    }
                    u = nu;
                    v = nv;
                }
            }
        }
    }

    private static void flatLine(PoseStack.Pose pose, VertexConsumer buffer, Direction.Axis fixed, Direction.Axis first, Direction.Axis second,
                                 float at, float u0, float v0, float u1, float v1, float width, int color)
    {
        float du = u1 - u0, dv = v1 - v0;
        var length = Mth.sqrt(du * du + dv * dv);
        if (length < 1e-4F)
            return;
        float nu = -dv / length * width / 2, nv = du / length * width / 2;
        vertex(pose, buffer, fixed, first, second, at, u0 - nu, v0 - nv, color);
        vertex(pose, buffer, fixed, first, second, at, u0 + nu, v0 + nv, color);
        vertex(pose, buffer, fixed, first, second, at, u1 + nu, v1 + nv, color);
        vertex(pose, buffer, fixed, first, second, at, u1 - nu, v1 - nv, color);
    }

    /**
     * Fissure violet light blazing out of a gap in the containment.
     */
    private static void breach(PoseStack.Pose pose, VertexConsumer buffer, BlockPos gap, float strength)
    {
        if (strength <= 0.001F)
            return;
        var center = new Vector3f(gap.getX() + 0.5F, gap.getY() + 0.5F, gap.getZ() + 0.5F);
        disk3(pose, buffer, center, BREACH_RADIUS * 0.25F, BREACH_RADIUS, POINT_CORE, BREACH_VIOLET, BREACH_ALPHA * strength, BREACH_ALPHA * strength);
    }

    /**
     * Sparks drawn in from all around while it lights.
     */
    private static void sparks(PoseStack.Pose pose, VertexConsumer buffer, State state, Vector3f center)
    {
        var random = RandomSource.create(state.seed);
        for (var spark = 0; spark < SPARKS; spark++)
        {
            var direction = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1).normalize();
            var progress = (state.time * (0.03F + 0.02F * random.nextFloat()) + spark / (float) SPARKS) % 1;
            var at = new Vector3f(direction).mul(SPARK_REACH * (1 - progress)).add(center);
            var alpha = progress * (0.4F + 0.6F * state.charge);
            disk3(pose, buffer, at, SPARK_SIZE, SPARK_SIZE * 2.5F, SPARK, SPARK, alpha, alpha * 0.4F);
        }
    }

    /**
     * A soft glow seen from every side: a bright core fading out to a halo, on three crossed planes.
     */
    private static void disk3(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, float core, float halo, int coreColor, int haloColor,
                              float coreAlpha, float haloAlpha)
    {
        var inner = alpha(coreAlpha, coreColor);
        var ring = alpha(haloAlpha, haloColor);
        var clear = alpha(0, haloColor);
        for (var plane = 0; plane < 3; plane++)
        {
            var u = plane == 0 ? new Vector3f(1, 0, 0) : plane == 1 ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
            var v = plane == 0 ? new Vector3f(0, 1, 0) : plane == 1 ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
            for (var segment = 0; segment < DISK_SEGMENTS; segment++)
            {
                var a0 = segment * Mth.TWO_PI / DISK_SEGMENTS;
                var a1 = (segment + 1) * Mth.TWO_PI / DISK_SEGMENTS;
                var c0 = onDisk(center, u, v, a0, core);
                var c1 = onDisk(center, u, v, a1, core);
                var h0 = onDisk(center, u, v, a0, halo);
                var h1 = onDisk(center, u, v, a1, halo);
                put(pose, buffer, center, inner);
                put(pose, buffer, c0, inner);
                put(pose, buffer, c1, inner);
                put(pose, buffer, center, inner);

                put(pose, buffer, c0, ring);
                put(pose, buffer, h0, clear);
                put(pose, buffer, h1, clear);
                put(pose, buffer, c1, ring);
            }
        }
    }

    private static Vector3f onDisk(Vector3f center, Vector3f u, Vector3f v, float angle, float radius)
    {
        return new Vector3f(center).add(new Vector3f(u).mul(Mth.cos(angle) * radius)).add(new Vector3f(v).mul(Mth.sin(angle) * radius));
    }

    private static void put(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, int color)
    {
        buffer.addVertex(pose, at.x, at.y, at.z).setColor(color);
    }

    /**
     * Two crossed planes along a piece, so it reads from every side but straight down its length.
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
     * A leaping arc between a corner of the crown and a pylon's bundle: leaps run outward while it gives energy, and inward while it draws to light.
     */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, State state, ArcDraw arc, float fade)
    {
        if (fade <= 0.001F)
            return;
        var end = new Vector3f((float) arc.end().x, (float) arc.end().y, (float) arc.end().z);
        var start = crownNearest(end);
        var axis = new Vector3f(end).sub(start);
        var length = axis.length();
        if (length < 0.5F)
            return;
        axis.div(length);
        var side = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        var up = new Vector3f(side).cross(axis).normalize();

        var joints = Math.max(MIN_JOINTS, Mth.ceil(length / JOINT_SPACING));
        var random = RandomSource.create(arc.seed());
        var points = new Vector3f[joints + 1];
        for (var i = 0; i <= joints; i++)
        {
            var point = new Vector3f(start).lerp(end, i / (float) joints);
            if (i > 0 && i < joints)
            {
                point.add(new Vector3f(side).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
                point.add(new Vector3f(up).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
            }
            points[i] = point;
        }

        var pieces = joints * SUBDIVISIONS;
        for (var piece = 0; piece < pieces; piece++)
        {
            var joint = piece / SUBDIVISIONS;
            var from = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS) / (float) SUBDIVISIONS);
            var to = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS + 1) / (float) SUBDIVISIONS);

            var along = (piece + 0.5F) / pieces * length;
            var leap = 0F;
            for (var head : arc.heads())
            {
                var progress = arc.inward() ? 1 - head : head;
                var middle = progress * (length + LEAP_LENGTH) - LEAP_LENGTH / 2;
                leap = Math.max(leap, 1 - Math.abs(along - middle) / (LEAP_LENGTH / 2));
            }

            var widen = 1 + (LEAP_WIDEN - 1) * leap;
            ribbon(pose, buffer, from, to, side, up, ARC_HALO_WIDTH * widen, alpha(ARC_HALO_ALPHA * fade * (1 + leap), AZURIUM_MID));
            ribbon(pose, buffer, from, to, side, up, ARC_CORE_WIDTH * widen, alpha(Math.min(1, ARC_CORE_ALPHA * (1 + leap)) * fade,
                    ARGB.srgbLerp(0.6F * leap, AZURIUM_LIGHT, 0xFFFFFF)));
        }
    }

    /**
     * The corner of the crown nearest a target, set just inside the shell's top corner.
     */
    private static Vector3f crownNearest(Vector3f target)
    {
        Vector3f best = null;
        var bestDistance = Float.MAX_VALUE;
        for (var x : new float[]{-1 + CROWN_INSET, 2 - CROWN_INSET})
        {
            for (var z : new float[]{-1 + CROWN_INSET, 2 - CROWN_INSET})
            {
                var corner = new Vector3f(x, 2, z);
                var distance = corner.distanceSquared(target);
                if (distance < bestDistance)
                {
                    best = corner;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    //
    // Marker
    //

    /**
     * The gold bands pulsing fissure violet, from once every two seconds to four times a second over the countdown.
     */
    private static void bands(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var color = alpha(BAND_ALPHA * state.pulse * (1 - state.collapse), FISSURE_VIOLET);
        for (var face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST})
        {
            var fixed = face.getAxis();
            var plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 2 + SURFACE_OFFSET : -1 - SURFACE_OFFSET;
            var across = fixed == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            for (var layer = -1; layer <= 1; layer++)
                quad(pose, buffer, fixed, across, Direction.Axis.Y, plane, -1, layer + BAND_BOTTOM, 2, layer + BAND_TOP, color);
        }
    }

    //
    // Preview
    //

    private static void submitGhosts(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.ghosts.isEmpty())
            return;

        var ghost = new QuadInstance();
        ghost.setColor(GHOST_COLOR);
        ghost.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        ghost.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        var conflict = new QuadInstance();
        conflict.setColor(CONFLICT_COLOR);
        conflict.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        conflict.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        for (var draw : state.ghosts)
        {
            var quads = ReactorGhostQuads.of(draw.state());
            var instance = draw.conflict() ? conflict : ghost;
            poseStack.pushPose();
            poseStack.translate(draw.offset().getX(), draw.offset().getY(), draw.offset().getZ());
            // Shrunk to a centered half-size block, as the Shatter Forge's preview does, so the hint never hides the rest
            poseStack.translate(0.25F, 0.25F, 0.25F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
            {
                for (var quad : quads)
                    buffer.putBakedQuad(pose, quad, instance);
            });
            poseStack.popPose();
        }
    }

    private static int alpha(float alpha, int color)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), color);
    }

    /**
     * Arcs reach out to a pylon's field away, and the shell spans the blocks around the core.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 96;
    }
}
