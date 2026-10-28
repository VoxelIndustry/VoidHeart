package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorView;
import org.joml.Vector3f;

/**
 * Waypoints toward the anchors linked to the one the player stands by. Far anchors are pinned at a fixed
 * distance in their direction so they stay readable, close ones float above the anchor itself.
 */
public class AnchorWaypointRender
{
    private static final double MARKER_DISTANCE = 16;
    private static final float LABEL_SCALE = 1.5F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final int ACTIVE_COLOR = 0x36C5B0;
    private static final int DORMANT_COLOR = 0x8A6A94;

    private static volatile AnchorPayloads.Waypoints waypoints = AnchorPayloads.Waypoints.CLEAR;

    public static void receive(AnchorPayloads.Waypoints payload, ClientPlayNetworking.Context context)
    {
        waypoints = payload;
    }

    public static void clear()
    {
        waypoints = AnchorPayloads.Waypoints.CLEAR;
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var current = waypoints;
        if (current.source().isEmpty() || current.linked().isEmpty())
            return;

        var cameraState = context.levelState().cameraRenderState;
        var camera = cameraState.pos;
        var poseStack = context.poseStack();
        var collector = context.submitNodeCollector();

        for (var anchor : current.linked())
        {
            var target = new Vec3(anchor.x() + 0.5, anchor.y() + 2.6, anchor.z() + 0.5);
            var offset = target.subtract(camera);
            double distance = offset.length();
            var marker = distance <= MARKER_DISTANCE ? offset : offset.scale(MARKER_DISTANCE / distance);

            poseStack.pushPose();
            poseStack.translate(marker.x, marker.y, marker.z);

            int color = anchor.active() ? ACTIVE_COLOR : DORMANT_COLOR;
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) -> drawDiamond(pose, buffer, marker, color));

            poseStack.translate(0, 0.45, 0);
            poseStack.scale(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);
            collector.submitNameTag(poseStack, Vec3.ZERO, 0, label(anchor, distance), true, FULL_BRIGHT, cameraState);
            poseStack.popPose();
        }
    }

    private static Component label(AnchorView anchor, double distance)
    {
        var label = Component.literal(anchor.name()).withStyle(anchor.active() ? ChatFormatting.AQUA : ChatFormatting.GRAY);
        label.append(Component.literal("  ").append(Component.translatable("voidheart.anchor.distance", (int) distance)).withStyle(ChatFormatting.WHITE));
        if (!anchor.active())
            label.append(Component.literal("  ").append(Component.translatable("voidheart.anchor.dormant_tag")).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        return label;
    }

    /**
     * A small upright diamond facing the camera, centered on the pose origin.
     */
    private static void drawDiamond(PoseStack.Pose pose, VertexConsumer buffer, Vec3 marker, int rgb)
    {
        var toCamera = new Vector3f((float) -marker.x, 0, (float) -marker.z);
        if (toCamera.lengthSquared() < 1.0E-4F)
            toCamera.set(0, 0, 1);
        var side = new Vector3f(toCamera.z, 0, -toCamera.x).normalize(0.18F);

        for (int layer = 0; layer < 2; layer++)
        {
            float grow = layer == 0 ? 2.2F : 1;
            int color = ARGB.color(layer == 0 ? 70 : 220, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            float sx = side.x * grow, sz = side.z * grow, h = 0.26F * grow;

            buffer.addVertex(pose, 0, -h, 0).setColor(color);
            buffer.addVertex(pose, sx, 0, sz).setColor(color);
            buffer.addVertex(pose, 0, h, 0).setColor(color);
            buffer.addVertex(pose, -sx, 0, -sz).setColor(color);
        }
    }
}
