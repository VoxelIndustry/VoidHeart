package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.ley.LeyDebugPayload;
import org.joml.Vector3f;

/**
 * Debug view of the ley lattice: glowing ribbons with pulses running in the flow direction, and a light column per node.
 */
public class LeyDebugRender
{
    private static final double DRAW_DISTANCE = 384;
    private static final float CORE_HALF_WIDTH = 0.12F;
    private static final float GLOW_HALF_WIDTH = 0.55F;
    private static final float PULSE_WAVELENGTH = 24;
    private static final float PULSE_SPEED = 0.6F;

    private static final int[] NODE_COLORS = { 0xB8A9D9, 0x6FCF97, 0xF2C94C, 0xEB5757 };

    private static volatile LeyDebugPayload view = LeyDebugPayload.CLEAR;

    public static void receive(LeyDebugPayload payload, ClientPlayNetworking.Context context)
    {
        view = payload;
    }

    public static void clear()
    {
        view = LeyDebugPayload.CLEAR;
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var current = view;
        if (current.lines().isEmpty() && current.nodes().isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) ->
        {
            for (var line : current.lines())
                drawLine(pose, buffer, line, camera, time);
            for (var node : current.nodes())
                drawNode(pose, buffer, node, camera);
        });
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer buffer, LeyDebugPayload.Line line, Vec3 camera, float time)
    {
        var heights = line.heights();
        int segments = heights.size() - 1;
        float stepX = (line.toX() - line.fromX()) / (float) segments;
        float stepZ = (line.toZ() - line.fromZ()) / (float) segments;
        float stepLength = (float) Math.sqrt(stepX * stepX + stepZ * stepZ);

        for (int i = 0; i < segments; i++)
        {
            var start = new Vector3f(line.fromX() + 0.5F + stepX * i, heights.get(i) + 0.5F, line.fromZ() + 0.5F + stepZ * i)
                    .sub((float) camera.x, (float) camera.y, (float) camera.z);
            var end = new Vector3f(line.fromX() + 0.5F + stepX * (i + 1), heights.get(i + 1) + 0.5F, line.fromZ() + 0.5F + stepZ * (i + 1))
                    .sub((float) camera.x, (float) camera.y, (float) camera.z);
            if (start.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE && end.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;

            // Travelling wave: crests move from the from node toward the to node
            float distance = (i + 0.5F) * stepLength;
            float pulse = 0.5F + 0.5F * (float) Math.sin((distance - time * PULSE_SPEED) * 2 * Math.PI / PULSE_WAVELENGTH);

            var side = facingSide(start, end);
            drawRibbon(pose, buffer, start, end, side, GLOW_HALF_WIDTH, ARGB.color((int) (40 + 50 * pulse), 120, 80, 255));
            drawRibbon(pose, buffer, start, end, side, CORE_HALF_WIDTH, ARGB.color((int) (140 + 115 * pulse), 200, 170, 255));
        }
    }

    private static void drawNode(PoseStack.Pose pose, VertexConsumer buffer, LeyDebugPayload.Node node, Vec3 camera)
    {
        var base = new Vector3f(node.x() + 0.5F, node.y() + 0.5F, node.z() + 0.5F).sub((float) camera.x, (float) camera.y, (float) camera.z);
        if (base.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE)
            return;

        float height = 3 + node.lines() * 2.5F;
        var top = new Vector3f(base).add(0, height, 0);
        var bottom = new Vector3f(base).sub(0, 1.5F, 0);
        var side = facingSide(bottom, top);

        int rgb = NODE_COLORS[node.kind().ordinal()];
        float halfWidth = 0.25F + 0.07F * node.lines();
        drawRibbon(pose, buffer, bottom, top, side, halfWidth * 2.5F, ARGB.color(60, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
        drawRibbon(pose, buffer, bottom, top, side, halfWidth, ARGB.color(200, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
    }

    /**
     * Unit vector across the segment, perpendicular to both the segment and the view direction, so the ribbon faces the camera.
     */
    private static Vector3f facingSide(Vector3f start, Vector3f end)
    {
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(start).add(end).mul(-0.5F);
        var side = direction.cross(toCamera);
        return side.lengthSquared() < 1.0E-6F ? new Vector3f(1, 0, 0) : side.normalize();
    }

    private static void drawRibbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, Vector3f side, float halfWidth, int color)
    {
        float sx = side.x * halfWidth;
        float sy = side.y * halfWidth;
        float sz = side.z * halfWidth;

        // Both windings so the ribbon survives back-face culling from either side
        buffer.addVertex(pose, start.x - sx, start.y - sy, start.z - sz).setColor(color);
        buffer.addVertex(pose, start.x + sx, start.y + sy, start.z + sz).setColor(color);
        buffer.addVertex(pose, end.x + sx, end.y + sy, end.z + sz).setColor(color);
        buffer.addVertex(pose, end.x - sx, end.y - sy, end.z - sz).setColor(color);

        buffer.addVertex(pose, end.x - sx, end.y - sy, end.z - sz).setColor(color);
        buffer.addVertex(pose, end.x + sx, end.y + sy, end.z + sz).setColor(color);
        buffer.addVertex(pose, start.x + sx, start.y + sy, start.z + sz).setColor(color);
        buffer.addVertex(pose, start.x - sx, start.y - sy, start.z - sz).setColor(color);
    }
}
