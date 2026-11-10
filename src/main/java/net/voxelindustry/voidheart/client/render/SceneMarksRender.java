package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.client.scene.SceneView;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * What a scene draws over its blocks to say what the blocks cannot: how far something reaches, what is tied to what, what runs between them.
 * <p>
 * Render types: everything is flat color through MARKER, blended and without depth writes, so a reach keeps its hue on the pale floor where a glow would wash out,
 * and blocks in front still hide it. Only the halos of arcs and pulses are GLOW, where adding light is the point. Ribbons face the stage's fixed camera, one quad
 * each, so nothing overlaps itself. Widths follow the camera's span: a line an in-world width wide would be thinner than a pixel once the stage holds forty blocks.
 * A scene shows a few reaches and a few dozen links at most, some hundreds of quads a frame.
 */
final class SceneMarksRender
{
    // The shorter side of the stage, in the units widths are given in: a width of 1 is about a pixel of a small window
    private static final float SPAN_UNITS = 170;

    private static final float RING_WIDTH   = 1.6F;
    private static final int   RING_ALPHA   = 210;
    private static final int   RING_FILL    = 26;
    private static final float RING_LIFT    = 0.04F;
    private static final float DASH_BLOCKS  = 1;
    private static final int   DASHES_MIN   = 10;
    private static final int   DASH_STEPS   = 4;
    private static final int   FILL_STEPS   = 48;
    private static final float RING_TURN    = 1600;

    private static final float LINE_WIDTH   = 1.1F;
    private static final int   LINE_ALPHA   = 200;
    private static final float END_SIZE     = 2.2F;

    private static final float PULSE_WIDTH  = 2;
    private static final float PULSE_BLOCKS = 0.45F;
    private static final float PULSE_SPEED  = 0.14F;
    private static final float PULSE_GAP    = 2.2F;
    private static final int   PULSE_ALPHA  = 255;
    private static final int   HALO_ALPHA   = 90;
    private static final float HALO_WIDEN   = 2.4F;
    private static final float PULSE_WHITE  = 0.55F;

    private static final float ARC_WIDTH    = 1.1F;
    private static final float ARC_JOINT    = 0.9F;
    private static final float ARC_JAG      = 0.2F;
    private static final float ARC_TICKS    = 3;
    private static final float ARC_WHITE    = 0.7F;
    private static final float LEAP_BLOCKS  = 1.4F;
    private static final float LEAP_TICKS   = 12;

    private static final float SHADOW_RADIUS = 0.2F;
    private static final int   SHADOW_ALPHA  = 95;
    private static final float SHADOW_LIFT   = 0.02F;
    private static final int   SHADOW_STEPS  = 12;

    private SceneMarksRender()
    {
    }

    /**
     * @param theme the color of what names none of its own
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, SceneFrame frame, SceneView view, List<float[]> shadows, int theme, float worldTime)
    {
        boolean marks = !frame.reaches().isEmpty() || !frame.links().isEmpty() || !frame.outlines().isEmpty() || !shadows.isEmpty();
        if (!marks)
            return;

        float unit = view.span() / SPAN_UNITS;
        var toward = new Matrix4f(view.matrix()).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        var camera = new Vec3(toward.x, toward.y, toward.z);

        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) ->
        {
            for (var shadow : shadows)
                disc(pose, buffer, shadow[0], shadow[1] + SHADOW_LIFT, shadow[2], SHADOW_RADIUS, ARGB.color(SHADOW_ALPHA, 0));

            for (var reach : frame.reaches())
                reach(pose, buffer, reach, unit, rgb(reach.rgb(), theme), worldTime);

            for (var outline : frame.outlines())
                outline(pose, buffer, outline, camera, unit, rgb(outline.rgb(), theme));

            for (var link : frame.links())
                link(pose, buffer, link, camera, unit, rgb(link.rgb(), theme));
        });

        if (frame.links().stream().noneMatch(link -> link.flow() != Scene.Flow.STILL))
            return;
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var link : frame.links())
            {
                int color = rgb(link.rgb(), theme);
                if (link.flow() == Scene.Flow.PULSE)
                    pulses(pose, buffer, link, camera, unit * PULSE_WIDTH * HALO_WIDEN, ARGB.color((int) (HALO_ALPHA * link.alpha()), color));
                else if (link.flow() == Scene.Flow.ARC)
                    arc(pose, buffer, link, camera, unit * ARC_WIDTH * HALO_WIDEN, ARGB.color((int) (HALO_ALPHA * link.alpha()), color));
            }
        });
    }

    private static int rgb(int rgb, int theme)
    {
        return (rgb == Scene.THEME ? theme : rgb) & 0xFFFFFF;
    }

    private static int toWhite(int rgb, float amount)
    {
        return ARGB.srgbLerp(amount, 0xFF000000 | rgb, 0xFFFFFFFF) & 0xFFFFFF;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color)
    {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }

    /**
     * A flat band from one point to the other that shows its face to the camera.
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, float width, int color)
    {
        var along = to.subtract(from);
        var side = along.cross(camera);
        if (side.lengthSqr() < 1.0E-8)
            return;
        side = side.normalize().scale(width / 2);

        vertex(pose, buffer, from.subtract(side), color);
        vertex(pose, buffer, from.add(side), color);
        vertex(pose, buffer, to.add(side), color);
        vertex(pose, buffer, to.subtract(side), color);
    }

    private static void disc(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float radius, int color)
    {
        for (int step = 0; step < SHADOW_STEPS; step += 2)
        {
            buffer.addVertex(pose, x, y, z).setColor(color);
            for (int corner = 0; corner <= 2; corner++)
            {
                double angle = (step + corner) * Math.PI * 2 / SHADOW_STEPS;
                buffer.addVertex(pose, x + (float) Math.cos(angle) * radius, y, z + (float) Math.sin(angle) * radius).setColor(color);
            }
        }
    }

    /**
     * Dashes that turn slowly, over a faint fill that says the reach is the whole disc and not its edge.
     */
    private static void reach(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownReach ring, float unit, int rgb, float worldTime)
    {
        float radius = ring.radius();
        if (radius < 0.05F)
            return;

        float x = (float) ring.center().x;
        float y = (float) ring.center().y + RING_LIFT;
        float z = (float) ring.center().z;

        int fill = ARGB.color((int) (RING_FILL * ring.alpha()), rgb);
        for (int step = 0; step < FILL_STEPS; step += 2)
        {
            buffer.addVertex(pose, x, y, z).setColor(fill);
            for (int corner = 0; corner <= 2; corner++)
            {
                double angle = (step + corner) * Math.PI * 2 / FILL_STEPS;
                buffer.addVertex(pose, x + (float) Math.cos(angle) * radius, y, z + (float) Math.sin(angle) * radius).setColor(fill);
            }
        }

        float half = Math.max(0.05F, unit * RING_WIDTH / 2);
        float inner = Math.max(0, radius - half);
        float outer = radius + half;
        int dashes = Math.max(DASHES_MIN, Math.round((float) (Math.PI * radius / Math.max(DASH_BLOCKS, unit * 5))));
        double dash = Math.PI / dashes;
        double turn = worldTime / RING_TURN * Math.PI * 2;
        int color = ARGB.color((int) (RING_ALPHA * ring.alpha()), rgb);

        for (int index = 0; index < dashes; index++)
        {
            for (int step = 0; step < DASH_STEPS; step++)
            {
                double from = turn + index * dash * 2 + dash * step / DASH_STEPS;
                double to = turn + index * dash * 2 + dash * (step + 1) / DASH_STEPS;
                float fromCos = (float) Math.cos(from), fromSin = (float) Math.sin(from);
                float toCos = (float) Math.cos(to), toSin = (float) Math.sin(to);
                buffer.addVertex(pose, x + fromCos * inner, y, z + fromSin * inner).setColor(color);
                buffer.addVertex(pose, x + fromCos * outer, y, z + fromSin * outer).setColor(color);
                buffer.addVertex(pose, x + toCos * outer, y, z + toSin * outer).setColor(color);
                buffer.addVertex(pose, x + toCos * inner, y, z + toSin * inner).setColor(color);
            }
        }
    }

    private static void outline(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownOutline outline, Vec3 camera, float unit, int rgb)
    {
        int color = ARGB.color((int) (LINE_ALPHA * outline.alpha()), rgb);
        float width = unit * LINE_WIDTH;
        var min = outline.min();
        var max = outline.max();
        double[] xs = { min.x, max.x }, ys = { min.y, max.y }, zs = { min.z, max.z };

        for (int a = 0; a < 2; a++)
        {
            for (int b = 0; b < 2; b++)
            {
                ribbon(pose, buffer, new Vec3(min.x, ys[a], zs[b]), new Vec3(max.x, ys[a], zs[b]), camera, width, color);
                ribbon(pose, buffer, new Vec3(xs[a], min.y, zs[b]), new Vec3(xs[a], max.y, zs[b]), camera, width, color);
                ribbon(pose, buffer, new Vec3(xs[a], ys[b], min.z), new Vec3(xs[a], ys[b], max.z), camera, width, color);
            }
        }
    }

    private static void link(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownLink link, Vec3 camera, float unit, int rgb)
    {
        var reached = link.start().lerp(link.end(), link.growth());
        switch (link.flow())
        {
            case STILL ->
            {
                int color = ARGB.color((int) (LINE_ALPHA * link.alpha()), rgb);
                ribbon(pose, buffer, link.start(), reached, camera, unit * LINE_WIDTH, color);
                end(pose, buffer, link.start(), link.end(), camera, unit, color);
                if (link.growth() >= 1)
                    end(pose, buffer, link.end(), link.start(), camera, unit, color);
            }
            case PULSE ->
            {
                ribbon(pose, buffer, link.start(), reached, camera, unit * LINE_WIDTH, ARGB.color((int) (LINE_ALPHA * 0.6F * link.alpha()), rgb));
                pulses(pose, buffer, link, camera, unit * PULSE_WIDTH, ARGB.color((int) (PULSE_ALPHA * link.alpha()), toWhite(rgb, PULSE_WHITE)));
            }
            case ARC -> arc(pose, buffer, link, camera, unit * ARC_WIDTH, ARGB.color((int) (PULSE_ALPHA * link.alpha()), toWhite(rgb, ARC_WHITE)));
        }
    }

    /**
     * A short bar across the end of a line, the way a drawing closes a dimension.
     */
    private static void end(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vec3 other, Vec3 camera, float unit, int color)
    {
        var side = other.subtract(at).cross(camera);
        if (side.lengthSqr() < 1.0E-8)
            return;
        side = side.normalize().scale(unit * END_SIZE);
        ribbon(pose, buffer, at.subtract(side), at.add(side), camera, unit * LINE_WIDTH, color);
    }

    private static void pulses(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownLink link, Vec3 camera, float width, int color)
    {
        var along = link.end().subtract(link.start());
        float length = (float) along.length();
        if (length < 1.0E-3F)
            return;

        float reach = length * link.growth();
        float pulse = Math.min(PULSE_BLOCKS, length / 3);
        float gap = Math.max(PULSE_GAP, length / 4);
        float head = (link.age() * PULSE_SPEED) % gap;
        for (float at = head; at < reach; at += gap)
        {
            float from = Math.max(0, at - pulse);
            ribbon(pose, buffer, link.start().add(along.scale(from / length)), link.start().add(along.scale(at / length)), camera, width, color);
        }
    }

    /**
     * Rolled from the link's own ends and age, so the same tick always draws the same arc and a held scene stays still.
     */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownLink link, Vec3 camera, float width, int color)
    {
        var along = link.end().subtract(link.start());
        float length = (float) along.length();
        if (length < 1.0E-3F)
            return;

        var side = along.cross(camera).normalize();
        int joints = Math.max(3, Mth.ceil(length / ARC_JOINT));
        long roll = (long) Math.floor(link.age() / ARC_TICKS) * 31 + Double.hashCode(link.start().x + link.end().z * 7);
        float leap = (link.age() % LEAP_TICKS) / LEAP_TICKS * (length + LEAP_BLOCKS);

        Vec3 previous = link.start();
        for (int joint = 1; joint <= joints; joint++)
        {
            float t = joint / (float) joints;
            if (t > link.growth())
                break;
            var point = link.start().add(along.scale(t));
            if (joint < joints)
            {
                float jag = (noise(roll + joint * 131L) * 2 - 1) * ARC_JAG * Math.min(1, length / 3);
                float lift = (noise(roll + joint * 733L) * 2 - 1) * ARC_JAG * 0.6F;
                point = point.add(side.scale(jag)).add(0, lift, 0);
            }

            float behind = leap - t * length;
            boolean leaping = behind >= 0 && behind < LEAP_BLOCKS;
            ribbon(pose, buffer, previous, point, camera, leaping ? width * 1.8F : width, color);
            previous = point;
        }
    }

    private static float noise(long seed)
    {
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        seed = (seed ^ (seed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return ((seed ^ (seed >>> 33)) & 0xFFFFFF) / (float) 0x1000000;
    }
}
