package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.avengingreach.AvengingReachPayload;
import net.voxelindustry.voidheart.common.content.avengingreach.ReachArms;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The void arms of the Avenging Reach: a segmented tendril that curls out from the middle of its bearer's back, lashes at the aggressor and
 * shuts a three-taloned claw on it, then draws back and thins away. Each segment is a plain box laid along a curve, black and taking world light,
 * with seams that glow on their own.
 * <p>
 * The body uses {@link RenderTypes#entityTranslucentCull}, slightly see-through and culling, so faces are wound counter-clockwise from outside and
 * a box never shows its own far side; where boxes overlap at the bends the arm is a shade denser. The veins are the same boxes again, lifted a hair, in
 * {@link RenderTypes#entityTranslucentEmissive} so they glow and are drawn in the same pass as the body rather than under it. Per frame: up to four lashes at once per bearer, each {@value #SEGMENTS} segments and six talon boxes, about 150 quads per pass.
 */
public final class AvengingReachRender
{
    private static final Identifier BODY_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/avenging_reach.png");
    private static final Identifier SEAM_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/entity/avenging_reach_glow.png");
    private static final RenderType BODY_TYPE     = RenderTypes.entityTranslucentCull(BODY_TEXTURE);
    private static final RenderType SEAM_TYPE     = RenderTypes.entityTranslucentEmissive(SEAM_TEXTURE);

    // Sizes snap to whole pixels of a block, and the skin is laid at a block's 16 texels per block, as on any mob
    private static final double PIXEL         = 1 / 16D;
    // Faces start within this many texels of the skin's corner, so even the longest segment stays inside it
    private static final int    SKIN_SPAN     = 8;
    // Only one segment in this many carries glowing veins, so they stay a few sparks along the arm
    private static final int    VEIN_EVERY    = 3;
    // The veins sit this far off the body, in the same translucent pass, so the body never covers them nor fights them
    private static final double SEAM_LIFT     = 0.004;
    private static final int    SEGMENTS      = 10;
    private static final double BASE_WIDTH    = 3 * PIXEL;
    private static final double TIP_WIDTH     = 2 * PIXEL;
    // Each segment starts this many widths early, so bends show no gap
    private static final double OVERLAP       = 0.5;

    // The coil the tendril curls out into behind its anchor before it lashes, in blocks
    private static final double COIL_BACK     = 0.75;
    private static final double COIL_SIDE     = 0.6;
    private static final double COIL_UP       = 0.5;
    // How high the lash arcs on its way, and how far it may swing to either side, against the distance to the target
    private static final double LASH_LIFT     = 0.18;
    private static final double LASH_SWAY     = 0.22;
    // A ripple along the tendril, strongest while coiled and calmer once it lashes
    private static final double WOBBLE        = 0.09;
    private static final double WOBBLE_CALM   = 0.75;
    private static final double WOBBLE_WAVES  = 1.5;
    private static final double WOBBLE_SPEED  = 0.9;

    private static final int    TALONS        = 3;
    private static final double PALM_LENGTH   = 2 * PIXEL;
    private static final double PALM_WIDTH    = 3 * PIXEL;
    private static final double TALON_LENGTH  = 4 * PIXEL;
    private static final double TALON_WIDTH   = PIXEL;
    private static final double HOOK_LENGTH   = 3 * PIXEL;
    private static final double HOOK_WIDTH    = PIXEL;
    private static final double TALON_OPEN    = 0.95;
    private static final double TALON_SHUT    = 0.18;
    private static final double HOOK_BEND     = 1.0;
    private static final double MIN_EXTENT    = 0.03;
    // In first person, boxes starting this close to the eyes are left out
    private static final double FIRST_PERSON_HIDDEN = 1.0;

    private static final int    IMPACT_EMBERS  = 10;
    private static final double IMPACT_SPEED   = 0.12;
    private static final int    RETRACT_EMBERS = 2;
    private static final double RETRACT_SPEED  = 0.02;

    private static final int WHITE      = 0xFFFFFFFF;
    // The body lets a little of what is behind it through
    private static final int BODY_COLOR = 0xC8FFFFFF;

    private static final class Lash
    {
        final int    ownerId;
        final int    targetId;
        final int    arm;
        final long   start;
        final double sway;
        final double phase;
        final int    skin;
        @Nullable Vec3   tip;
        Vec3[] points = new Vec3[0];

        Lash(AvengingReachPayload payload, long start)
        {
            ownerId = payload.ownerId();
            targetId = payload.targetId();
            arm = payload.arm();
            this.start = start;
            var random = new Random(payload.seed());
            sway = random.nextDouble() * 2 - 1;
            phase = random.nextDouble() * Math.PI * 2;
            skin = random.nextInt(256);
        }
    }

    private static final List<Lash> LASHES = new ArrayList<>();

    private AvengingReachRender()
    {
    }

    public static void register()
    {
        ClientPlayNetworking.registerGlobalReceiver(AvengingReachPayload.TYPE, (payload, context) ->
        {
            var level = context.client().level;
            if (level != null)
                LASHES.add(new Lash(payload, level.getGameTime()));
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(AvengingReachRender::collectSubmits);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client.level));
    }

    public static void clear()
    {
        LASHES.clear();
    }

    /** How many lashes are being drawn, for game tests. */
    public static int activeLashes()
    {
        return LASHES.size();
    }

    private static void tick(@Nullable ClientLevel level)
    {
        if (level == null)
        {
            LASHES.clear();
            return;
        }
        long now = level.getGameTime();
        LASHES.removeIf(lash -> now - lash.start > ReachArms.TOTAL_TICKS || !(level.getEntity(lash.ownerId) instanceof LivingEntity));
        var random = level.getRandom();
        for (var lash : LASHES)
        {
            long ticks = now - lash.start;
            if (ticks == ReachArms.IMPACT_TICK && lash.points.length > 0)
            {
                // Where the claw was drawn, which falls short of a target that slipped out of reach
                var claw = lash.points[lash.points.length - 1];
                for (int i = 0; i < IMPACT_EMBERS; i++)
                    level.addParticle(VoidHeart.VOID_EMBER_PARTICLE, claw.x, claw.y, claw.z,
                            (random.nextDouble() - 0.5) * IMPACT_SPEED * 2, random.nextDouble() * IMPACT_SPEED, (random.nextDouble() - 0.5) * IMPACT_SPEED * 2);
            }
            else if (ticks > ReachArms.IMPACT_TICK + ReachArms.HOLD_TICKS && lash.points.length > 1)
            {
                // Thinning away from the tip back toward the anchor
                for (int i = 0; i < RETRACT_EMBERS; i++)
                {
                    var at = lash.points[lash.points.length - 1 - random.nextInt(Math.min(lash.points.length, 4))];
                    level.addParticle(VoidHeart.VOID_EMBER_PARTICLE, at.x, at.y, at.z,
                            (random.nextDouble() - 0.5) * RETRACT_SPEED * 2, RETRACT_SPEED, (random.nextDouble() - 0.5) * RETRACT_SPEED * 2);
                }
            }
        }
    }

    private static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || LASHES.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        var collector = context.submitNodeCollector();
        var poseStack = context.poseStack();

        var shapes = new ArrayList<Shape>();
        for (var lash : LASHES)
        {
            var shape = shapeOf(level, lash, level.getGameTime() - lash.start + partialTicks, partialTicks);
            if (shape == null)
                continue;
            // Seen from the bearer's own eyes, the root behind their head would fill the view, so only the arm reaching out shows
            if (lash.ownerId == firstPersonId(minecraft))
                shape = new Shape(shape.boxes().stream().filter(box -> box.from().distanceToSqr(camera) > FIRST_PERSON_HIDDEN * FIRST_PERSON_HIDDEN).toList());
            shapes.add(shape);
        }
        if (shapes.isEmpty())
            return;

        collector.submitCustomGeometry(poseStack, BODY_TYPE, (pose, buffer) -> shapes.forEach(shape -> shape.draw(pose, buffer, camera, true)));
        collector.submitCustomGeometry(poseStack, SEAM_TYPE, (pose, buffer) -> shapes.forEach(shape -> shape.draw(pose, buffer, camera, false)));
    }

    private static int firstPersonId(Minecraft minecraft)
    {
        var viewer = minecraft.getCameraEntity();
        return viewer != null && minecraft.options.getCameraType().isFirstPerson() ? viewer.getId() : -1;
    }

    /** One box of a tendril or claw, in world coordinates. */
    private record Box(Vec3 from, Vec3 forward, double length, double width, double roll, int light, int skin, boolean veined)
    {
    }

    private record Shape(List<Box> boxes)
    {
        void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, boolean lit)
        {
            for (var box : boxes)
            {
                if (lit || box.veined())
                    AvengingReachRender.box(pose, buffer, box, camera, lit ? box.light() : LightCoordsUtil.FULL_BRIGHT, lit ? BODY_COLOR : WHITE, lit ? 0 : SEAM_LIFT);
            }
        }
    }

    private static @Nullable Shape shapeOf(ClientLevel level, Lash lash, float ticks, float partialTicks)
    {
        if (ticks < 0 || ticks > ReachArms.TOTAL_TICKS || !(level.getEntity(lash.ownerId) instanceof LivingEntity owner))
            return null;
        float extent = ReachArms.extent(ticks);
        if (extent < MIN_EXTENT)
            return null;

        var position = owner.getPosition(partialTicks);
        double yaw = Math.toRadians(owner.getPreciseBodyRotation(partialTicks));
        var forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        var right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var up = new Vec3(0, 1, 0);
        var local = ReachArms.anchor(lash.arm, owner.getBbHeight());
        var anchor = position.add(right.scale(local.x)).add(0, local.y, 0).subtract(forward.scale(local.z));
        var chest = position.add(0, ReachArms.chestHeight(owner.getBbHeight()), 0);

        if (level.getEntity(lash.targetId) instanceof LivingEntity target)
        {
            var box = target.getBoundingBox().move(target.getPosition(partialTicks).subtract(target.position()));
            lash.tip = ReachArms.nearestPoint(chest, box);
        }
        if (lash.tip == null)
            return null;
        var tip = lash.tip;
        var fromChest = tip.subtract(chest);
        if (fromChest.length() > ReachArms.REACH)
            tip = chest.add(fromChest.normalize().scale(ReachArms.REACH));

        var toTip = tip.subtract(anchor);
        double distance = Math.max(toTip.length(), 0.01);
        var side = right.scale(ReachArms.side(lash.arm));
        var across = toTip.cross(up).lengthSqr() > 1E-6 ? toTip.cross(up).normalize() : side;

        float straighten = ReachArms.straighten(ticks);
        double rise = COIL_UP * ReachArms.coilRise(lash.arm);
        var coilA = anchor.subtract(forward.scale(COIL_BACK)).add(side.scale(COIL_SIDE)).add(up.scale(rise));
        var coilB = anchor.subtract(forward.scale(COIL_BACK * 0.3)).add(side.scale(COIL_SIDE * 1.7)).add(up.scale(rise * 1.8));
        var lashA = anchor.add(toTip.scale(0.33)).add(up.scale(LASH_LIFT * distance)).add(side.scale(LASH_SWAY * distance * 0.5));
        var lashB = anchor.add(toTip.scale(0.7)).add(up.scale(LASH_LIFT * distance * 0.6)).add(across.scale(LASH_SWAY * distance * lash.sway));
        var controlA = coilA.lerp(lashA, straighten);
        var controlB = coilB.lerp(lashB, straighten);

        double wobble = WOBBLE * (1 - WOBBLE_CALM * straighten);
        var points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++)
        {
            double along = extent * i / (double) SEGMENTS;
            var point = bezier(anchor, controlA, controlB, tip, along);
            double ripple = wobble * Math.sin(along * Math.PI) * Math.sin(along * Math.PI * 2 * WOBBLE_WAVES + ticks * WOBBLE_SPEED + lash.phase);
            points[i] = point.add(across.scale(ripple)).add(up.scale(ripple * 0.5));
        }
        lash.points = points;

        float thickness = ReachArms.thickness(ticks);
        var boxes = new ArrayList<Box>();
        for (int i = 0; i < SEGMENTS; i++)
        {
            double along = extent * i / (double) SEGMENTS;
            double width = pixels(Mth.lerp(0.5 * along + 0.5 * i / (double) SEGMENTS, BASE_WIDTH, TIP_WIDTH) * thickness);
            var segment = points[i + 1].subtract(points[i]);
            double length = segment.length();
            if (length < 1E-4)
                continue;
            var direction = segment.scale(1 / length);
            var from = points[i].subtract(direction.scale(width * OVERLAP));
            boxes.add(new Box(from, direction, length + width * OVERLAP, width, 0, lightAt(level, points[i]), lash.skin + i, (lash.skin + i) % VEIN_EVERY == 0));
        }
        claw(boxes, level, points, ReachArms.clawOpen(ticks), thickness, lash.skin + SEGMENTS);
        return new Shape(boxes);
    }

    private static void claw(List<Box> boxes, ClientLevel level, Vec3[] points, float open, float thickness, int skin)
    {
        var tip = points[points.length - 1];
        var forward = tip.subtract(points[points.length - 2]);
        if (forward.lengthSqr() < 1E-8)
            return;
        forward = forward.normalize();
        int light = lightAt(level, tip);
        var palmFrom = tip.subtract(forward.scale(PALM_LENGTH * 0.5 * thickness));
        boxes.add(new Box(palmFrom, forward, PALM_LENGTH * thickness, pixels(PALM_WIDTH * thickness), 0, light, skin, false));

        var frame = frame(forward, 0);
        double spread = Mth.lerp(open, TALON_SHUT, TALON_OPEN);
        for (int talon = 0; talon < TALONS; talon++)
        {
            double around = talon * Math.PI * 2 / TALONS;
            var radial = frame[0].scale(Math.cos(around)).add(frame[1].scale(Math.sin(around)));
            var base = tip.add(radial.scale(PALM_WIDTH * 0.3 * thickness));
            var out = forward.scale(Math.cos(spread)).add(radial.scale(Math.sin(spread)));
            boxes.add(new Box(base, out, TALON_LENGTH * thickness, pixels(TALON_WIDTH * thickness), 0, light, skin + talon, false));
            var knuckle = base.add(out.scale(TALON_LENGTH * thickness * 0.9));
            var hook = forward.scale(Math.cos(spread - HOOK_BEND)).add(radial.scale(Math.sin(spread - HOOK_BEND)));
            boxes.add(new Box(knuckle, hook, HOOK_LENGTH * thickness, pixels(HOOK_WIDTH * thickness), 0, light, skin + talon, false));
        }
    }

    /** A size rounded to whole pixels of a block, never under one. */
    private static double pixels(double size)
    {
        return Math.max(1, Math.round(size / PIXEL)) * PIXEL;
    }

    private static int lightAt(ClientLevel level, Vec3 point)
    {
        return LightCoordsUtil.getLightCoords(level, BlockPos.containing(point));
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t)
    {
        double u = 1 - t;
        return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
    }

    /**
     * Two axes across a direction, turned by {@code roll} around it, so that (first, second, forward) keeps the handedness of (x, y, z).
     */
    private static Vec3[] frame(Vec3 forward, double roll)
    {
        var reference = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        var first = reference.cross(forward).normalize();
        var second = forward.cross(first);
        double cos = Math.cos(roll), sin = Math.sin(roll);
        return new Vec3[]{first.scale(cos).add(second.scale(sin)), second.scale(cos).subtract(first.scale(sin))};
    }

    /** Where a box's face starts on the skin, on whole texels, leaving room for the face's largest size. */
    private static double skinOffset(int skin, Direction direction, boolean first)
    {
        int hash = (skin * 31 + direction.ordinal()) * 17 + (first ? 7 : 0);
        return Math.floorMod(hash * 2654435761L >>> 7, SKIN_SPAN) * PIXEL;
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, Box box, Vec3 camera, int light, int color, double lift)
    {
        var frame = frame(box.forward(), box.roll());
        var across = frame[0];
        var upward = frame[1];
        var origin = box.from().subtract(camera);
        for (var direction : Direction.values())
        {
            // A unit cube's face, wound counter-clockwise from outside, laid across the box: x across, y upward, z along
            var corners = GlowCube.faceCorners(direction, 0, 0, 0, 0);
            var normal = switch (direction)
            {
                case EAST -> across;
                case WEST -> across.reverse();
                case UP -> upward;
                case DOWN -> upward.reverse();
                case SOUTH -> box.forward();
                case NORTH -> box.forward().reverse();
            };
            for (int i = 0; i < 4; i++)
            {
                var corner = corners[i];
                double shown = box.width() + 2 * lift;
                var at = origin.add(across.scale((corner[0] - 0.5) * shown))
                        .add(upward.scale((corner[1] - 0.5) * shown))
                        .add(box.forward().scale(corner[2] * box.length()));
                // The skin is a block's worth of texels, laid at its real size on each face from a pixel-aligned spot that varies per box
                double first = direction.getAxis() == Direction.Axis.X ? corner[2] * box.length() : corner[0] * box.width();
                double second = direction.getAxis() == Direction.Axis.Y ? corner[2] * box.length() : corner[1] * box.width();
                buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                        .setColor(color)
                        .setUv((float) (skinOffset(box.skin(), direction, true) + first),
                               (float) (skinOffset(box.skin(), direction, false) + second))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }
}
