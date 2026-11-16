package net.voxelindustry.voidheart.client.render.energy.pearl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import net.voxelindustry.voidheart.common.energy.pearl.PearlTremor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A Collapsing Rubrum Pearl hung under its chain: a crystal folding in on itself around a dark point.
 * <p>
 * It hangs by one corner from the chain's foot. A speck grows into a body, buds fold out of the body's corners and smaller buds out of theirs, each
 * sunk into its parent and leaning back toward the centre; then the whole pulls inward, the body shrinks away and a black core shows through the
 * gaps, ringed with dim red. In bursts drawn by {@link PearlTremor} every part jolts on its own each tick, the inner ones most, so it chatters rather
 * than shakes. While fed, Fiery and void motes drift into it, and pearls of one choir that shudder together throw a jagged thread between them.
 * <p>
 * Render types. The body, its buds and the stem are {@link RenderTypes#entityCutout}, opaque, writing depth and lit by the world, so the pearl is a
 * solid thing by day and in the dark. The core is the same, given full light so its rim glows while its black stays black. The seams over the body
 * and the rim are {@link VoidHeartRenderTypes#radiant}: added, never writing depth, so they cross in any order without flicker while the body and
 * terrain still hide them; the seams sit {@value #SEAM_LIFT} off the faces they light so the depth test never ties. Motes and threads are
 * {@link VoidHeartRenderTypes#GLOW}, crossed ribbons that read from any side.
 * <p>
 * Per frame, at worst: 13 cubes and a stem (84 quads), 5 seam cubes (30), the core and rim (7), 8 motes (16) and up to 5 threads of
 * {@value #THREAD_JOINTS} segments in two layers (100), all rebuilt from the tile's numbers, about 240 quads a pearl.
 */
public class HangingPearlRender implements BlockEntityRenderer<HangingPearlTile, HangingPearlRender.State>
{
    private static final Identifier BODY_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/rubrum_pearl/body.png");
    private static final Identifier CORE_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/rubrum_pearl/core.png");
    private static final Identifier SEAMS_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/rubrum_pearl/seams.png");
    private static final Identifier RIM_TEXTURE   = Identifier.fromNamespaceAndPath(MODID, "textures/entity/rubrum_pearl/rim.png");

    private static final RenderType BODY_TYPE  = RenderTypes.entityCutout(BODY_TEXTURE);
    private static final RenderType CORE_TYPE  = RenderTypes.entityCutout(CORE_TEXTURE);
    private static final RenderType SEAMS_TYPE = VoidHeartRenderTypes.radiant(SEAMS_TEXTURE);
    private static final RenderType RIM_TYPE   = VoidHeartRenderTypes.radiant(RIM_TEXTURE);

    // Shape
    /** Where the chain's foot meets the block. */
    private static final float HANG_Y      = 1;
    private static final float SPECK_HALF  = 1.5F / 16;
    private static final float BODY_HALF   = 2.5F / 16;
    /** Size of a bud against its parent. */
    private static final float CHILD_SCALE = 0.55F;
    /** How far toward its parent's corner a bud sits, so it stays sunk into it. */
    private static final float SINK        = 0.8F;
    /** Degrees each bud is turned off its parent, so no faces run parallel. */
    private static final float LEAN        = 14;
    /** Share of their distance the buds are drawn inward once collapsed. */
    private static final float PULL        = 0.22F;
    /** The body's size against its full size once collapsed, small enough to hide inside the core. */
    private static final float BODY_COLLAPSED = 0.35F;
    /** The core's size against the full body, large enough to show between the buds once they fold. */
    private static final float CORE_SCALE  = 0.62F;
    /** Share of their size the buds fold away once collapsed, opening gaps onto the core. */
    private static final float BUD_FOLD    = 0.3F;
    private static final float STEM_HALF   = 0.5F / 16;
    /** Degrees a tick the whole pearl turns under its chain. */
    private static final float SPIN        = 0.12F;

    // Color and light
    private static final int   BODY_COLOR      = 0xFFFFFF;
    /** The body darkens toward this as it collapses into the core. */
    private static final int   BODY_DARK       = 0x5A3040;
    private static final int   STEM_COLOR      = 0x8A5A5A;
    private static final float SEAM_LIFT       = 0.004F;
    private static final int   SEAM_ALPHA_LOW  = 60;
    private static final int   SEAM_ALPHA_HIGH = 190;
    /** Seam alpha added at the height of a burst. */
    private static final int   SEAM_FLARE      = 60;
    /** Seams of a starved pearl dim to this share. */
    private static final float STARVED_DIM     = 0.55F;
    /** A seed not yet kindled is this much duller. */
    private static final int   UNKINDLED_TINT  = 0x8C6670;
    /** Share of their light the buds' seams lose once collapsed, so the core's rim leads. */
    private static final float BUD_SEAM_FADE   = 0.5F;
    private static final float RIM_RADIUS      = 2.6F;
    private static final int   RIM_ALPHA       = 110;
    private static final int   RIM_FLARE       = 60;
    private static final float RIM_TURN        = 0.4F;

    // Tremor
    /** Jolts at the height of a burst, in blocks: the body and core, the first buds, the second buds. */
    private static final float[] JOLT = {0.7F / 16, 0.4F / 16, 0.25F / 16};

    // Motes
    private static final int   MOTES        = 8;
    private static final int   MOTE_LIFE    = 34;
    private static final float MOTE_FROM    = 0.75F;
    private static final float MOTE_TO      = 0.06F;
    private static final float MOTE_HALF    = 0.012F;
    private static final float MOTE_TAIL    = 0.09F;
    private static final int   MOTE_ALPHA   = 210;
    private static final int   FIERY_COLOR  = 0xFF7A2A;
    private static final int   VOID_COLOR   = 0x5C9CFF;

    // Threads
    private static final int   THREAD_JOINTS     = 5;
    private static final float THREAD_JAG        = 0.12F;
    private static final float THREAD_CORE_HALF  = 0.008F;
    private static final float THREAD_HALO_HALF  = 0.03F;
    private static final int   THREAD_CORE_COLOR = 0xFFC08A;
    private static final int   THREAD_HALO_COLOR = 0xE0361F;
    private static final int   THREAD_CORE_ALPHA = 130;
    private static final int   THREAD_HALO_ALPHA = 45;

    /** Corners of the body the first buds grow from: the bottom one and three around its upper half. The top one hangs from the chain. */
    private static final Vector3f[] FIRST_CORNERS = {
            new Vector3f(-1, -1, -1), new Vector3f(-1, 1, 1), new Vector3f(1, -1, 1), new Vector3f(1, 1, -1)};

    private static final float SQRT_THREE = (float) Math.sqrt(3);

    /** Turns the body so its diagonal stands upright, hanging by one corner. */
    private static final Quaternionf UPRIGHT = new Quaternionf().rotationTo(new Vector3f(1, 1, 1).normalize(), new Vector3f(0, 1, 0));

    public static class State extends BlockEntityRenderState
    {
        public float   share;
        public float   time;
        public long    tick;
        public long    seed;
        public boolean fed;
        public boolean starved;
        public float   tremor;
        /** False for the items, which hang from nothing and so have no stem. */
        public boolean hanging = true;
        /** False for the seed the reactor throws off, dull and without lit seams until the forge kindles it. */
        public boolean kindled = true;
        public Vector3f camera = new Vector3f();
        public final List<Vector3f> threads = new ArrayList<>();
        public final List<Float>    threadStrengths = new ArrayList<>();
    }

    public HangingPearlRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HangingPearlTile pearl, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(pearl, state, partialTicks, cameraPosition, breakProgress);

        var level = pearl.getLevel();
        var pos = pearl.getBlockPos();
        state.share = pearl.growth().share();
        state.time = level == null ? 0 : level.getGameTime() + partialTicks;
        state.tick = level == null ? 0 : level.getGameTime();
        state.seed = pearl.seed();
        state.fed = pearl.isFed() && !pearl.growth().isMature();
        state.starved = !pearl.isFed() && !pearl.growth().isMature();
        state.camera = new Vector3f((float) (cameraPosition.x - pos.getX()), (float) (cameraPosition.y - pos.getY()), (float) (cameraPosition.z - pos.getZ()));

        var moment = PearlTremor.at(pearl.seed(), pearl.choirSeed(), pearl.sync(partialTicks), state.time);
        state.tremor = moment.intensity();

        // A thread to each pearl of the choir shuddering with this one, drawn by the lower of the two so it is drawn once
        state.threads.clear();
        state.threadStrengths.clear();
        if (level != null && moment.shared() && moment.intensity() > 0)
        {
            var own = center(state.share);
            for (var other : pearl.choir())
            {
                if (other.asLong() < pos.asLong() || !(level.getBlockEntity(other) instanceof HangingPearlTile neighbour))
                    continue;
                var theirs = PearlTremor.at(neighbour.seed(), neighbour.choirSeed(), neighbour.sync(partialTicks), state.time);
                if (!theirs.shared() || theirs.intensity() <= 0)
                    continue;
                var end = center(neighbour.growth().share()).add(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ());
                state.threads.add(new Vector3f(own));
                state.threads.add(end);
                state.threadStrengths.add(Math.min(moment.intensity(), theirs.intensity()));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        submitPearl(state, poseStack, collector);
    }

    /**
     * Draws a pearl in block space from its state, for the hung block and for the item, which builds a state of its own.
     */
    static void submitPearl(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var body = new Quads();
        var core = new Quads();
        var seams = new Quads();
        var rim = new Quads();
        var glow = new Quads();
        build(state, body, core, seams, rim, glow);

        if (!body.isEmpty())
            collector.submitCustomGeometry(poseStack, BODY_TYPE, (pose, buffer) -> body.emitTextured(pose, buffer, state.lightCoords));
        if (!core.isEmpty())
            collector.submitCustomGeometry(poseStack, CORE_TYPE, (pose, buffer) -> core.emitTextured(pose, buffer, LightCoordsUtil.FULL_BRIGHT));
        if (!seams.isEmpty())
            collector.submitCustomGeometry(poseStack, SEAMS_TYPE, (pose, buffer) -> seams.emitTextured(pose, buffer, LightCoordsUtil.FULL_BRIGHT));
        if (!rim.isEmpty())
            collector.submitCustomGeometry(poseStack, RIM_TYPE, (pose, buffer) -> rim.emitTextured(pose, buffer, LightCoordsUtil.FULL_BRIGHT));
        if (!glow.isEmpty())
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, glow::emitPlain);
    }

    //
    // Shape
    //

    /**
     * Where the pearl's centre hangs: a speck right under the chain's foot, then the full body's corner touching it.
     */
    static Vector3f center(float share)
    {
        var half = share < 0.25F ? Mth.lerp(ease(share / 0.25F), SPECK_HALF, BODY_HALF) : BODY_HALF;
        return new Vector3f(0.5F, HANG_Y - half * SQRT_THREE, 0.5F);
    }

    private static float stagePart(float share, float from)
    {
        return Mth.clamp((share - from) / 0.25F, 0, 1);
    }

    private static float ease(float t)
    {
        return t * t * (3 - 2 * t);
    }

    /**
     * How far from its centre a still pearl reaches at this growth, so an item can grow it to fill its slot whatever its stage.
     */
    static float reach(float share)
    {
        share = Math.min(1, share);
        var grown = share < 0.25F ? Mth.lerp(ease(share / 0.25F), SPECK_HALF, BODY_HALF) : BODY_HALF;
        var firstBuds = ease(stagePart(share, 0.25F));
        var secondBuds = ease(stagePart(share, 0.5F));
        var collapse = ease(stagePart(share, 0.75F));
        var pull = 1 - PULL * collapse;
        var fold = 1 - BUD_FOLD * collapse;

        var reach = grown * Mth.lerp(collapse, 1, BODY_COLLAPSED) * SQRT_THREE;
        if (collapse > 0)
            reach = Math.max(reach, BODY_HALF * CORE_SCALE * Math.min(1, collapse * 2) * SQRT_THREE);
        if (firstBuds > 0)
        {
            var budCenter = BODY_HALF * SINK * pull * SQRT_THREE;
            reach = Math.max(reach, budCenter + BODY_HALF * CHILD_SCALE * firstBuds * fold * SQRT_THREE);
            if (secondBuds > 0)
                reach = Math.max(reach, budCenter + BODY_HALF * CHILD_SCALE * SINK * pull * SQRT_THREE
                        + BODY_HALF * CHILD_SCALE * CHILD_SCALE * secondBuds * fold * SQRT_THREE);
        }
        return reach;
    }

    private static void build(State state, Quads body, Quads core, Quads seams, Quads rim, Quads glow)
    {
        var share = Math.min(1, state.share);
        var grown = share < 0.25F ? Mth.lerp(ease(share / 0.25F), SPECK_HALF, BODY_HALF) : BODY_HALF;
        var firstBuds = ease(stagePart(share, 0.25F));
        var secondBuds = ease(stagePart(share, 0.5F));
        var collapse = ease(stagePart(share, 0.75F));
        var center = center(share);

        var yaw = (state.time * SPIN + (state.seed & 0xFFFF) / 65536F * 360) * Mth.DEG_TO_RAD;
        var turn = new Quaternionf().rotationY(yaw).mul(UPRIGHT);
        var tremor = state.tremor;
        var tint = state.kindled ? lerpColor(BODY_COLOR, BODY_DARK, collapse) : UNKINDLED_TINT;

        var seamAlpha = Mth.lerp(share, SEAM_ALPHA_LOW, SEAM_ALPHA_HIGH) + SEAM_FLARE * tremor;
        if (state.starved)
            seamAlpha *= STARVED_DIM;
        var seamColor = ARGB.color(Math.round(Mth.clamp(seamAlpha, 0, 255)), 0xFFFFFF);

        // The stem from the chain's foot down to the centre, so a shrinking body never leaves the pearl floating
        if (state.hanging)
            body.box(new Vector3f(0.5F, (HANG_Y + center.y) / 2, 0.5F), new Quaternionf(), STEM_HALF, (HANG_Y - center.y) / 2, STEM_HALF, ARGB.color(255, STEM_COLOR));

        // The body, shrinking into the core as it collapses
        var bodyHalf = grown * Mth.lerp(collapse, 1, BODY_COLLAPSED);
        var bodyAt = jolted(center, state, 0, 0);
        body.cube(bodyAt, turn, bodyHalf, ARGB.color(255, tint));
        if (state.kindled)
            seams.cube(bodyAt, turn, bodyHalf + SEAM_LIFT, seamColor);

        if (collapse > 0)
        {
            var coreHalf = BODY_HALF * CORE_SCALE * Math.min(1, collapse * 2);
            var coreTurn = new Quaternionf().rotationY(-yaw * 1.7F + Mth.HALF_PI / 2).mul(UPRIGHT);
            core.cube(jolted(center, state, 0, 1), coreTurn, coreHalf, ARGB.color(255, 0xFFFFFF));
            var rimAlpha = Math.round(Mth.clamp(collapse * RIM_ALPHA + tremor * RIM_FLARE, 0, 255));
            rim.sprite(center, state.camera, coreHalf * RIM_RADIUS, state.time * RIM_TURN, ARGB.color(rimAlpha, 0xFFFFFF));
        }

        if (firstBuds <= 0)
        {
            motes(state, center, glow);
            threads(state, glow);
            return;
        }

        var pull = 1 - PULL * collapse;
        var fold = 1 - BUD_FOLD * collapse;
        var budFull = BODY_HALF * CHILD_SCALE;
        var budSeamColor = ARGB.color(Math.round(ARGB.alpha(seamColor) * (1 - BUD_SEAM_FADE * collapse)), 0xFFFFFF);
        for (int i = 0; i < FIRST_CORNERS.length; i++)
        {
            var corner = FIRST_CORNERS[i];
            var offset = new Vector3f(corner).mul(BODY_HALF * SINK * pull);
            turn.transform(offset);
            var budTurn = new Quaternionf(turn).mul(lean(corner));
            var budAt = jolted(new Vector3f(center).add(offset), state, 1, 2 + i);
            var budHalf = budFull * firstBuds * fold;
            body.cube(budAt, budTurn, budHalf, ARGB.color(255, tint));
            if (state.kindled)
                seams.cube(budAt, budTurn, budHalf + SEAM_LIFT, budSeamColor);

            if (secondBuds <= 0)
                continue;
            var children = lowestFlips(corner, budTurn);
            for (int j = 0; j < children.length; j++)
            {
                var child = children[j];
                var childOffset = new Vector3f(child).mul(budFull * SINK * pull);
                budTurn.transform(childOffset);
                var childTurn = new Quaternionf(budTurn).mul(lean(child));
                var childAt = jolted(new Vector3f(budAt).add(childOffset), state, 2, 6 + i * 2 + j);
                body.cube(childAt, childTurn, budFull * CHILD_SCALE * secondBuds * fold, ARGB.color(255, tint));
            }
        }

        motes(state, center, glow);
        threads(state, glow);
    }

    /**
     * A bud's turn off its parent, about an axis across its corner.
     */
    private static Quaternionf lean(Vector3f corner)
    {
        var axis = new Vector3f(corner).cross(1, 0, 0);
        if (axis.lengthSquared() < 1e-6F)
            axis.set(0, 1, 0);
        return new Quaternionf().rotationAxis(LEAN * Mth.DEG_TO_RAD, axis.normalize());
    }

    /**
     * Two of the corners next to a bud's outer one, the two lowest in the world, so second buds never climb into the chain.
     */
    private static Vector3f[] lowestFlips(Vector3f corner, Quaternionf turn)
    {
        var flips = new Vector3f[3];
        var heights = new float[3];
        for (int axis = 0; axis < 3; axis++)
        {
            var flipped = new Vector3f(corner);
            flipped.setComponent(axis, -flipped.get(axis));
            flips[axis] = flipped;
            heights[axis] = turn.transform(new Vector3f(flipped)).y;
        }
        var highest = 0;
        for (int axis = 1; axis < 3; axis++)
            if (heights[axis] > heights[highest])
                highest = axis;
        return new Vector3f[]{flips[(highest + 1) % 3], flips[(highest + 2) % 3]};
    }

    private static Vector3f jolted(Vector3f at, State state, int depth, int part)
    {
        if (state.tremor <= 0)
            return new Vector3f(at);
        var amount = JOLT[depth] * state.tremor;
        return new Vector3f(at).add(PearlTremor.jolt(state.seed, state.tick, part, 0) * amount,
                PearlTremor.jolt(state.seed, state.tick, part, 1) * amount,
                PearlTremor.jolt(state.seed, state.tick, part, 2) * amount);
    }

    /**
     * Fiery and void motes falling in from all around while it is fed, each with a tail laid back along its way so it never steps pixel to pixel.
     */
    private static void motes(State state, Vector3f center, Quads glow)
    {
        if (!state.fed)
            return;
        for (int i = 0; i < MOTES; i++)
        {
            var offset = (float) i / MOTES * MOTE_LIFE;
            var age = state.time + offset;
            var cycle = (long) Math.floor(age / MOTE_LIFE);
            var t = (age - cycle * MOTE_LIFE) / MOTE_LIFE;
            var direction = direction(state.seed, i, cycle);
            var fall = t * t;
            var at = new Vector3f(direction).mul(Mth.lerp(fall, MOTE_FROM, MOTE_TO)).add(center);
            var tail = new Vector3f(direction).mul(MOTE_TAIL * (0.4F + t)).add(at);
            var alpha = Math.round(MOTE_ALPHA * Math.min(1, t * 4) * Math.min(1, (1 - t) * 6));
            var color = ARGB.color(alpha, i % 2 == 0 ? FIERY_COLOR : VOID_COLOR);
            glow.segment(at, tail, MOTE_HALF, color, ARGB.color(0, color));
        }
    }

    private static Vector3f direction(long seed, int mote, long cycle)
    {
        var x = PearlTremor.jolt(seed, cycle, 100 + mote, 0);
        var y = PearlTremor.jolt(seed, cycle, 100 + mote, 1);
        var z = PearlTremor.jolt(seed, cycle, 100 + mote, 2);
        var direction = new Vector3f(x, y, z);
        if (direction.lengthSquared() < 1e-4F)
            direction.set(0, -1, 0);
        return direction.normalize();
    }

    /**
     * A jagged thread between pearls shuddering together, re-jagged each tick, a bright core in a red halo.
     */
    private static void threads(State state, Quads glow)
    {
        for (int i = 0; i < state.threadStrengths.size(); i++)
        {
            var from = state.threads.get(i * 2);
            var to = state.threads.get(i * 2 + 1);
            var strength = state.threadStrengths.get(i);
            var core = ARGB.color(Math.round(THREAD_CORE_ALPHA * strength), THREAD_CORE_COLOR);
            var halo = ARGB.color(Math.round(THREAD_HALO_ALPHA * strength), THREAD_HALO_COLOR);

            var previous = from;
            for (int joint = 1; joint <= THREAD_JOINTS; joint++)
            {
                var along = (float) joint / THREAD_JOINTS;
                var point = new Vector3f(from).lerp(to, along);
                if (joint < THREAD_JOINTS)
                {
                    var jag = THREAD_JAG * Mth.sin(along * Mth.PI);
                    point.add(PearlTremor.jolt(state.seed, state.tick, 200 + i * 8 + joint, 0) * jag,
                            PearlTremor.jolt(state.seed, state.tick, 200 + i * 8 + joint, 1) * jag,
                            PearlTremor.jolt(state.seed, state.tick, 200 + i * 8 + joint, 2) * jag);
                }
                glow.segment(previous, point, THREAD_CORE_HALF, core, core);
                glow.segment(previous, point, THREAD_HALO_HALF, halo, halo);
                previous = point;
            }
        }
    }

    private static int lerpColor(int from, int to, float t)
    {
        return ARGB.color(0,
                Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF)),
                Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF)),
                Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF))) & 0xFFFFFF;
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 64;
    }

    /**
     * Quads in block space, gathered while submitting and drawn later in the frame. Each vertex carries a place in its texture and a normal, which the
     * plain glow leaves unused.
     */
    private static final class Quads
    {
        private static final Vector3f UP = new Vector3f(0, 1, 0);

        private final FloatArrayList positions = new FloatArrayList();
        private final FloatArrayList uvs       = new FloatArrayList();
        private final FloatArrayList normals   = new FloatArrayList();
        private final IntArrayList   colors    = new IntArrayList();

        boolean isEmpty()
        {
            return colors.isEmpty();
        }

        void emitPlain(PoseStack.Pose pose, VertexConsumer buffer)
        {
            for (int i = 0; i < colors.size(); i++)
                buffer.addVertex(pose, positions.getFloat(i * 3), positions.getFloat(i * 3 + 1), positions.getFloat(i * 3 + 2)).setColor(colors.getInt(i));
        }

        void emitTextured(PoseStack.Pose pose, VertexConsumer buffer, int light)
        {
            for (int i = 0; i < colors.size(); i++)
                buffer.addVertex(pose, positions.getFloat(i * 3), positions.getFloat(i * 3 + 1), positions.getFloat(i * 3 + 2))
                        .setColor(colors.getInt(i))
                        .setUv(uvs.getFloat(i * 2), uvs.getFloat(i * 2 + 1))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, normals.getFloat(i * 3), normals.getFloat(i * 3 + 1), normals.getFloat(i * 3 + 2));
        }

        private void vertex(Vector3f at, float u, float v, Vector3f normal, int color)
        {
            positions.add(at.x);
            positions.add(at.y);
            positions.add(at.z);
            uvs.add(u);
            uvs.add(v);
            normals.add(normal.x);
            normals.add(normal.y);
            normals.add(normal.z);
            colors.add(color);
        }

        void cube(Vector3f center, Quaternionf turn, float half, int color)
        {
            box(center, turn, half, half, half, color);
        }

        /**
         * A turned box, each side showing the whole texture, its corners listed counter-clockwise seen from outside.
         */
        void box(Vector3f center, Quaternionf turn, float halfX, float halfY, float halfZ, int color)
        {
            if (halfX <= 0 || halfY <= 0 || halfZ <= 0)
                return;
            var x = turn.transform(new Vector3f(1, 0, 0));
            var y = turn.transform(new Vector3f(0, 1, 0));
            var z = turn.transform(new Vector3f(0, 0, 1));
            side(center, x, halfX, y, halfY, z, halfZ, color);
            side(center, new Vector3f(x).negate(), halfX, z, halfZ, y, halfY, color);
            side(center, y, halfY, z, halfZ, x, halfX, color);
            side(center, new Vector3f(y).negate(), halfY, x, halfX, z, halfZ, color);
            side(center, z, halfZ, x, halfX, y, halfY, color);
            side(center, new Vector3f(z).negate(), halfZ, y, halfY, x, halfX, color);
        }

        /**
         * One side facing along normal, spanned by u and v, with u x v pointing the same way as normal so it winds counter-clockwise from outside.
         */
        private void side(Vector3f center, Vector3f normal, float depth, Vector3f u, float halfU, Vector3f v, float halfV, int color)
        {
            var middle = new Vector3f(normal).mul(depth).add(center);
            var su = new Vector3f(u).mul(halfU);
            var sv = new Vector3f(v).mul(halfV);
            var flip = new Vector3f(u).cross(v).dot(normal) < 0;
            if (flip)
                sv.negate();
            vertex(new Vector3f(middle).sub(su).sub(sv), 0, 1, normal, color);
            vertex(new Vector3f(middle).add(su).sub(sv), 1, 1, normal, color);
            vertex(new Vector3f(middle).add(su).add(sv), 1, 0, normal, color);
            vertex(new Vector3f(middle).sub(su).add(sv), 0, 0, normal, color);
        }

        /**
         * The whole texture turned to the camera and spun in its own plane, in degrees.
         */
        void sprite(Vector3f center, Vector3f camera, float radius, float spin, int color)
        {
            var toCamera = new Vector3f(camera).sub(center);
            if (toCamera.lengthSquared() < 1e-6F)
                return;
            toCamera.normalize();
            var side = new Vector3f(toCamera).cross(Math.abs(toCamera.y) > 0.99F ? new Vector3f(1, 0, 0) : UP).normalize();
            var up = new Vector3f(side).cross(toCamera);
            var angle = spin * Mth.DEG_TO_RAD;
            var u = new Vector3f(side).mul(Mth.cos(angle) * radius).add(new Vector3f(up).mul(Mth.sin(angle) * radius));
            var v = new Vector3f(up).mul(Mth.cos(angle) * radius).sub(new Vector3f(side).mul(Mth.sin(angle) * radius));
            vertex(new Vector3f(center).sub(u).add(v), 0, 0, toCamera, color);
            vertex(new Vector3f(center).sub(u).sub(v), 0, 1, toCamera, color);
            vertex(new Vector3f(center).add(u).sub(v), 1, 1, toCamera, color);
            vertex(new Vector3f(center).add(u).add(v), 1, 0, toCamera, color);
        }

        /**
         * Two ribbons crossed along a segment, so it reads as a line from any side, with a color at each end.
         */
        void segment(Vector3f from, Vector3f to, float half, int fromColor, int toColor)
        {
            var direction = new Vector3f(to).sub(from);
            if (direction.lengthSquared() < 1e-8F)
                return;
            direction.normalize();
            var helper = Math.abs(direction.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            var first = new Vector3f(direction).cross(helper).normalize().mul(half);
            var second = new Vector3f(direction).cross(first).normalize().mul(half);
            ribbon(from, to, first, fromColor, toColor);
            ribbon(from, to, second, fromColor, toColor);
        }

        private void ribbon(Vector3f from, Vector3f to, Vector3f side, int fromColor, int toColor)
        {
            vertex(new Vector3f(from).sub(side), 0, 0, UP, fromColor);
            vertex(new Vector3f(from).add(side), 0, 1, UP, fromColor);
            vertex(new Vector3f(to).add(side), 1, 1, UP, toColor);
            vertex(new Vector3f(to).sub(side), 1, 0, UP, toColor);
        }
    }
}
