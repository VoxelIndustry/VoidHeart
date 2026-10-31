package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight;
import net.voxelindustry.voidheart.common.content.oculus.OculusSightPayloads;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Asks what each table sees, and draws the beholders seeing for it: an eye over each, with the reach of its clear
 * sight and of its dim sight traced around it in dotted rings.
 */
public final class OculusBeholderLayer
{
    private static final int REQUEST_INTERVAL = 20;
    private static final int FIRST_REQUEST_INTERVAL = 5;

    private static final int RING_DOTS_PER_BLOCK = 2;
    private static final int MAX_RING_DOTS = 512;
    private static final float DOT_SIZE = 0.016F;
    private static final int HIGH_RING_COLOR = ARGB.color(220, 199, 125, 255);
    private static final int LOW_RING_COLOR = ARGB.color(140, 150, 110, 210);

    private static final float EYE_WIDTH = 0.11F;
    private static final float EYE_HEIGHT = 0.055F;
    private static final int EYE_SEGMENTS = 12;
    private static final int EYE_HALO = ARGB.color(150, 10, 6, 18);
    private static final int EYE_WHITE = 0xFFD9C8FF;
    private static final int EYE_IRIS = 0xFF9B4DFF;
    private static final int EYE_PUPIL = 0xFF0A0510;

    private OculusBeholderLayer()
    {
    }

    public static void tick(long tick)
    {
        for (var projector : OculusRenderer.projectors())
        {
            int interval = projector.sight() == null ? FIRST_REQUEST_INTERVAL : REQUEST_INTERVAL;
            if (tick % interval == 0)
                ClientPlayNetworking.send(new OculusSightPayloads.Request(projector.anchor));
        }
    }

    public static void receive(OculusSightPayloads.Sight payload, ClientPlayNetworking.Context context)
    {
        for (var projector : OculusRenderer.projectors())
        {
            if (projector.anchor.equals(payload.table()))
                projector.setSight(payload.sight());
        }
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var camera = context.levelState().cameraRenderState;
        var right = new Vector3f(1, 0, 0).rotate(camera.orientation);
        var up = new Vector3f(0, 1, 0).rotate(camera.orientation);
        float seconds = (context.levelState().gameTime % 24000L + context.levelState().worldPartialTicks) / 20F;

        for (var projector : OculusRenderer.projectors())
        {
            var sight = projector.sight();
            if (sight == null || projector.view().asleep() || sight.sources().stream().noneMatch(OculusSight.Source::beholder))
                continue;

            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) ->
            {
                for (var source : sight.sources())
                {
                    if (!source.beholder())
                        continue;
                    var center = Vec3.atCenterOf(source.pos());
                    drawRing(pose, buffer, projector, center, source.lowRange(), LOW_RING_COLOR, camera.pos, right, up, 0);
                    drawRing(pose, buffer, projector, center, source.highRange(), HIGH_RING_COLOR, camera.pos, right, up, seconds * 0.05F);

                    var eye = projector.toHologramClamped(center.x, center.y, center.z);
                    if (eye != null)
                        drawEye(pose, buffer, eye.subtract(camera.pos).toVector3f(), right, up, seconds);
                }
            });
        }
    }

    /**
     * The reach of a beholder at its height, as dots spaced evenly on the land, slowly turning. Dots off the hologram
     * are skipped.
     */
    private static void drawRing(PoseStack.Pose pose, VertexConsumer buffer, OculusProjector projector, Vec3 center, int range, int color,
                                 Vec3 camera, Vector3f right, Vector3f up, float turn)
    {
        int dots = Math.min(MAX_RING_DOTS, Math.max(24, Math.round((float) (Math.PI * 2 * range * RING_DOTS_PER_BLOCK / projector.drawnScale()))));
        for (int dot = 0; dot < dots; dot++)
        {
            double angle = turn + dot * Math.PI * 2 / dots;
            var holo = projector.toHologramClamped(center.x + Math.cos(angle) * range, center.y, center.z + Math.sin(angle) * range);
            if (holo != null)
                square(pose, buffer, holo.subtract(camera).toVector3f(), right, up, DOT_SIZE, color);
        }
    }

    /**
     * An almond eye facing the camera, its iris drifting as if looking around.
     */
    private static void drawEye(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float seconds)
    {
        var lifted = new Vector3f(center).add(0, EYE_HEIGHT * 1.6F, 0);
        almond(pose, buffer, lifted, right, up, EYE_WIDTH * 1.35F, EYE_HEIGHT * 1.8F, EYE_HALO, null);
        almond(pose, buffer, lifted, right, up, EYE_WIDTH, EYE_HEIGHT, EYE_WHITE, new Vector3f(lifted).normalize(-0.002F));

        float lookX = (float) Math.sin(seconds * 0.7F) * EYE_WIDTH * 0.3F;
        float lookY = (float) Math.sin(seconds * 0.45F + 1.3F) * EYE_HEIGHT * 0.2F;
        var iris = new Vector3f(lifted).add(new Vector3f(right).mul(lookX)).add(new Vector3f(up).mul(lookY));
        // Pulled toward the camera, iris and pupil sort in front of the white
        diamondDisc(pose, buffer, iris.add(new Vector3f(lifted).normalize(-0.004F)), right, up, EYE_HEIGHT * 0.85F, EYE_IRIS);
        diamondDisc(pose, buffer, iris.add(new Vector3f(lifted).normalize(-0.002F)), right, up, EYE_HEIGHT * 0.38F, EYE_PUPIL);
    }

    /**
     * Two arcs meeting at the corners of the eye, filled as a strip of quads.
     */
    private static void almond(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float width, float height, int color,
                               @Nullable Vector3f nudge)
    {
        var origin = nudge == null ? center : new Vector3f(center).add(nudge);
        for (int i = 0; i < EYE_SEGMENTS; i++)
        {
            float from = -1 + 2F * i / EYE_SEGMENTS;
            float to = -1 + 2F * (i + 1) / EYE_SEGMENTS;
            float fromHeight = height * (1 - from * from);
            float toHeight = height * (1 - to * to);
            var a = point(origin, right, up, from * width, -fromHeight);
            var b = point(origin, right, up, to * width, -toHeight);
            var c = point(origin, right, up, to * width, toHeight);
            var d = point(origin, right, up, from * width, fromHeight);
            buffer.addVertex(pose, a.x, a.y, a.z).setColor(color);
            buffer.addVertex(pose, b.x, b.y, b.z).setColor(color);
            buffer.addVertex(pose, c.x, c.y, c.z).setColor(color);
            buffer.addVertex(pose, d.x, d.y, d.z).setColor(color);
        }
    }

    private static Vector3f point(Vector3f center, Vector3f right, Vector3f up, float across, float along)
    {
        return new Vector3f(center).add(new Vector3f(right).mul(across)).add(new Vector3f(up).mul(along));
    }

    private static void diamondDisc(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float radius, int color)
    {
        var a = point(center, right, up, 0, -radius);
        var b = point(center, right, up, radius, 0);
        var c = point(center, right, up, 0, radius);
        var d = point(center, right, up, -radius, 0);
        buffer.addVertex(pose, a.x, a.y, a.z).setColor(color);
        buffer.addVertex(pose, b.x, b.y, b.z).setColor(color);
        buffer.addVertex(pose, c.x, c.y, c.z).setColor(color);
        buffer.addVertex(pose, d.x, d.y, d.z).setColor(color);
    }

    private static void square(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float size, int color)
    {
        var a = point(center, right, up, -size, -size);
        var b = point(center, right, up, size, -size);
        var c = point(center, right, up, size, size);
        var d = point(center, right, up, -size, size);
        buffer.addVertex(pose, a.x, a.y, a.z).setColor(color);
        buffer.addVertex(pose, b.x, b.y, b.z).setColor(color);
        buffer.addVertex(pose, c.x, c.y, c.z).setColor(color);
        buffer.addVertex(pose, d.x, d.y, d.z).setColor(color);
    }
}
