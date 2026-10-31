package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.oculus.OculusLeyPayloads;
import net.voxelindustry.voidheart.common.ley.LeyDebugPayload;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorView;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The ley layer of a hologram: flowing ley lines, node pillars and Old Anchors, asked from the server while shown.
 */
public final class OculusLeyLayer
{
    private static final int CHECK_INTERVAL = 20;
    private static final int REFRESH_AGE = 600;
    /**
     * Share of the gathered radius the view may drift before the layer is gathered again around it.
     */
    private static final double DRIFT = 0.25;

    /**
     * Pieces lines are cut into, in hologram blocks, so they clip cleanly at the table edges.
     */
    private static final float PIECE_LENGTH = 0.05F;
    private static final int MAX_PIECES = 400;
    private static final float CORE_HALF_WIDTH = 0.009F;
    private static final float GLOW_HALF_WIDTH = 0.035F;
    private static final float PULSE_WAVELENGTH = 24;
    private static final float PULSE_SPEED = 0.6F;
    private static final int[] NODE_COLORS = { 0xB8A9D9, 0x6FCF97, 0xF2C94C, 0xEB5757 };
    private static final int ACTIVE_ANCHOR_COLOR = 0x36C5B0;
    private static final int DORMANT_ANCHOR_COLOR = 0x8A6A94;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float NAME_SCALE = 1 / 110F;

    private record Received(OculusLeyPayloads.Layer layer, int scale, long tick)
    {
    }

    private static final Map<BlockPos, Received> LAYERS = new HashMap<>();
    private static final Map<BlockPos, Integer> SCALES_ASKED = new HashMap<>();

    private OculusLeyLayer()
    {
    }

    public static void tick(long tick)
    {
        if (tick % CHECK_INTERVAL != 0)
            return;

        for (var projector : OculusRenderer.projectors())
        {
            if (projector.view().asleep())
                continue;
            if (!projector.view().leyLines())
            {
                LAYERS.remove(projector.anchor);
                continue;
            }

            var received = LAYERS.get(projector.anchor);
            var center = projector.displayCenter();
            boolean stale = received == null
                    || received.scale() != projector.scale()
                    || tick - received.tick() > REFRESH_AGE
                    || Math.hypot(center.x - received.layer().centerX(), center.z - received.layer().centerZ()) > received.layer().radius() * DRIFT;
            if (stale)
            {
                SCALES_ASKED.put(projector.anchor, projector.scale());
                ClientPlayNetworking.send(new OculusLeyPayloads.Request(projector.anchor));
            }
        }
        LAYERS.keySet().removeIf(anchor -> OculusRenderer.projectors().stream().noneMatch(projector -> projector.anchor.equals(anchor)));
    }

    public static void receive(OculusLeyPayloads.Layer layer, ClientPlayNetworking.Context context)
    {
        LAYERS.put(layer.table(), new Received(layer, SCALES_ASKED.getOrDefault(layer.table(), 0), OculusRenderer.ticks()));
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        if (LAYERS.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState;
        float time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;
        for (var projector : OculusRenderer.projectors())
        {
            if (projector.view().asleep())
                continue;
            var received = LAYERS.get(projector.anchor);
            if (received == null || !projector.view().leyLines())
                continue;

            var layer = received.layer();
            // Halos add light, cores are blended over the land so their hue survives on bright ground
            for (boolean core : new boolean[] { false, true })
            {
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), core ? VoidHeartRenderTypes.MARKER : VoidHeartRenderTypes.GLOW, (pose, buffer) ->
                {
                    for (var line : layer.lines())
                        drawLine(pose, buffer, projector, line, camera.pos, time, core);
                    for (var node : layer.nodes())
                        drawNode(pose, buffer, projector, node, camera.pos, core);
                    if (!core)
                    {
                        for (var anchor : layer.anchors())
                            drawAnchor(pose, buffer, projector, anchor, camera.pos);
                    }
                });
            }
            for (var anchor : layer.anchors())
                drawAnchorName(context, camera, projector, anchor);
        }
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer buffer, OculusProjector projector, LeyDebugPayload.Line line, Vec3 camera, float time, boolean core)
    {
        var heights = line.heights();
        double dx = line.toX() - line.fromX();
        double dz = line.toZ() - line.fromZ();
        double length = Math.hypot(dx, dz);
        int pieces = (int) Math.min(MAX_PIECES, Math.ceil(length / projector.scale() / PIECE_LENGTH));
        if (pieces <= 0 || heights.size() < 2)
            return;

        var previous = pointOn(projector, line, 0);
        for (int piece = 1; piece <= pieces; piece++)
        {
            double t = piece / (double) pieces;
            var next = pointOn(projector, line, t);
            if (previous != null && next != null)
            {
                // Crests travel from the from node toward the to node, as in the debug view
                float distance = (float) ((t - 0.5 / pieces) * length);
                float pulse = 0.5F + 0.5F * (float) Math.sin((distance - time * PULSE_SPEED) * 2 * Math.PI / PULSE_WAVELENGTH);
                var start = previous.subtract(camera).toVector3f();
                var end = next.subtract(camera).toVector3f();
                var side = facingSide(start, end);
                if (core)
                    ribbon(pose, buffer, start, end, side, CORE_HALF_WIDTH, ARGB.color(255, (int) (150 + 70 * pulse), (int) (90 + 70 * pulse), 255));
                else
                    ribbon(pose, buffer, start, end, side, GLOW_HALF_WIDTH, ARGB.color((int) (50 + 60 * pulse), 140, 80, 255));
            }
            previous = next;
        }
    }

    /**
     * A point along a line in hologram space, its height taken from the sampled terrain profile.
     */
    private static Vec3 pointOn(OculusProjector projector, LeyDebugPayload.Line line, double t)
    {
        var heights = line.heights();
        double index = t * (heights.size() - 1);
        int below = (int) Math.floor(index);
        int above = Math.min(below + 1, heights.size() - 1);
        double height = heights.get(below) + (heights.get(above) - heights.get(below)) * (index - below);
        return projector.toHologramClamped(
                line.fromX() + 0.5 + (line.toX() - line.fromX()) * t,
                height + 1,
                line.fromZ() + 0.5 + (line.toZ() - line.fromZ()) * t);
    }

    private static void drawNode(PoseStack.Pose pose, VertexConsumer buffer, OculusProjector projector, LeyDebugPayload.Node node, Vec3 camera, boolean core)
    {
        var base = projector.toHologramClamped(node.x() + 0.5, node.y() + 1, node.z() + 0.5);
        if (base == null)
            return;

        var bottom = base.subtract(camera).toVector3f();
        var top = new Vector3f(bottom).add(0, 0.08F + node.lines() * 0.02F, 0);
        var side = facingSide(bottom, top);
        int rgb = NODE_COLORS[node.kind().ordinal()];
        float halfWidth = 0.01F + 0.002F * node.lines();
        if (core)
        {
            ribbon(pose, buffer, bottom, top, side, halfWidth, ARGB.color(255, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
            gem(pose, buffer, top, 0.025F + 0.006F * node.lines(), ARGB.color(255, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
        }
        else
        {
            ribbon(pose, buffer, bottom, top, side, halfWidth * 3, ARGB.color(70, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
            gem(pose, buffer, top, (0.025F + 0.006F * node.lines()) * 2.2F, ARGB.color(80, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
        }
    }

    /**
     * An upright diamond turned toward the camera, centered on the point.
     */
    private static void gem(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, float size, int color)
    {
        var toCamera = new Vector3f(-center.x, 0, -center.z);
        if (toCamera.lengthSquared() < 1.0E-6F)
            toCamera.set(0, 0, 1);
        var side = new Vector3f(toCamera.z, 0, -toCamera.x).normalize(size * 0.7F);
        buffer.addVertex(pose, center.x, center.y - size, center.z).setColor(color);
        buffer.addVertex(pose, center.x + side.x, center.y, center.z + side.z).setColor(color);
        buffer.addVertex(pose, center.x, center.y + size, center.z).setColor(color);
        buffer.addVertex(pose, center.x - side.x, center.y, center.z - side.z).setColor(color);
    }

    private static void drawAnchor(PoseStack.Pose pose, VertexConsumer buffer, OculusProjector projector, AnchorView anchor, Vec3 camera)
    {
        var position = projector.toHologramClamped(anchor.x() + 0.5, anchor.y() + 1, anchor.z() + 0.5);
        if (position == null)
            return;

        var center = position.add(0, 0.06, 0).subtract(camera).toVector3f();
        int rgb = anchor.active() ? ACTIVE_ANCHOR_COLOR : DORMANT_ANCHOR_COLOR;
        gem(pose, buffer, center, 0.045F * 2.2F, ARGB.color(70, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
        gem(pose, buffer, center, 0.045F, ARGB.color(230, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb)));
    }

    private static void drawAnchorName(LevelRenderContext context, CameraRenderState camera, OculusProjector projector, AnchorView anchor)
    {
        var position = projector.toHologramClamped(anchor.x() + 0.5, anchor.y() + 1, anchor.z() + 0.5);
        if (position == null)
            return;

        var font = Minecraft.getInstance().font;
        var name = Component.literal(anchor.name()).getVisualOrderText();
        var offset = position.add(0, 0.16, 0).subtract(camera.pos);
        var poseStack = context.poseStack();

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(NAME_SCALE, -NAME_SCALE, NAME_SCALE);
        context.submitNodeCollector().submitText(poseStack, -font.width(name) / 2F, 0, name, false, Font.DisplayMode.NORMAL, FULL_BRIGHT,
                anchor.active() ? 0xFF9FF3E8 : 0xFFB8A9C9, 0x40000000, 0);
        poseStack.popPose();
    }

    /**
     * Unit vector across the segment, perpendicular to both the segment and the view direction, so the ribbon faces the camera.
     */
    private static Vector3f facingSide(Vector3f start, Vector3f end)
    {
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(start).add(end).mul(-0.5F);
        var side = direction.cross(toCamera);
        return side.lengthSquared() < 1.0E-12F ? new Vector3f(1, 0, 0) : side.normalize();
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, Vector3f side, float halfWidth, int color)
    {
        float sx = side.x * halfWidth;
        float sy = side.y * halfWidth;
        float sz = side.z * halfWidth;
        buffer.addVertex(pose, start.x - sx, start.y - sy, start.z - sz).setColor(color);
        buffer.addVertex(pose, start.x + sx, start.y + sy, start.z + sz).setColor(color);
        buffer.addVertex(pose, end.x + sx, end.y + sy, end.z + sz).setColor(color);
        buffer.addVertex(pose, end.x - sx, end.y - sy, end.z - sz).setColor(color);
    }

    public static void clear()
    {
        LAYERS.clear();
        SCALES_ASKED.clear();
    }
}
