package net.voxelindustry.voidheart.client.render.crystal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidBeamRender;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.energy.BlockQuads;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundleBlock;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleBlock;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A bundle rising out of the cradle's bed. The shards fed in lie dim on the basin floor and go as they are used, the bundle's own model grows up from among
 * them, and ley pools faintly at its foot while it is fed. Fed essence through a relay, the pool takes the essence's colour and motes of it rise along the
 * bundle. Once grown it rests and nothing moves.
 * <p>
 * Crystal draws as cutout block quads, or translucent for a habit drawn see-through. The pool is one additive fan just above the floor, so it can't
 * fight it. At most one bundle and four shards, about 21 cached quads, per cradle, plus 40 for the motes of a fed one.
 */
public class CrystalCradleRender implements BlockEntityRenderer<CrystalCradleTile, CrystalCradleRender.State>
{
    private static final float FLOOR = CrystalCradleBlock.FLOOR / 16;

    // How much of its height a first bundle starts sunk into the bed
    private static final float SINK = 0.6F;

    private static final float     BED_SCALE = 0.42F;
    private static final float     BED_TILT  = 72;
    private static final float     BED_TINT  = 0.55F;
    private static final float     BED_DIP   = 0.4F / 16;
    // Where shards lie, in pixels, and which way each points: near the walls, clear of the bundle in the middle
    private static final float[][] BED_SPOTS = {
            { 4.6F, 4.4F, 35 }, { 11.4F, 4.9F, 125 }, { 11.1F, 11.6F, 215 }, { 4.9F, 11.2F, 300 } };

    private static final int   POOL_RGB      = 0x36C5B0;
    private static final float POOL_ALPHA    = 0.32F;
    private static final float POOL_RADIUS   = 5F / 16;
    private static final float POOL_HEIGHT   = 0.15F / 16;
    private static final int   POOL_SEGMENTS = 12;
    private static final float POOL_BREATH   = 0.2F;
    private static final float POOL_PERIOD   = 90;
    // Seep pools violet where ley pools teal, and a scouring cradle's floor is an ink drain blended over the stone so it darkens it
    private static final int   SEEP_RGB      = 0x7A3FD0;
    private static final float SEEP_ALPHA    = 0.3F;
    private static final int   INK_RGB       = 0x1A0F24;
    private static final float INK_ALPHA     = 0.75F;
    private static final float INK_STALLED   = 0.45F;
    private static final float INK_RADIUS    = 4F / 16;
    // Essence fed through a relay pools in its own colour, and motes of it climb the bundle
    private static final float FED_ALPHA     = 0.38F;
    private static final int   MOTES         = 5;
    private static final float MOTE_PERIOD   = 70;
    private static final float MOTE_RADIUS   = 0.028F;
    private static final float MOTE_ALPHA    = 0.85F;
    private static final float MOTE_ORBIT    = 2.6F / 16;
    private static final float MOTE_SPREAD   = 1.2F / 16;
    private static final float MOTE_TURN     = 0.6F;
    private static final float MOTE_LOWEST   = 4F / 16;
    // Void energy pools near-black violet, blended so it darkens the floor, and a Deep growth twinkles with stars in it
    private static final int   VOID_RGB      = 0x120A22;
    private static final float VOID_ALPHA    = 0.8F;
    private static final int   STAR_RGB      = 0xF2ECFF;
    private static final int   STARS         = 6;
    private static final float STAR_RADIUS   = 0.022F;
    private static final float STAR_ALPHA    = 0.95F;
    private static final float STAR_PERIOD   = 46;

    public static class State extends BlockEntityRenderState
    {
        @Nullable CrystalKind kind;
        @Nullable BundleSize  target;
        double       shown;
        int          waiting;
        float        lastShard;
        CrystalHabit habit = CrystalHabit.PLAIN;
        CrystalHabit seed  = CrystalHabit.PLAIN;
        float        pool;
        float        time;
        CrystalCradleTile.Source source = CrystalCradleTile.Source.IDLE;
        CrystalHabit feed = CrystalHabit.PLAIN;
        int          fedRgb;
        float        bundleTop;
        final Vector3f camera = new Vector3f();
    }

    public CrystalCradleRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CrystalCradleTile cradle, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(cradle, state, partialTicks, cameraPosition, breakProgress);
        var growth = cradle.growth();
        state.kind = growth.kind();
        state.target = growth.target();
        state.shown = cradle.shown(partialTicks);
        state.habit = cradle.preview();
        state.seed = growth.seedHabit();
        state.pool = cradle.pool(partialTicks);
        state.source = cradle.source();
        state.feed = cradle.feed();
        state.time = (cradle.getLevel().getGameTime() % 24000L) + partialTicks;
        var essence = CrystalCradleTile.essenceOf(cradle.feed());
        state.fedRgb = essence == null ? POOL_RGB : VoidBeamRender.aspectColor(essence) & 0xFFFFFF;
        var rise = CradleRise.of(state.shown, state.target);
        state.bundleTop = rise == null ? 0 : CradleRise.height(rise.size()) / 16 * rise.scale();
        var pos = cradle.getBlockPos();
        state.camera.set((float) (cameraPosition.x - pos.getX()), (float) (cameraPosition.y - pos.getY()), (float) (cameraPosition.z - pos.getZ()));

        // The shards not yet grown into the bundle, the one in use shrinking as it goes
        double left = Math.max(0, growth.worth() - state.shown);
        state.waiting = Math.min(BED_SPOTS.length, (int) Math.ceil(left - 1E-3));
        double part = left - Math.floor(left);
        state.lastShard = part < 1E-3 ? 1 : (float) part;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.kind == null)
            return;
        submitBed(state, poseStack, collector);
        submitBundle(state, poseStack, collector);
        if (state.pool <= 0.01F)
            return;
        switch (state.source)
        {
            case SCOURING, BREACHED -> collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) -> ink(state, pose, buffer));
            case FED -> collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
            {
                disc(state, pose, buffer, state.fedRgb, FED_ALPHA, POOL_RADIUS);
                motes(state, pose, buffer);
            });
            case VOID ->
            {
                collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) ->
                        fan(pose, buffer, ARGB.color(Mth.clamp((int) (VOID_ALPHA * state.pool * 255), 0, 255), VOID_RGB), ARGB.color(0, VOID_RGB), POOL_RADIUS));
                if (state.feed == CrystalHabit.DEEP)
                    collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> stars(state, pose, buffer));
            }
            case SEEP -> collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> disc(state, pose, buffer, SEEP_RGB, SEEP_ALPHA,
                    POOL_RADIUS));
            default -> collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> disc(state, pose, buffer, POOL_RGB, POOL_ALPHA,
                    POOL_RADIUS));
        }
    }

    private static void submitBundle(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var rise = CradleRise.of(state.shown, state.target);
        if (rise == null)
            return;
        var bundle = bundleState(state.kind, rise.size(), state.habit);
        float height = CradleRise.height(rise.size()) / 16 * rise.scale();

        poseStack.pushPose();
        poseStack.translate(0.5F, FLOOR - rise.sunk() * SINK * height, 0.5F);
        poseStack.scale(rise.scale(), rise.scale(), rise.scale());
        poseStack.translate(-0.5F, 0, -0.5F);
        submitQuads(poseStack, collector, bundle, state.habit, lit(state, bundle), 0xFFFFFFFF);
        poseStack.popPose();
    }

    private static void submitBed(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.waiting <= 0)
            return;
        var shard = bundleState(state.kind, BundleSize.SMALL, state.seed);
        int tint = ARGB.colorFromFloat(1F, BED_TINT, BED_TINT, BED_TINT);
        for (int i = 0; i < state.waiting; i++)
        {
            var spot = BED_SPOTS[i];
            // The last one is the shard being used up
            float scale = BED_SCALE * (i == state.waiting - 1 ? state.lastShard : 1);
            poseStack.pushPose();
            poseStack.translate(spot[0] / 16, FLOOR - BED_DIP, spot[1] / 16);
            poseStack.rotate(Axis.YP.rotationDegrees(spot[2]));
            poseStack.rotate(Axis.XP.rotationDegrees(BED_TILT));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5F, 0, -0.5F);
            submitQuads(poseStack, collector, shard, state.seed, lit(state, shard), tint);
            poseStack.popPose();
        }
    }

    private static BlockState bundleState(CrystalKind kind, BundleSize size, CrystalHabit habit)
    {
        return CrystalBundles.bundle(kind, size).defaultBlockState().setValue(CrystalBundleBlock.HABIT, habit);
    }

    /**
     * World light at the cradle, raised to the crystal's own glow as a placed bundle would be.
     */
    private static int lit(State state, BlockState crystal)
    {
        int block = Math.max(LightCoordsUtil.block(state.lightCoords), crystal.getLightEmission());
        return LightCoordsUtil.pack(block, LightCoordsUtil.sky(state.lightCoords));
    }

    private static void submitQuads(PoseStack poseStack, SubmitNodeCollector collector, BlockState crystal, CrystalHabit habit, int light, int color)
    {
        var instance = new QuadInstance();
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        instance.setLightCoords(light);
        instance.setColor(color);
        var quads = BlockQuads.of(crystal);
        collector.submitCustomGeometry(poseStack, renderType(habit), (pose, buffer) ->
        {
            for (var quad : quads)
                buffer.putBakedQuad(pose, quad, instance);
        });
    }

    /**
     * See-through habits keep their partial alpha, which cutout would round off.
     */
    private static RenderType renderType(CrystalHabit habit)
    {
        return habit == CrystalHabit.CLEAR ? RenderTypes.translucentMovingBlock() : RenderTypes.cutoutMovingBlock();
    }

    /**
     * The drain the foulness leaves by while scouring: dark in the middle, fading to the floor, steady while a breached line holds it back.
     */
    private static void ink(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float alpha = state.source == CrystalCradleTile.Source.BREACHED ? INK_STALLED : INK_ALPHA;
        int center = ARGB.color(Mth.clamp((int) (alpha * state.pool * 255), 0, 255), INK_RGB);
        int rim = ARGB.color(0, INK_RGB);
        fan(pose, buffer, center, rim, INK_RADIUS);
    }

    /**
     * A soft disc of what feeds the cradle at the bundle's foot, brightest in the middle and gone at its rim, breathing slowly while fed.
     */
    private static void disc(State state, PoseStack.Pose pose, VertexConsumer buffer, int rgb, float alpha, float radius)
    {
        float breath = 1 - POOL_BREATH * (0.5F + 0.5F * Mth.sin(state.time / POOL_PERIOD * Mth.TWO_PI));
        int center = ARGB.color(Mth.clamp((int) (alpha * state.pool * breath * 255), 0, 255), rgb);
        fan(pose, buffer, center, ARGB.color(0, rgb), radius);
    }

    /**
     * Motes of the fed essence climbing around the bundle from the pool to its tip, each on its own turn, fading in at the floor and out at the top.
     */
    private static void motes(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float top = FLOOR + Math.max(MOTE_LOWEST, state.bundleTop);
        for (int i = 0; i < MOTES; i++)
        {
            float phase = (state.time / MOTE_PERIOD + i / (float) MOTES + 0.37F * i * i) % 1;
            float angle = Mth.TWO_PI * (i / (float) MOTES + phase * MOTE_TURN);
            float orbit = MOTE_ORBIT + MOTE_SPREAD * Mth.sin(i * 2.3F);
            var at = new Vector3f(0.5F + orbit * Mth.cos(angle), Mth.lerp(phase, FLOOR + POOL_HEIGHT, top), 0.5F + orbit * Mth.sin(angle));
            float alpha = MOTE_ALPHA * state.pool * Mth.sin(phase * Mth.PI);
            dot(pose, buffer, at, state.camera, MOTE_RADIUS, state.fedRgb, alpha);
        }
    }

    /**
     * Star glints winking in a Deep pool, each at its own spot and on its own beat.
     */
    private static void stars(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        for (int i = 0; i < STARS; i++)
        {
            float angle = i * 2.39996F;
            float distance = POOL_RADIUS * (0.25F + 0.7F * ((i * 0.61803F) % 1));
            var at = new Vector3f(0.5F + distance * Mth.cos(angle), FLOOR + POOL_HEIGHT * 2, 0.5F + distance * Mth.sin(angle));
            float beat = Mth.sin((state.time / STAR_PERIOD + i * 0.37F) * Mth.TWO_PI);
            float alpha = STAR_ALPHA * state.pool * Math.max(0, beat) * Math.max(0, beat);
            dot(pose, buffer, at, state.camera, STAR_RADIUS, STAR_RGB, alpha);
        }
    }

    /**
     * A soft round dot facing the camera, given where the camera is from the cradle.
     */
    private static void dot(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Vector3f camera, float radius, int rgb, float alpha)
    {
        if (alpha <= 0.004F)
            return;
        var toCamera = new Vector3f(camera).sub(at);
        if (toCamera.lengthSquared() < 1.0E-6F)
            return;
        toCamera.normalize();
        var right = new Vector3f(0, 1, 0).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F)
            right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(toCamera).cross(right).normalize();
        int core = ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
        int rim = ARGB.color(0, rgb);
        int segments = 8;
        for (int i = 0; i < segments; i++)
        {
            float a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
            buffer.addVertex(pose, at.x, at.y, at.z).setColor(core);
            buffer.addVertex(pose, at.x, at.y, at.z).setColor(core);
            buffer.addVertex(pose, at.x + (right.x * Mth.cos(a0) + up.x * Mth.sin(a0)) * radius, at.y + (right.y * Mth.cos(a0) + up.y * Mth.sin(a0)) * radius,
                    at.z + (right.z * Mth.cos(a0) + up.z * Mth.sin(a0)) * radius).setColor(rim);
            buffer.addVertex(pose, at.x + (right.x * Mth.cos(a1) + up.x * Mth.sin(a1)) * radius, at.y + (right.y * Mth.cos(a1) + up.y * Mth.sin(a1)) * radius,
                    at.z + (right.z * Mth.cos(a1) + up.z * Mth.sin(a1)) * radius).setColor(rim);
        }
    }

    private static void fan(PoseStack.Pose pose, VertexConsumer buffer, int center, int rim, float radius)
    {
        float y = FLOOR + POOL_HEIGHT;
        for (int i = 0; i < POOL_SEGMENTS; i++)
        {
            float a0 = i * Mth.TWO_PI / POOL_SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / POOL_SEGMENTS;
            buffer.addVertex(pose, 0.5F, y, 0.5F).setColor(center);
            buffer.addVertex(pose, 0.5F + radius * Mth.cos(a1), y, 0.5F + radius * Mth.sin(a1)).setColor(rim);
            buffer.addVertex(pose, 0.5F + radius * Mth.cos(a0), y, 0.5F + radius * Mth.sin(a0)).setColor(rim);
            buffer.addVertex(pose, 0.5F, y, 0.5F).setColor(center);
        }
    }
}
