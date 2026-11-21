package net.voxelindustry.voidheart.client.render.blight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.blight.Punctured;
import net.voxelindustry.voidheart.common.content.blight.RapierStrikes;
import net.voxelindustry.voidheart.common.content.blight.Wound;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The punctures a rapier leaves: small lenses of black that ignore the light, with a faint pale rim, standing just off the body where each thrust went in.
 * They open fast, breathe unevenly and pinch shut oldest first. On an Unseaming they run together into one crack, and the snap back throws a pale ring
 * and a brief darkening around where the crack was.
 * Drawn blended over the scene without writing depth, so slits crossing each other never flicker, and still hidden behind the body they are on.
 */
public final class PunctureRender
{
    private static final RenderType SLIT = VoidHeartRenderTypes.tint(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/slit.png"));
    private static final RenderType RING = VoidHeartRenderTypes.radiant(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/snap_ring.png"));
    private static final RenderType DARK = VoidHeartRenderTypes.tint(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/snap_dark.png"));

    /** A slit's size in blocks: its 6 by 3 sprite at the 16 pixels a block of the world around it. */
    private static final float SLIT_LENGTH   = 6 / 16F;
    private static final float SLIT_WIDTH    = 3 / 16F;
    /** Steps a slit opens and closes in, so it changes by whole pixels like the blocks around it. */
    private static final float SIZE_STEPS    = 2;
    /** How far off the body a slit stands, so it never sinks into the skin. */
    private static final float OUTSET        = 0.04F;
    /** A slit's distance from the body's axis, against where it was on the box: boxes stand wider than most bodies. */
    private static final float INSET         = 0.8F;
    private static final float OPEN_TICKS    = 3;
    private static final float PINCH_TICKS   = 4;
    /** The crack the slits join into: this many slits end to end, each turned off the last. */
    private static final int   CRACK_PARTS   = 3;
    /** How far each segment of the crack turns from the last, in radians. */
    private static final float CRACK_JAG     = 0.55F;

    private static final float RING_TICKS    = 7;
    private static final float RING_FROM     = 0.3F;
    private static final float RING_TO       = 1.9F;
    private static final float RING_ALPHA    = 0.95F;
    private static final int   RING_COLOR    = 0xE8DEFF;
    private static final float DARK_TICKS    = 8;
    private static final float DARK_SIZE     = 2.2F;
    private static final float DARK_ALPHA    = 0.55F;

    /** Folds seen, by entity, kept past their end for the snap. */
    private static final Map<Integer, Snap> SNAPS = new HashMap<>();

    /**
     * A fold being watched for its end. The snap is timed from when this client sees the fold go, since its clock can trail the server's by a few ticks.
     *
     * @param end when it was seen to end, or {@link Double#NaN} while it lasts
     */
    private record Snap(double end, Vec3 center)
    {
    }

    /**
     * @param shift      how far it is moved off its point, in the plane facing the camera
     * @param shiftAngle which way, turned from the camera's right
     */
    private record Lens(Vec3 at, float length, float width, float angle, int alpha, float shift, float shiftAngle)
    {
        Lens(Vec3 at, float length, float width, float angle, int alpha)
        {
            this(at, length, width, angle, alpha, 0, 0);
        }
    }

    private PunctureRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null)
            return;

        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = FoldLook.now(level, partial);
        var camera = context.levelState().cameraRenderState.pos;
        var orientation = context.levelState().cameraRenderState.orientation;

        var lenses = new ArrayList<Lens>();
        for (var entity : level.entitiesForRendering())
        {
            if (!(entity instanceof LivingEntity living) || living.isInvisible())
                continue;
            var punctured = living.getAttached(RapierStrikes.PUNCTURED);
            if (punctured == null)
                continue;

            var feet = new Vec3(Mth.lerp(partial, living.xo, living.getX()), Mth.lerp(partial, living.yo, living.getY()), Mth.lerp(partial, living.zo, living.getZ()));
            float bodyYaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
            for (var wound : punctured.wounds().values())
                slits(lenses, living, feet, bodyYaw, wound, now, camera);
            punctured.fold().ifPresent(fold -> crack(lenses, living, feet, bodyYaw, fold, now, camera));
        }

        FoldLook.forgetEnded(level);
        SNAPS.replaceAll((id, snap) ->
        {
            if (!Double.isNaN(snap.end()))
                return snap;
            boolean folded = level.getEntity(id) instanceof LivingEntity living && living.getAttached(RapierStrikes.PUNCTURED) != null
                             && living.getAttached(RapierStrikes.PUNCTURED).fold().isPresent();
            return folded ? snap : new Snap(now, snap.center());
        });
        SNAPS.values().removeIf(snap -> now > snap.end() + Math.max(RING_TICKS, DARK_TICKS) || now < snap.end());
        if (lenses.isEmpty() && SNAPS.isEmpty())
            return;

        var poseStack = context.poseStack();
        var right = orientation.transform(new Vector3f(1, 0, 0));
        var up = orientation.transform(new Vector3f(0, 1, 0));
        var collector = context.submitNodeCollector();
        if (!lenses.isEmpty())
            collector.submitCustomGeometry(poseStack, SLIT, (pose, buffer) ->
            {
                for (var lens : lenses)
                    lens(pose, buffer, lens, camera, right, up);
            });

        var snaps = List.copyOf(SNAPS.values());
        collector.submitCustomGeometry(poseStack, DARK, (pose, buffer) ->
        {
            for (var snap : snaps)
            {
                float age = (float) (now - snap.end());
                if (age < 0 || age > DARK_TICKS)
                    continue;
                float fade = 1 - age / DARK_TICKS;
                billboard(pose, buffer, snap.center().subtract(camera), right, up, DARK_SIZE * (0.7F + 0.3F * fade), ARGB.color((int) (255 * DARK_ALPHA * fade), 0xFFFFFF));
            }
        });
        collector.submitCustomGeometry(poseStack, RING, (pose, buffer) ->
        {
            for (var snap : snaps)
            {
                float age = (float) (now - snap.end());
                if (age < 0 || age > RING_TICKS)
                    continue;
                float t = age / RING_TICKS;
                float eased = 1 - (1 - t) * (1 - t);
                billboard(pose, buffer, snap.center().subtract(camera), right, up, Mth.lerp(eased, RING_FROM, RING_TO), ARGB.color((int) (255 * RING_ALPHA * (1 - t)), RING_COLOR));
            }
        });
    }

    private static void slits(List<Lens> lenses, LivingEntity entity, Vec3 feet, float bodyYaw, Wound wound, double now, Vec3 camera)
    {
        var slits = wound.slits();
        for (int i = 0; i < slits.size(); i++)
        {
            // Ages taken in double before they shrink to float, so ticks stay whole however old the world is
            long closeAt = wound.lastHit() + Wound.HOLD + i * Wound.CLOSE_EVERY;
            float pinch = Mth.clamp((float) (closeAt - now) / PINCH_TICKS, 0, 1);
            // Only the newest is still opening: the others were open before the last hit
            float open = i == slits.size() - 1 ? Mth.clamp((float) (now - wound.lastHit()) / OPEN_TICKS, 0, 1) : 1;
            float width = open * pinch;
            if (width <= 0)
                continue;
            var slit = slits.get(i);
            var at = onSurface(entity, feet, bodyYaw, slit.side(), slit.height(), slit.depth(), camera);
            float step = Mth.ceil(width * SIZE_STEPS) / SIZE_STEPS;
            lenses.add(new Lens(at, SLIT_LENGTH * step, SLIT_WIDTH * step, slit.angle(), 255));
        }
    }

    /**
     * The slits run into the middle of them all, stretching until they are one crack, which then shrinks with the body collapsing into it.
     */
    private static void crack(List<Lens> lenses, LivingEntity entity, Vec3 feet, float bodyYaw, Punctured.Fold fold, double now, Vec3 camera)
    {
        float age = FoldLook.age(entity, fold, now);
        float[] center = FoldLook.crackCenter(fold.slits());
        var middle = onSurface(entity, feet, bodyYaw, center[0], center[1], center[2], camera);
        SNAPS.put(entity.getId(), new Snap(Double.NaN, FoldLook.toWorld(entity, feet, bodyYaw, center[0], center[1], center[2])));

        float join = Mth.clamp(age / FoldLook.CRACK_TICKS, 0, 1);
        float shrink = 1 - FoldLook.collapse(age);
        if (shrink <= 0.02F)
            return;
        if (join < 1)
        {
            for (int i = 0; i < fold.slits().size(); i++)
            {
                var slit = fold.slits().get(i);
                var at = onSurface(entity, feet, bodyYaw, slit.side(), slit.height(), slit.depth(), camera);
                lenses.add(new Lens(at.lerp(middle, join), SLIT_LENGTH, SLIT_WIDTH, Mth.lerp(join, slit.angle(), 0), 255));
            }
            return;
        }
        // One jagged crack: thin segments end to end, each turned off the last, all shrinking with the body
        float part = SLIT_LENGTH * shrink;
        float width = SLIT_WIDTH * shrink;
        float reach = -part * (CRACK_PARTS - 1) / 2F;
        for (int i = 0; i < CRACK_PARTS; i++)
        {
            float angle = (i % 2 == 0 ? 1 : -1) * CRACK_JAG + 0.2F;
            lenses.add(new Lens(middle, part, width, angle, 255, reach * 0.85F, 0.2F));
            reach += part;
        }
    }

    /** A point of the body pushed out of it, away from its middle, or toward the camera for a point on its axis. */
    private static Vec3 onSurface(LivingEntity entity, Vec3 feet, float bodyYaw, float side, float height, float depth, Vec3 camera)
    {
        var onBox = FoldLook.toWorld(entity, feet, bodyYaw, side, height, depth);
        var point = new Vec3(feet.x + (onBox.x - feet.x) * INSET, onBox.y, feet.z + (onBox.z - feet.z) * INSET);
        var out = new Vec3(point.x - feet.x, 0, point.z - feet.z);
        if (out.lengthSqr() < 0.01)
            out = new Vec3(camera.x - point.x, 0, camera.z - point.z);
        if (out.lengthSqr() < 1.0E-6)
            return point;
        return point.add(out.normalize().scale(OUTSET));
    }

    private static void lens(PoseStack.Pose pose, VertexConsumer buffer, Lens lens, Vec3 camera, Vector3f right, Vector3f up)
    {
        // Turned in the plane facing the camera, so a slit keeps its angle however it is seen
        var spin = new Quaternionf().rotationAxis(lens.angle(), new Vector3f(right).cross(up).normalize());
        var along = spin.transform(new Vector3f(right)).mul(lens.length() / 2);
        var across = spin.transform(new Vector3f(up)).mul(lens.width() / 2);
        var normal = new Vector3f(right).cross(up).normalize();
        var shifted = new Quaternionf().rotationAxis(lens.shiftAngle(), normal).transform(new Vector3f(right)).mul(lens.shift());
        var at = lens.at().subtract(camera).add(shifted.x, shifted.y, shifted.z);
        HoleLook.quad(pose, buffer, at, along, across, ARGB.color(lens.alpha(), 0xFFFFFF));
    }

    private static void billboard(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vector3f right, Vector3f up, float size, int color)
    {
        HoleLook.quad(pose, buffer, at, new Vector3f(right).mul(size / 2), new Vector3f(up).mul(size / 2), color);
    }

}
