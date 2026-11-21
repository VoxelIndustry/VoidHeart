package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.energy.GlowDraw;
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

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
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
        double stepX = (line.toX() - line.fromX()) / (double) segments;
        double stepZ = (line.toZ() - line.fromZ()) / (double) segments;
        float stepLength = (float) Math.sqrt(stepX * stepX + stepZ * stepZ);

        for (int i = 0; i < segments; i++)
        {
            var start = GlowDraw.relative(line.fromX() + 0.5 + stepX * i, heights.get(i) + 0.5, line.fromZ() + 0.5 + stepZ * i, camera);
            var end = GlowDraw.relative(line.fromX() + 0.5 + stepX * (i + 1), heights.get(i + 1) + 0.5, line.fromZ() + 0.5 + stepZ * (i + 1), camera);
            if (start.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE && end.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;

            // Travelling wave: crests move from the from node toward the to node
            float distance = (i + 0.5F) * stepLength;
            float pulse = 0.5F + 0.5F * (float) Math.sin((distance - time * PULSE_SPEED) * 2 * Math.PI / PULSE_WAVELENGTH);

            var side = GlowDraw.facingSide(start, end);
            GlowDraw.ribbon(pose, buffer, start, end, side, GLOW_HALF_WIDTH, ARGB.color((int) (40 + 50 * pulse), 120, 80, 255));
            GlowDraw.ribbon(pose, buffer, start, end, side, CORE_HALF_WIDTH, ARGB.color((int) (140 + 115 * pulse), 200, 170, 255));
        }
    }

    private static void drawNode(PoseStack.Pose pose, VertexConsumer buffer, LeyDebugPayload.Node node, Vec3 camera)
    {
        var base = GlowDraw.relative(node.x() + 0.5, node.y() + 0.5, node.z() + 0.5, camera);
        if (base.lengthSquared() > DRAW_DISTANCE * DRAW_DISTANCE)
            return;

        float height = 3 + node.lines() * 2.5F;
        var top = new Vector3f(base).add(0, height, 0);
        var bottom = new Vector3f(base).sub(0, 1.5F, 0);
        var side = GlowDraw.facingSide(bottom, top);

        int rgb = NODE_COLORS[node.kind().ordinal()];
        float halfWidth = 0.25F + 0.07F * node.lines();
        GlowDraw.ribbon(pose, buffer, bottom, top, side, halfWidth * 2.5F, ARGB.color(60, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
        GlowDraw.ribbon(pose, buffer, bottom, top, side, halfWidth, ARGB.color(200, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
    }

}
