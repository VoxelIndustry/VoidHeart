package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePayloads;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Mark;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Plan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import net.voxelindustry.voidheart.common.content.wraithveil.WraithVeilItem;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What the Wraith Veil shows its bearer. Aimed at a surface it can cross, a faint mark where it would open. While the tunnel is open, the blocks it
 * crosses become a realm of rolling ink smoke, open at both ends through the holes the marks open, so the rooms on either side show through
 * while the stone around stays hidden. Embers peel off each hole's edge and drift into the tunnel. As it collapses its walls close in and the
 * holes seal from their edges inward.
 * <p>
 * The walls use {@link VoidHeartRenderTypes#shadowRealm} and the mouths {@link VoidHeartRenderTypes#wraithMouth}, which both write depth so that
 * mobs behind them are hidden and only show as Insight silhouettes. A mouth reads the same mask as its mark, see {@link WraithInk}, so the hole
 * has one edge from both sides. Wisps over the walls are blended without depth, and motes and embers glow additively. They lie a hair apart,
 * inside the blocks crossed, so none fights the stone faces. Per frame: the walls split where their texture wraps, about 8 quads per block crossed
 * for walls and wisps, two mouths, four motes per block and a dozen embers a mouth, and only while a tunnel is open.
 */
public final class WallPassageRender
{
    private static final Identifier SMOKE    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wall_passage_smoke.png");
    private static final Identifier WISPS    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wall_passage_wisps.png");
    private static final Identifier LIP      = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wall_passage_lip.png");

    private static final RenderType WALL_TYPE  = VoidHeartRenderTypes.shadowRealm(SMOKE);
    private static final RenderType WISP_TYPE  = RenderTypes.entityTranslucentEmissive(WISPS);
    private static final RenderType LIP_TYPE   = RenderTypes.entityTranslucentEmissive(LIP);

    // Ink lining each mouth, as if the mark sank into the wall: it reaches this deep, streaming inward, and fades out as it goes
    private static final float LIP_DEPTH       = 0.6F;
    private static final float LIP_LENGTH      = 0.5F;
    private static final float LIP_WIDTH       = 1;
    private static final float LIP_SPEED       = 0.02F;
    private static final int   LIP_ALPHA       = 235;
    private static final float LIP_INSET       = 0.008F;

    // Smoke: one texture repeat per this many blocks along the tunnel and across a wall, rolling toward the far mouth
    private static final float SMOKE_LENGTH    = 2.5F;
    private static final float SMOKE_WIDTH     = 2;
    private static final float SMOKE_SPEED     = 0.012F;
    private static final float WISP_LENGTH     = 1.6F;
    private static final float WISP_WIDTH      = 1.2F;
    private static final float WISP_SPEED      = 0.03F;
    private static final int   WISP_ALPHA      = 190;
    private static final int   MOTES_PER_BLOCK = 4;
    private static final float MOTE_SIZE       = 0.025F;
    private static final int   MOTE_COLOR      = 0x8A3DF0;
    private static final int   MOTE_ALPHA      = 90;
    private static final float MOTE_SPEED      = 0.02F;
    private static final float MOTE_MARGIN     = 0.15F;

    // Embers off each hole's edge: they start this far out in front of the mark and drift this far in, drawn toward the middle and swirling
    private static final int   EMBERS          = 12;
    private static final float EMBER_SIZE      = 0.016F;
    private static final float EMBER_NEAR      = 0.6F;
    private static final float EMBER_NEAR_FADE = 1.2F;
    private static final int   EMBER_COLOR     = 0xB58AF5;
    private static final int   EMBER_ALPHA     = 170;
    private static final float EMBER_START     = 0.35F;
    private static final float EMBER_TRAVEL    = 1.6F;
    private static final float EMBER_GATHER    = 0.45F;
    private static final float EMBER_SWIRL     = 1.2F;
    private static final int   EMBER_LIFE      = 34;
    // How brightly the hole's edge smoulders from inside
    private static final float MOUTH_GLOW      = 0.7F;
    private static final float WALL_INSET      = 0.004F;
    private static final float WISP_INSET      = 0.012F;
    private static final float MOUTH_INSET     = 0.008F;
    private static final int   OPEN_FADE_TICKS = 4;
    // A pinch goes in steps, like carved blocks: the tunnel is cut into slices this long, each drawn in by whole steps of this size
    private static final float PINCH_SLICE     = 0.125F;
    private static final float PINCH_STEP      = 0.0625F;

    // The preview traces the outline the ink would reach as a dim glowing front, breathing in and out over it
    private static final float  PREVIEW_SPREAD      = 0.85F;
    private static final float  PREVIEW_GLOW        = 0.4F;
    private static final float  PREVIEW_PULSE_RATE  = 0.15F;
    private static final float  PREVIEW_PULSE_RANGE = 0.03F;
    // Above the real mark's gap, so the preview never shares its plane
    private static final double PREVIEW_GAP         = 0.015;

    private WallPassageRender()
    {
    }

    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(WallPassagePayloads.Begin.TYPE, (payload, context) -> WallPassages.beginClient(context.player(), payload.plan()));
        ClientPlayNetworking.registerGlobalReceiver(WallPassagePayloads.End.TYPE, (payload, context) -> WallPassages.endClient(context.player()));
        LevelRenderEvents.COLLECT_SUBMITS.register(WallPassageRender::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null)
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        float time = levelState.gameTime + partialTicks;

        var passage = WallPassages.passageOf(player);
        if (passage != null)
        {
            float ticks = WallPassages.ticksOf(player) + partialTicks;
            float shown = Mth.clamp((ticks - WallPassagePlan.MARK_TICKS) / OPEN_FADE_TICKS, 0, 1);
            if (shown <= 0 || ticks > Plan.totalTicks() + 1)
                return;
            var box = passage.realm();
            WallShape shape = along -> Plan.closure(ticks, fromMiddle(passage, along));
            var collector = context.submitNodeCollector();
            var poseStack = context.poseStack();
            float open = Plan.holeOpen(ticks);
            // From outside, the realm shows through the marks' holes, since the client has erased the tunnel's blocks; each piece of wall is
            // only drawn where a block behind it hides it, so none of it shows anywhere else
            boolean inside = passage.realm().contains(camera);
            var level = minecraft.level;
            // A piece is hidden from outside by the block past it, or by the redrawn face of the erased block it lines
            BiPredicate<BlockPos, BlockPos> drawn = inside ? (beyond, lined) -> true
                                                           : (beyond, lined) -> level.getBlockState(beyond).canOcclude() || !WallPassages.trueState(level, lined).isAir();
            var shownBlocks = WallPassages.shownFromOutside(player);
            if (!shownBlocks.isEmpty())
            {
                collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) -> covers(pose, buffer, minecraft, shownBlocks, camera, false));
                collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> covers(pose, buffer, minecraft, shownBlocks, camera, true));
            }
            var axis = passage.into().getAxis();
            double from = axis.choose(box.minX, box.minY, box.minZ), to = axis.choose(box.maxX, box.maxY, box.maxZ);
            float flow = passage.into().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;

            collector.submitCustomGeometry(poseStack, WALL_TYPE, (pose, buffer) ->
            {
                int color = ARGB.color((int) (255 * shown), 0xFFFFFF);
                walls(pose, buffer, passage, box, camera, WALL_INSET, SMOKE_LENGTH, SMOKE_WIDTH, -time * SMOKE_SPEED * flow, from, to, color, color, drawn, shape);
                steps(pose, buffer, passage, box, camera, from, to, color, shape);
            });
            int mouthColor = WraithInk.color(0, open, MOUTH_GLOW, WraithInk.Mode.MARK);
            for (var mark : new Mark[]{passage.entry(), passage.exit()})
            {
                var variant = variantOf(mark);
                collector.submitCustomGeometry(poseStack, variant.mouth(), (pose, buffer) -> mouth(pose, buffer, mark, variant, box, camera, mouthColor));
            }
            collector.submitCustomGeometry(poseStack, LIP_TYPE, (pose, buffer) ->
            {
                for (var mark : new Mark[]{passage.entry(), passage.exit()})
                {
                    double plane = axis.choose(mark.center().x, mark.center().y, mark.center().z);
                    int ink = ARGB.color((int) (LIP_ALPHA * shown), 0xFFFFFF), none = ARGB.color(0, 0xFFFFFF);
                    // Streaming away from the mouth, into the wall
                    if (Math.abs(plane - from) < Math.abs(plane - to))
                        walls(pose, buffer, passage, box, camera, LIP_INSET, LIP_LENGTH, LIP_WIDTH, -time * LIP_SPEED, from, Math.min(to, from + LIP_DEPTH), ink, none, drawn,
                                shape);
                    else
                        walls(pose, buffer, passage, box, camera, LIP_INSET, LIP_LENGTH, LIP_WIDTH, time * LIP_SPEED, Math.max(from, to - LIP_DEPTH), to, none, ink, drawn,
                                shape);
                }
            });
            collector.submitCustomGeometry(poseStack, WISP_TYPE, (pose, buffer) -> walls(pose, buffer, passage, box, camera, WISP_INSET, WISP_LENGTH, WISP_WIDTH,
                    -time * WISP_SPEED * flow, from, to, ARGB.color((int) (WISP_ALPHA * shown), 0xFFFFFF), ARGB.color((int) (WISP_ALPHA * shown), 0xFFFFFF), drawn, shape));
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
            {
                if (inside)
                    motes(pose, buffer, passage, box, camera, time, shown, shape);
                embers(pose, buffer, passage.entry(), camera, time, open, 0);
                embers(pose, buffer, passage.exit(), camera, time, open, EMBERS);
            });
            return;
        }

        var preview = previewPlan(minecraft, player);
        if (preview == null)
            return;
        var mark = preview.entry();
        var normal = mark.face().getUnitVec3();
        var at = mark.center().add(normal.scale(PREVIEW_GAP)).subtract(camera);
        float spread = PREVIEW_SPREAD + PREVIEW_PULSE_RANGE * Mth.sin(time * PREVIEW_PULSE_RATE);
        var poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate((float) at.x, (float) at.y, (float) at.z);
        WraithInk.submit(poseStack, context.submitNodeCollector(), mark.face(), (float) mark.halfU(), (float) mark.halfV(), variantOf(mark),
                WraithInk.color(spread, 0, PREVIEW_GLOW, WraithInk.Mode.PREVIEW), LightCoordsUtil.FULL_BRIGHT,
                WraithInk.holding(minecraft.level, mark.face(), mark.center(), (float) mark.halfU(), (float) mark.halfV()));
        poseStack.popPose();
    }

    private static @Nullable Plan previewPlan(Minecraft minecraft, Player player)
    {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !WallPassages.canStart(player))
            return null;
        for (var hand : InteractionHand.values())
        {
            var stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WraithVeilItem)
            {
                if (player.getCooldowns().isOnCooldown(stack))
                    return null;
                return WallPassagePlan.plan(WallPassages.cells(player.level(), player), hit.getBlockPos(), hit.getDirection(), player.position());
            }
        }
        return null;
    }

    /**
     * How shut the tunnel is at a point along it, 0 open to 1 shut, so its walls draw in toward its middle line there.
     */
    @FunctionalInterface
    private interface WallShape
    {
        float closure(double along);
    }

    /**
     * @return 0 at the middle of the realm along the tunnel, 1 at either mark
     */
    private static float fromMiddle(Plan plan, double along)
    {
        var realm = plan.realm();
        var axis = plan.into().getAxis();
        double from = axis.choose(realm.minX, realm.minY, realm.minZ), to = axis.choose(realm.maxX, realm.maxY, realm.maxZ);
        double half = Math.max(1e-6, (to - from) / 2);
        return (float) Mth.clamp(Math.abs(along - (from + to) / 2) / half, 0, 1);
    }

    /**
     * @return the tunnel's cross-section in the slice holding this point along it, drawn in by whole {@link #PINCH_STEP}s on each side by how
     * shut the tunnel is at the slice's middle, or null where it is shut
     */
    private static @Nullable AABB slice(AABB box, Direction.Axis axis, double along, WallShape shape)
    {
        double middle = (Mth.floor(along / PINCH_SLICE) + 0.5) * PINCH_SLICE;
        float shut = shape.closure(middle);
        double[] inset = new double[3];
        for (var lateral : Direction.Axis.values())
        {
            if (lateral == axis)
                continue;
            double half = lateral.choose(box.getXsize(), box.getYsize(), box.getZsize()) / 2;
            inset[lateral.ordinal()] = Math.min(half, Math.round(shut * half / PINCH_STEP) * PINCH_STEP);
            if (inset[lateral.ordinal()] >= half - 1e-6)
                return null;
        }
        return box.deflate(inset[0], inset[1], inset[2]);
    }

    private static double nextSlice(double along)
    {
        return (Mth.floor(along / PINCH_SLICE + 1e-6) + 1) * PINCH_SLICE;
    }

    /**
     * The four walls along a stretch of the tunnel, cut wherever their texture wraps, at every block, so each piece lies in front of one block,
     * and at every {@link #PINCH_SLICE}, so where the tunnel pinches its walls step in like carved blocks. u runs along the tunnel, offset by
     * flow, and v across each wall, kept from the open shape so the smoke squeezes with it. The color goes from one end of the stretch to the
     * other, and a piece is only drawn when the block behind it passes the test.
     */
    private static void walls(PoseStack.Pose pose, VertexConsumer buffer, Plan plan, AABB box, Vec3 camera, float inset, float length, float width, float flow,
                              double from, double to, int colorFrom, int colorTo, BiPredicate<BlockPos, BlockPos> drawn, WallShape shape)
    {
        var axis = plan.into().getAxis();
        var inner = box.deflate(axis == Direction.Axis.X ? 0 : inset, axis == Direction.Axis.Y ? 0 : inset, axis == Direction.Axis.Z ? 0 : inset);
        var whole = plan.box();
        var middle = inner.getCenter();
        double span = Math.max(1e-6, to - from);

        for (var side : Direction.values())
        {
            if (side.getAxis() == axis)
                continue;
            var across = acrossAxis(axis, side.getAxis());
            double a0 = across.choose(inner.minX, inner.minY, inner.minZ), a1 = across.choose(inner.maxX, inner.maxY, inner.maxZ);
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            // The block just past this wall, in the tunnel's full size
            int beyond = positive ? (int) Math.round(side.getAxis().choose(whole.maxX, whole.maxY, whole.maxZ))
                                  : (int) Math.round(side.getAxis().choose(whole.minX, whole.minY, whole.minZ)) - 1;
            var normal = side.getOpposite().getUnitVec3f();
            double start = from;
            while (start < to - 1e-6)
            {
                float u = (float) (start / length) + flow;
                float uFrom = u - Mth.floor(u);
                double end = Math.min(Math.min(to, start + (1 - uFrom) * length), Math.min(Mth.floor(start + 1e-6) + 1, nextSlice(start)));
                float uTo = (float) (uFrom + (end - start) / length);
                var cut = slice(inner, axis, (start + end) / 2, shape);
                if (cut != null)
                {
                    double plane = positive ? side.getAxis().choose(cut.maxX, cut.maxY, cut.maxZ) : side.getAxis().choose(cut.minX, cut.minY, cut.minZ);
                    double centre = across.choose(middle.x, middle.y, middle.z);
                    double scale = across.choose(cut.getXsize(), cut.getYsize(), cut.getZsize()) / Math.max(1e-6, a1 - a0);
                    var wall = new Wall(axis, across, side.getAxis(), plane, centre, scale, beyond, positive, normal);
                    int c0 = ARGB.srgbLerp((float) ((start - from) / span), colorFrom, colorTo), c1 = ARGB.srgbLerp((float) ((end - from) / span), colorFrom, colorTo);
                    acrossWall(pose, buffer, wall, start, end, a0, a1, uFrom, uTo, width, camera, c0, c1, drawn);
                }
                start = end;
            }
        }
    }

    /**
     * The flat faces where the walls step in between two slices, closing each step like the side of a carved block.
     */
    private static void steps(PoseStack.Pose pose, VertexConsumer buffer, Plan plan, AABB box, Vec3 camera, double from, double to, int color, WallShape shape)
    {
        var axis = plan.into().getAxis();
        var inner = box.deflate(axis == Direction.Axis.X ? 0 : WALL_INSET, axis == Direction.Axis.Y ? 0 : WALL_INSET, axis == Direction.Axis.Z ? 0 : WALL_INSET);
        Direction.Axis first = null, second = null;
        for (var lateral : Direction.Axis.values())
            if (lateral != axis)
            {
                if (first == null)
                    first = lateral;
                else
                    second = lateral;
            }
        var normal = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE).getUnitVec3f();
        for (double at = nextSlice(from); at < to - 1e-6; at = nextSlice(at))
        {
            var before = slice(inner, axis, at - PINCH_SLICE / 2, shape);
            var after = slice(inner, axis, at + PINCH_SLICE / 2, shape);
            if (before == null && after == null || before != null && before.equals(after))
                continue;
            // The bigger of the two cross-sections, less the smaller one
            var outer = before == null ? after : after == null ? before : area(before, axis) >= area(after, axis) ? before : after;
            var hole = outer == before ? after : before;
            double o0 = first.choose(outer.minX, outer.minY, outer.minZ), o1 = first.choose(outer.maxX, outer.maxY, outer.maxZ);
            double p0 = second.choose(outer.minX, outer.minY, outer.minZ), p1 = second.choose(outer.maxX, outer.maxY, outer.maxZ);
            if (hole == null)
            {
                stepQuad(pose, buffer, axis, first, second, at, o0, o1, p0, p1, camera, color, normal);
                continue;
            }
            double h0 = first.choose(hole.minX, hole.minY, hole.minZ), h1 = first.choose(hole.maxX, hole.maxY, hole.maxZ);
            double q0 = second.choose(hole.minX, hole.minY, hole.minZ), q1 = second.choose(hole.maxX, hole.maxY, hole.maxZ);
            stepQuad(pose, buffer, axis, first, second, at, o0, o1, p0, q0, camera, color, normal);
            stepQuad(pose, buffer, axis, first, second, at, o0, o1, q1, p1, camera, color, normal);
            stepQuad(pose, buffer, axis, first, second, at, o0, h0, q0, q1, camera, color, normal);
            stepQuad(pose, buffer, axis, first, second, at, h1, o1, q0, q1, camera, color, normal);
        }
    }

    private static double area(AABB box, Direction.Axis axis)
    {
        return box.getXsize() * box.getYsize() * box.getZsize() / Math.max(1e-6, axis.choose(box.getXsize(), box.getYsize(), box.getZsize()));
    }

    private static void stepQuad(PoseStack.Pose pose, VertexConsumer buffer, Direction.Axis along, Direction.Axis first, Direction.Axis second, double at, double f0,
                                 double f1, double g0, double g1, Vec3 camera, int color, org.joml.Vector3fc n)
    {
        if (f1 - f0 < 1e-6 || g1 - g0 < 1e-6)
            return;
        float u0 = (float) Mth.frac(f0 / SMOKE_WIDTH), u1 = u0 + (float) ((f1 - f0) / SMOKE_WIDTH);
        float v0 = (float) Mth.frac(g0 / SMOKE_WIDTH), v1 = v0 + (float) ((g1 - g0) / SMOKE_WIDTH);
        vertex(pose, buffer, point(along, first, at, f0, second, g0), camera, u0, v0, color, n.x(), n.y(), n.z());
        vertex(pose, buffer, point(along, first, at, f1, second, g0), camera, u1, v0, color, n.x(), n.y(), n.z());
        vertex(pose, buffer, point(along, first, at, f1, second, g1), camera, u1, v1, color, n.x(), n.y(), n.z());
        vertex(pose, buffer, point(along, first, at, f0, second, g1), camera, u0, v1, color, n.x(), n.y(), n.z());
    }

    private static Vec3 point(Direction.Axis along, Direction.Axis first, double s, double f, Direction.Axis second, double g)
    {
        double[] at = new double[3];
        at[along.ordinal()] = s;
        at[first.ordinal()] = f;
        at[second.ordinal()] = g;
        return new Vec3(at[0], at[1], at[2]);
    }

    /**
     * One wall of the tunnel within a slice: its plane, how its width is drawn in toward the middle line across it, and the block coordinate just
     * past it in the open shape.
     */
    private record Wall(Direction.Axis along, Direction.Axis across, Direction.Axis normalAxis, double plane, double acrossMiddle, double scale, int beyond,
                        boolean positive, org.joml.Vector3fc normal)
    {
        Vec3 at(double s, double a)
        {
            return point(along, across, s, acrossMiddle + (a - acrossMiddle) * scale, plane);
        }
    }

    /**
     * One stretch of a wall, cut again across it wherever v wraps and at every block of the open shape.
     */
    private static void acrossWall(PoseStack.Pose pose, VertexConsumer buffer, Wall wall, double s0, double s1, double a0, double a1, float u0, float u1, float width,
                                   Vec3 camera, int c0, int c1, BiPredicate<BlockPos, BlockPos> drawn)
    {
        int alongBlock = Mth.floor((s0 + s1) / 2);
        var n = wall.normal();
        double start = a0;
        while (start < a1 - 1e-6)
        {
            float v = (float) (start / width);
            float vFrom = v - Mth.floor(v);
            double end = Math.min(Math.min(a1, start + (1 - vFrom) * width), Mth.floor(start + 1e-6) + 1);
            float vTo = (float) (vFrom + (end - start) / width);
            int acrossBlock = Mth.floor((start + end) / 2);
            var beyond = blockAt(wall.along(), alongBlock, wall.across(), acrossBlock, wall.normalAxis(), wall.beyond());
            var lined = blockAt(wall.along(), alongBlock, wall.across(), acrossBlock, wall.normalAxis(), wall.beyond() + (wall.positive() ? -1 : 1));
            if (drawn.test(beyond, lined))
            {
                vertex(pose, buffer, wall.at(s0, start), camera, u0, vFrom, c0, n.x(), n.y(), n.z());
                vertex(pose, buffer, wall.at(s1, start), camera, u1, vFrom, c1, n.x(), n.y(), n.z());
                vertex(pose, buffer, wall.at(s1, end), camera, u1, vTo, c1, n.x(), n.y(), n.z());
                vertex(pose, buffer, wall.at(s0, end), camera, u0, vTo, c0, n.x(), n.y(), n.z());
            }
            start = end;
        }
    }

    /**
     * Draws again the faces of erased blocks that show from outside, through the game's own block renderer, so they keep their model, tint and
     * light, one layer at a time.
     */
    private static void covers(PoseStack.Pose pose, VertexConsumer buffer, Minecraft minecraft, List<WallPassages.Shown> shown, Vec3 camera, boolean translucent)
    {
        var renderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
        var models = minecraft.getModelManager().getBlockStateModelSet();
        var stack = new PoseStack();
        stack.mulPose(pose.pose());
        for (var block : shown)
        {
            var pos = block.pos();
            BlockQuadOutput output = (x, y, z, quad, instance) ->
            {
                if (quad.direction() == null || !block.sides().contains(quad.direction()) || quad.materialInfo().layer().translucent() != translucent)
                    return;
                stack.pushPose();
                stack.translate(x, y, z);
                buffer.putBakedQuad(stack.last(), quad, instance);
                stack.popPose();
            };
            renderer.tesselateBlock(output, (float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y), (float) (pos.getZ() - camera.z), minecraft.level, pos,
                    block.state(), models.get(block.state()), block.state().getSeed(pos));
        }
    }

    private static BlockPos blockAt(Direction.Axis along, int alongBlock, Direction.Axis across, int acrossBlock, Direction.Axis wall, int wallBlock)
    {
        int[] at = new int[3];
        at[along.ordinal()] = alongBlock;
        at[across.ordinal()] = acrossBlock;
        at[wall.ordinal()] = wallBlock;
        return new BlockPos(at[0], at[1], at[2]);
    }

    /**
     * @return the axis across a wall: of the two besides the tunnel's, the one that isn't the wall's own
     */
    private static Direction.Axis acrossAxis(Direction.Axis along, Direction.Axis wall)
    {
        for (var axis : Direction.Axis.values())
            if (axis != along && axis != wall)
                return axis;
        throw new IllegalStateException();
    }

    private static Vec3 point(Direction.Axis along, Direction.Axis across, double s, double a, double plane)
    {
        double x = along == Direction.Axis.X ? s : across == Direction.Axis.X ? a : plane;
        double y = along == Direction.Axis.Y ? s : across == Direction.Axis.Y ? a : plane;
        double z = along == Direction.Axis.Z ? s : across == Direction.Axis.Z ? a : plane;
        return new Vec3(x, y, z);
    }

    private static WraithInk.Variant variantOf(Mark mark)
    {
        return WraithInk.variant(mark.face(), mark.center(), (float) mark.halfU(), (float) mark.halfV());
    }

    /**
     * Closes a tunnel's end but for the hole its mark has opened, mapped exactly as the mark is so the two edges meet. The blocks the tunnel
     * crosses are erased from the client's world while it is open, so a block just beyond a mouth, such as the step a raised tunnel comes out
     * on, shows its face through the hole.
     */
    private static void mouth(PoseStack.Pose pose, VertexConsumer buffer, Mark mark, WraithInk.Variant variant, AABB box, Vec3 camera, int color)
    {
        var face = mark.face();
        var center = mark.center().subtract(face.getUnitVec3().scale(MOUTH_INSET));
        float[] min = surfaceOffset(face, new Vec3(box.minX, box.minY, box.minZ).subtract(center));
        float[] max = surfaceOffset(face, new Vec3(box.maxX, box.maxY, box.maxZ).subtract(center));
        mouthBand(pose, buffer, mark, variant, center, camera, min[0], max[0], min[1], max[1], color);
    }

    private static void mouthBand(PoseStack.Pose pose, VertexConsumer buffer, Mark mark, WraithInk.Variant variant, Vec3 center, Vec3 camera,
                                  float u0, float u1, float v0, float v1, int color)
    {
        var face = mark.face();
        var n = face.getOpposite().getUnitVec3f();
        float halfU = (float) mark.halfU(), halfV = (float) mark.halfV();
        float[][] corners = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (var corner : corners)
        {
            var offset = WraithInk.onSurface(face, corner[0], corner[1]);
            var uv = variant.uv(corner[0], corner[1], halfU, halfV);
            vertex(pose, buffer, center.add(offset[0], offset[1], offset[2]), camera, uv[0], uv[1], color, n.x(), n.y(), n.z());
        }
    }

    /**
     * Embers peeling off a hole's edge and drifting in through it, each on its own path and age so they never move in step.
     */
    private static void embers(PoseStack.Pose pose, VertexConsumer buffer, Mark mark, Vec3 camera, float time, float open, int salt)
    {
        if (open <= 0)
            return;
        var face = mark.face();
        var inward = face.getOpposite().getUnitVec3();
        // About where the mouth's own edge is
        float edgeU = (float) (mark.halfU() * WallPassagePlan.MARK_COVER), edgeV = (float) (mark.halfV() * WallPassagePlan.MARK_COVER);
        for (int i = 0; i < EMBERS; i++)
        {
            int id = salt + i;
            float life = EMBER_LIFE * (0.7F + 0.6F * hash(id, 5));
            float age = Mth.frac(hash(id, 6) + time / life);
            float angle = hash(id, 7) * Mth.TWO_PI + age * EMBER_SWIRL;
            float reach = (0.75F + 0.3F * hash(id, 8)) * (1 - EMBER_GATHER * age);
            var along = WraithInk.onSurface(face, Mth.cos(angle) * edgeU * reach, Mth.sin(angle) * edgeV * reach);
            double depth = -EMBER_START + age * EMBER_TRAVEL;
            var at = mark.center().add(along[0], along[1], along[2]).add(inward.scale(depth)).subtract(camera);
            // Up close a dot shows its square sides, so embers and motes by the eye fade away
            float near = Mth.clamp((float) (at.length() - EMBER_NEAR) / EMBER_NEAR_FADE, 0, 1);
            int color = ARGB.color((int) (EMBER_ALPHA * Mth.sin(age * Mth.PI) * open * near), EMBER_COLOR);
            glow(buffer, pose, at, EMBER_SIZE, 0, 0, 0, EMBER_SIZE, 0, color);
            glow(buffer, pose, at, EMBER_SIZE, 0, 0, 0, 0, EMBER_SIZE, color);
            glow(buffer, pose, at, 0, EMBER_SIZE, 0, 0, 0, EMBER_SIZE, color);
        }
    }

    /**
     * @return a world offset from a mark's center as its u and v on that mark's surface, the inverse of {@link WraithStainRender#onSurface}
     */
    private static float[] surfaceOffset(Direction face, Vec3 offset)
    {
        return switch (face.getAxis())
        {
            case X -> new float[]{(float) offset.z, (float) offset.y};
            case Y -> new float[]{(float) offset.x, (float) offset.z};
            case Z -> new float[]{(float) offset.x, (float) offset.y};
        };
    }

    /**
     * Faint violet motes drifting toward the far mouth, each on its own line and speed so they never march in step.
     */
    private static void motes(PoseStack.Pose pose, VertexConsumer buffer, Plan plan, AABB box, Vec3 camera, float time, float shown, WallShape shape)
    {
        var middle = box.getCenter();
        var axis = plan.into().getAxis();
        boolean positive = plan.into().getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double from = axis.choose(box.minX, box.minY, box.minZ), length = axis.choose(box.getXsize(), box.getYsize(), box.getZsize());
        int count = plan.depth() * MOTES_PER_BLOCK;
        for (int i = 0; i < count; i++)
        {
            float speed = MOTE_SPEED * (0.5F + hash(i, 4));
            float progress = Mth.frac(hash(i, 1) + time * speed / (float) length);
            double s = from + (positive ? progress : 1 - progress) * length;
            float first = hash(i, 2), second = hash(i, 3);
            double x = axis == Direction.Axis.X ? s : across(first, box.minX, box.maxX);
            double y = axis == Direction.Axis.Y ? s : across(axis == Direction.Axis.X ? first : second, box.minY, box.maxY);
            double z = axis == Direction.Axis.Z ? s : across(second, box.minZ, box.maxZ);
            // Drawn in with the walls where the tunnel pinches, and gone where it is shut
            float open = 1 - shape.closure(s);
            var point = new Vec3(x, y, z);
            var at = middle.add(point.subtract(middle).multiply(axis == Direction.Axis.X ? 1 : open, axis == Direction.Axis.Y ? 1 : open, axis == Direction.Axis.Z ? 1 : open))
                    .subtract(camera);
            float near = Mth.clamp((float) (at.length() - EMBER_NEAR) / EMBER_NEAR_FADE, 0, 1);
            int color = ARGB.color((int) (MOTE_ALPHA * Mth.sin(progress * Mth.PI) * shown * near * open), MOTE_COLOR);
            // Three crossed squares read as a dot from any side without facing the camera
            glow(buffer, pose, at, MOTE_SIZE, 0, 0, 0, MOTE_SIZE, 0, color);
            glow(buffer, pose, at, MOTE_SIZE, 0, 0, 0, 0, MOTE_SIZE, color);
            glow(buffer, pose, at, 0, MOTE_SIZE, 0, 0, 0, MOTE_SIZE, color);
        }
    }

    private static double across(float t, double min, double max)
    {
        return Mth.lerp(t, min + MOTE_MARGIN, max - MOTE_MARGIN);
    }

    private static void glow(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float ax, float ay, float az, float bx, float by, float bz, int color)
    {
        buffer.addVertex(pose, (float) at.x - ax - bx, (float) at.y - ay - by, (float) at.z - az - bz).setColor(color);
        buffer.addVertex(pose, (float) at.x + ax - bx, (float) at.y + ay - by, (float) at.z + az - bz).setColor(color);
        buffer.addVertex(pose, (float) at.x + ax + bx, (float) at.y + ay + by, (float) at.z + az + bz).setColor(color);
        buffer.addVertex(pose, (float) at.x - ax + bx, (float) at.y - ay + by, (float) at.z - az + bz).setColor(color);
    }

    private static float hash(int i, int salt)
    {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 world, Vec3 camera, float u, float v, int color, float nx, float ny, float nz)
    {
        buffer.addVertex(pose, (float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z))
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
