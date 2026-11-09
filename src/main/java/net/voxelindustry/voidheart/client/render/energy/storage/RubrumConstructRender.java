package net.voxelindustry.voidheart.client.render.energy.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.storage.ConstructBundles;
import net.voxelindustry.voidheart.common.energy.storage.ConstructFrameBlock;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.energy.storage.FrameGeometry;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBudding;
import net.voxelindustry.voidheart.common.energy.storage.RubrumConstructTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.storage.SocketBundle;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A rubrum construct's looks: the braces across its faces, the bundles on them pointing at the core, the veins on its vertical edges lit
 * from the bottom up with its fill, the core's inset glowing faintly, tiny arcs off the azurium bundles while energy
 * moves, the bud growing on its frame, and the ghost of its frame while the loose core shows it.
 * <p>
 * Bundles and the bud shard are block quads in the cutout layer, so they write depth like the blocks around them.
 * Veins, the inset, the speck and the arcs are additive {@link VoidHeartRenderTypes#GLOW}, which writes no depth and
 * blends the same in any order; the flat ones sit a hair off the faces they light so they never fight them.
 * At worst, a large construct with every socket full redraws about 300 bundle quads and a few dozen glow quads a
 * frame, from quads cached per block state.
 */
public class RubrumConstructRender implements BlockEntityRenderer<RubrumConstructTile, RubrumConstructRender.State>
{
    // Rubrum and azurium tones sampled from the bundle textures
    private static final int RUBRUM_LIGHT  = 0xFF5926;
    private static final int RUBRUM_GLOW   = 0xFF9238;
    private static final int AZURIUM_LIGHT = 0x9EECFF;
    private static final int AZURIUM_MID   = 0x3D7ED9;

    private static final float FACE_OFFSET = 0.004F;

    /** Vein strip inlaid at the middle of each outer face of the pillar beams. */
    private static final float VEIN_HALF_WIDTH = 1 / 16F;
    /** Height of the gold caps on the corners, which the vein runs between. */
    private static final float CAP_HEIGHT      = FrameGeometry.CAP / 16F;
    private static final int   VEIN_ALPHA      = 235;

    /** The rubrum inset on each face of the core. */
    private static final float INSET_MIN       = 5 / 16F;
    private static final float INSET_MAX       = 11 / 16F;
    private static final float INSET_MAX_ALPHA = 0.45F;

    private static final float REST_PERIOD     = 80;
    private static final float CHARGING_PERIOD = 30;
    /** Light and tint of a breathing bundle at the bottom of its breath; it reaches full bright at the top. */
    private static final int   BREATH_LOW_LIGHT = 8;
    private static final float BREATH_LOW_TINT  = 0.72F;
    private static final int   AZURIUM_LIGHT_LEVEL = 10;

    public static final  int   ARC_LIFE        = 4;
    private static final float ARC_LENGTH      = 0.5F;
    private static final int   ARC_JOINTS      = 4;
    private static final float ARC_JAG         = 0.06F;
    private static final float ARC_CORE_WIDTH  = 0.012F;
    private static final float ARC_HALO_WIDTH  = 0.035F;
    /** How far toward the core the tip of a bundle in a socket reaches. */
    private static final float BUNDLE_TIP      = 0.25F;

    private static final float SPECK_HALF = 1 / 16F;
    private static final float SHARD_SCALE = 0.55F;

    private static final int GHOST_COLOR    = ARGB.color(150, 255, 255, 255);
    private static final int CONFLICT_COLOR = ARGB.color(170, 255, 60, 60);

    public record BundleDraw(BlockPos offset, SocketBundle bundle, Direction inward, int socket, int lightCoords, float arcAge, int arcSeed)
    {
    }

    public record GhostDraw(BlockPos offset, BlockState state, boolean conflict)
    {
    }

    public static class State extends BlockEntityRenderState
    {
        public boolean          formed;
        public @Nullable Size   layout;
        public float            fill;
        public float            flow;
        public float            time;
        public final List<BundleDraw> bundles = new ArrayList<>();
        public @Nullable BlockPos budOffset;
        public Direction        budFace = Direction.NORTH;
        public RubrumBudding.Stage budStage = RubrumBudding.Stage.NONE;
        public int              budLight;
        public final List<GhostDraw> ghosts = new ArrayList<>();
        /** Light in the middle of each face, by direction, for its brace. */
        public final int[]      faceLights = new int[6];
    }

    public RubrumConstructRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(RubrumConstructTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var level = tile.getLevel();
        var time = level.getGameTime();
        state.formed = tile.isFormed();
        state.layout = tile.layout();
        state.fill = tile.displayFill(partialTicks);
        state.flow = tile.flow();
        state.time = time + partialTicks;

        state.bundles.clear();
        if (state.formed && state.layout != null)
        {
            for (var face : Direction.values())
                state.faceLights[face.ordinal()] = LightCoordsUtil.getLightCoords(level, tile.getBlockPos().relative(face, state.layout.half()));

            var sockets = ConstructLayout.sockets(state.layout);
            for (var socket = 0; socket < sockets.size(); socket++)
            {
                var bundle = tile.sockets().get(socket);
                if (bundle == null)
                    continue;
                var offset = sockets.get(socket);
                var light = LightCoordsUtil.getLightCoords(level, tile.getBlockPos().offset(offset));
                var arcAge = time - tile.arcStart(socket) + partialTicks;
                state.bundles.add(new BundleDraw(offset, bundle, ConstructLayout.inward(state.layout, offset), socket, light,
                        arcAge >= 0 && arcAge < ARC_LIFE ? arcAge : -1, tile.arcSeed(socket)));
            }
        }

        var bud = tile.budPos();
        state.budStage = tile.budding().stage();
        state.budOffset = state.formed && bud != null ? bud.subtract(tile.getBlockPos()) : null;
        state.budFace = tile.budFace();
        state.budLight = bud != null ? LightCoordsUtil.getLightCoords(level, bud) : 0;

        state.ghosts.clear();
        var preview = tile.previewSize();
        if (!state.formed && preview != null)
        {
            for (var offset : ConstructLayout.frame(preview))
            {
                var actual = level.getBlockState(tile.getBlockPos().offset(offset));
                if (actual.is(RubrumContent.RUBRUM_CONSTRUCT_FRAME))
                    continue;
                var ghost = RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState()
                        .setValue(ConstructFrameBlock.PART, ConstructLayout.partAt(preview, offset));
                state.ghosts.add(new GhostDraw(offset, ghost, !actual.canBeReplaced()));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        submitGhosts(state, poseStack, collector);
        if (!state.formed || state.layout == null)
            return;

        ConstructBraces.submit(poseStack, collector, state.layout, state.faceLights);
        submitBundles(state, poseStack, collector);
        submitBudShard(state, poseStack, collector);

        var cameraOffset = camera.pos.subtract(Vec3.atLowerCornerOf(state.blockPos));
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            veins(state, pose, buffer);
            inset(state, pose, buffer);
            speck(state, pose, buffer);
            arcs(state, pose, buffer, cameraOffset);
        });
    }

    //
    // Bundles
    //

    private static void submitBundles(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var charging = state.flow > 0.02F;
        var period = charging ? CHARGING_PERIOD : REST_PERIOD;
        for (var draw : state.bundles)
        {
            var instance = new QuadInstance();
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            var worldBlock = LightCoordsUtil.block(draw.lightCoords());
            var sky = LightCoordsUtil.sky(draw.lightCoords());
            if (draw.bundle().isRubrum())
            {
                // Each bundle keeps its own phase so a full construct never pulses in step
                var phase = (draw.socket() * 0.37F) % 1F;
                var wave = 0.5F - 0.5F * Mth.cos((state.time / period + phase) * Mth.TWO_PI);
                var breath = wave * wave;
                var block = Math.max(worldBlock, Math.round(Mth.lerp(breath, BREATH_LOW_LIGHT, 15)));
                instance.setLightCoords(LightCoordsUtil.pack(block, sky));
                var tint = Mth.lerp(breath, BREATH_LOW_TINT, 1F);
                instance.setColor(ARGB.colorFromFloat(1F, tint, tint, tint));
            }
            else
            {
                instance.setLightCoords(LightCoordsUtil.pack(Math.max(worldBlock, AZURIUM_LIGHT_LEVEL), sky));
                instance.setColor(0xFFFFFFFF);
            }

            var quads = BlockQuads.of(ConstructBundles.stateOf(draw.bundle(), draw.inward()));
            poseStack.pushPose();
            poseStack.translate(draw.offset().getX(), draw.offset().getY(), draw.offset().getZ());
            collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) ->
            {
                for (var quad : quads)
                    buffer.putBakedQuad(pose, quad, instance);
            });
            poseStack.popPose();
        }
    }

    //
    // Bud
    //

    private static void submitBudShard(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        if (state.budOffset == null || state.budStage == RubrumBudding.Stage.NONE || state.budStage == RubrumBudding.Stage.SPECK)
            return;

        var instance = new QuadInstance();
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        instance.setLightCoords(LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.budLight), 11), LightCoordsUtil.sky(state.budLight)));
        instance.setColor(0xFFFFFFFF);
        var quads = BlockQuads.of(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE.defaultBlockState().setValue(AmethystClusterBlock.FACING, state.budFace));

        // Shrinks toward the face it grows from, so it stays attached
        var anchor = faceCenter(state.budOffset, state.budFace.getOpposite());
        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y, anchor.z);
        poseStack.scale(SHARD_SCALE, SHARD_SCALE, SHARD_SCALE);
        // The model's own attachment point, at the middle of its face against the frame
        poseStack.translate(-(0.5F - state.budFace.getStepX() * 0.5F), -(0.5F - state.budFace.getStepY() * 0.5F), -(0.5F - state.budFace.getStepZ() * 0.5F));
        collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) ->
        {
            for (var quad : quads)
                buffer.putBakedQuad(pose, quad, instance);
        });
        poseStack.popPose();
    }

    /**
     * The center of a block's face, in the pose's block space.
     */
    private static Vec3 faceCenter(BlockPos offset, Direction face)
    {
        return Vec3.atCenterOf(offset).add(new Vec3(face.getUnitVec3i()).scale(0.5));
    }

    private static void speck(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        if (state.budOffset == null || state.budStage != RubrumBudding.Stage.SPECK)
            return;
        var center = faceCenter(state.budOffset, state.budFace.getOpposite()).add(new Vec3(state.budFace.getUnitVec3i()).scale(SPECK_HALF));
        var pulse = 0.8F + 0.2F * Mth.sin(state.time * 0.15F);
        var color = ARGB.color(Math.round(230 * pulse), RUBRUM_GLOW);
        for (var face : Direction.values())
            glowFace(pose, buffer, face, (float) center.x, (float) center.y, (float) center.z, SPECK_HALF, SPECK_HALF, color);
    }

    //
    // Veins and inset
    //

    private static void veins(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        if (state.fill <= 0.001F)
            return;
        var half = state.layout.half();
        var bottom = -half + CAP_HEIGHT;
        var span = state.layout.edge() - 2 * CAP_HEIGHT;
        var top = bottom + span * Mth.clamp(state.fill, 0F, 1F);
        var color = ARGB.color(VEIN_ALPHA, RUBRUM_LIGHT);

        for (var sx = -1; sx <= 1; sx += 2)
        {
            for (var sz = -1; sz <= 1; sz += 2)
            {
                // The middle of the beam hugging this edge, on each of its outer faces
                var columnX = sx * half + FrameGeometry.veinCenter(sx);
                var columnZ = sz * half + FrameGeometry.veinCenter(sz);
                // The face looking out along x, then the one looking out along z
                var faceX = sx > 0 ? half + 1 + FACE_OFFSET : -half - FACE_OFFSET;
                strip(pose, buffer, Direction.Axis.X, faceX, columnZ, bottom, top, sx > 0 ? Direction.EAST : Direction.WEST, color);
                var faceZ = sz > 0 ? half + 1 + FACE_OFFSET : -half - FACE_OFFSET;
                strip(pose, buffer, Direction.Axis.Z, faceZ, columnX, bottom, top, sz > 0 ? Direction.SOUTH : Direction.NORTH, color);
            }
        }
    }

    /**
     * A vertical strip on a face perpendicular to the axis, at the given plane, centered across it.
     */
    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Direction.Axis axis, float plane, float across, float bottom, float top,
                              Direction facing, int color)
    {
        float a0 = across - VEIN_HALF_WIDTH, a1 = across + VEIN_HALF_WIDTH;
        if (axis == Direction.Axis.X)
            quad(pose, buffer, color, facing, plane, bottom, a0, plane, bottom, a1, plane, top, a1, plane, top, a0);
        else
            quad(pose, buffer, color, facing, a0, bottom, plane, a1, bottom, plane, a1, top, plane, a0, top, plane);
    }

    private static void inset(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        if (state.fill <= 0.01F)
            return;
        var color = ARGB.color(Math.round(255 * INSET_MAX_ALPHA * Mth.clamp(state.fill, 0F, 1F)), RUBRUM_GLOW);
        var halfSize = (INSET_MAX - INSET_MIN) / 2;
        for (var face : Direction.values())
            glowFace(pose, buffer, face, 0.5F, 0.5F, 0.5F, 0.5F + FACE_OFFSET, halfSize, color);
    }

    /**
     * A square on one face of a cube centered at (cx, cy, cz), {@code reach} from the center, {@code halfSize} across.
     */
    private static void glowFace(PoseStack.Pose pose, VertexConsumer buffer, Direction face, float cx, float cy, float cz, float reach, float halfSize, int color)
    {
        var normal = face.getUnitVec3i();
        float px = cx + normal.getX() * reach, py = cy + normal.getY() * reach, pz = cz + normal.getZ() * reach;
        // Two axes across the face
        var u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        var v = new Vector3f(normal.getX(), normal.getY(), normal.getZ()).cross(u);
        u.mul(halfSize);
        v.mul(halfSize);
        quad(pose, buffer, color, face,
                px - u.x - v.x, py - u.y - v.y, pz - u.z - v.z,
                px + u.x - v.x, py + u.y - v.y, pz + u.z - v.z,
                px + u.x + v.x, py + u.y + v.y, pz + u.z + v.z,
                px - u.x + v.x, py - u.y + v.y, pz - u.z + v.z);
    }

    //
    // Arcs
    //

    /**
     * Tiny leaping arcs off each azurium bundle: inward toward the core while charging, outward while discharging, a
     * brighter stretch running along each the way the energy goes.
     */
    private static void arcs(State state, PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera)
    {
        if (Math.abs(state.flow) < 0.02F)
            return;
        var charging = state.flow > 0;
        for (var draw : state.bundles)
        {
            if (draw.bundle().isRubrum() || draw.arcAge() < 0)
                continue;

            var inward = new Vec3(draw.inward().getUnitVec3i());
            var tip = Vec3.atCenterOf(draw.offset()).add(inward.scale(BUNDLE_TIP));
            var far = tip.add(inward.scale(ARC_LENGTH));
            var from = charging ? tip : far;
            var to = charging ? far : tip;

            var life = draw.arcAge() / ARC_LIFE;
            var fade = 1 - life * life;
            // Re-jags every other tick
            var random = new java.util.Random(draw.arcSeed() * 31L + (long) (draw.arcAge() / 2));
            var points = new Vec3[ARC_JOINTS + 1];
            var side = inward.cross(Math.abs(inward.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            var up = inward.cross(side).normalize();
            for (var joint = 0; joint <= ARC_JOINTS; joint++)
            {
                var t = joint / (float) ARC_JOINTS;
                var point = from.lerp(to, t);
                if (joint > 0 && joint < ARC_JOINTS)
                    point = point.add(side.scale((random.nextFloat() * 2 - 1) * ARC_JAG)).add(up.scale((random.nextFloat() * 2 - 1) * ARC_JAG));
                points[joint] = point;
            }

            for (var joint = 0; joint < ARC_JOINTS; joint++)
            {
                var t = (joint + 0.5F) / ARC_JOINTS;
                // The leap: brightest where the travelling stretch is
                var leap = 1 - Mth.clamp(Math.abs(t - life * 1.2F) * 3, 0, 1);
                var halo = ARGB.color(Math.round((70 + 60 * leap) * fade), AZURIUM_MID);
                var core = ARGB.color(Math.round((170 + 85 * leap) * fade), AZURIUM_LIGHT);
                ribbon(pose, buffer, points[joint], points[joint + 1], camera, ARC_HALO_WIDTH, halo);
                ribbon(pose, buffer, points[joint], points[joint + 1], camera, ARC_CORE_WIDTH, core);
            }
        }
    }

    /**
     * A flat band from a to b turned toward the camera, given in the pose's block space.
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 camera, float halfWidth, int color)
    {
        var along = b.subtract(a);
        var toCamera = camera.subtract(a.add(b).scale(0.5));
        var side = along.cross(toCamera);
        if (side.lengthSqr() < 1.0E-8)
            return;
        side = side.normalize().scale(halfWidth);
        buffer.addVertex(pose, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(color);
        buffer.addVertex(pose, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(color);
        buffer.addVertex(pose, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(color);
        buffer.addVertex(pose, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, Direction facing,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz)
    {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }

    //
    // Preview
    //

    /**
     * The frame blocks still missing, as ghosts of the beams they become, which are thin enough not to hide the rest;
     * red where something else stands.
     */
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
            var quads = BlockQuads.of(draw.state());
            var instance = draw.conflict() ? conflict : ghost;
            poseStack.pushPose();
            poseStack.translate(draw.offset().getX(), draw.offset().getY(), draw.offset().getZ());
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
            {
                for (var quad : quads)
                    buffer.putBakedQuad(pose, quad, instance);
            });
            poseStack.popPose();
        }
    }

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
