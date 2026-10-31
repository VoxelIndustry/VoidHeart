package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.oculus.OculusControlPanel.Button;
import net.voxelindustry.voidheart.common.content.oculus.OculusControlPayload;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The holographic buttons of every table near the player: zoom out, zoom in, follow and ley lines.
 * Each player sees them on the table edge closest to them, presses are sent to the server.
 */
public final class OculusControls
{
    private static final double REACH = 6;
    private static final double SHOW_DISTANCE = 10;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float TEXT_SCALE = 1 / 110F;

    private static final int TEAL = 0x5BE3D2;
    private static final int FRAME_ALPHA = 190;
    private static final int FILL_ALPHA = 40;
    private static final int HOVER_FILL_ALPHA = 110;
    private static final int ICON_ALPHA = 150;
    private static final int ACTIVE_ICON_ALPHA = 255;
    private static final float FRAME = 0.012F;
    private static final float STROKE = 0.022F;

    private static final Map<BlockPos, OculusControlPanel> PANELS = new HashMap<>();

    private static @Nullable Target hovered;

    private record Target(OculusProjector projector, OculusControlPanel panel, Button button, double distance)
    {
    }

    private OculusControls()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || OculusRenderer.projectors().isEmpty())
        {
            PANELS.clear();
            hovered = null;
            return;
        }

        var cameraState = context.levelState().cameraRenderState;
        float partialTick = cameraState.cameraEntityPartialTicks;
        var eye = player.getEyePosition(partialTick);
        placePanels(eye);
        hovered = target(eye, player.getViewVector(partialTick));

        var poseStack = context.poseStack();
        var collector = context.submitNodeCollector();
        for (var projector : OculusRenderer.projectors())
        {
            var panel = PANELS.get(projector.anchor);
            if (panel == null || panel.center().distanceTo(eye) > SHOW_DISTANCE)
                continue;

            poseStack.pushPose();
            var offset = panel.center().subtract(cameraState.pos);
            poseStack.translate(offset.x, offset.y, offset.z);
            var hoveredButton = hovered != null && hovered.projector() == projector ? hovered.button() : null;
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
                    drawButtons(new Plane(pose, buffer, panel), projector.view(), hoveredButton));
            drawLabels(poseStack, collector, panel, projector.view(), hoveredButton);
            poseStack.popPose();
        }
    }

    private static void placePanels(Vec3 eye)
    {
        var placed = new HashMap<BlockPos, OculusControlPanel>();
        for (var projector : OculusRenderer.projectors())
        {
            var panel = OculusControlPanel.place(projector.footprint(), PANELS.get(projector.anchor), eye);
            if (panel != null)
                placed.put(projector.anchor, panel);
        }
        PANELS.clear();
        PANELS.putAll(placed);
    }

    /**
     * The button under the crosshair, unless a block or entity stands in front of it.
     */
    private static @Nullable Target target(Vec3 eye, Vec3 look)
    {
        Target best = null;
        for (var projector : OculusRenderer.projectors())
        {
            var panel = PANELS.get(projector.anchor);
            var hit = panel == null ? null : panel.hit(eye, look, REACH);
            if (hit != null && (best == null || hit.distance() < best.distance()))
                best = new Target(projector, panel, hit.button(), hit.distance());
        }

        var vanillaHit = Minecraft.getInstance().hitResult;
        if (best != null && vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS && vanillaHit.getLocation().distanceTo(eye) < best.distance())
            return null;
        return best;
    }

    /**
     * Presses the button under the crosshair, if any.
     *
     * @return whether a button took the click, which must then not reach the world
     */
    public static boolean press()
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || PANELS.isEmpty())
            return false;

        var target = target(player.getEyePosition(), player.getViewVector(1));
        if (target == null)
            return false;

        ClientPlayNetworking.send(new OculusControlPayload(target.panel().cell(), target.button().action));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6F));
        return true;
    }

    public static boolean isAimingAtButton()
    {
        return hovered != null;
    }

    public static void clear()
    {
        PANELS.clear();
        hovered = null;
    }

    /**
     * Quads in the panel's own frame: x along the strip, y up, z toward the viewer.
     */
    private record Plane(PoseStack.Pose pose, VertexConsumer buffer, Vec3 right, Vec3 normal)
    {
        Plane(PoseStack.Pose pose, VertexConsumer buffer, OculusControlPanel panel)
        {
            this(pose, buffer, panel.right(), panel.normal());
        }

        void vertex(float x, float y, float z, int color)
        {
            buffer.addVertex(pose, (float) (right.x * x + normal.x * z), y, (float) (right.z * x + normal.z * z)).setColor(color);
        }

        void rect(float x0, float y0, float x1, float y1, float z, int color)
        {
            vertex(x0, y0, z, color);
            vertex(x1, y0, z, color);
            vertex(x1, y1, z, color);
            vertex(x0, y1, z, color);
        }

        /**
         * A stroke of constant width between two points of the plane.
         */
        void line(float x0, float y0, float x1, float y1, float z, int color)
        {
            float length = (float) Math.hypot(x1 - x0, y1 - y0);
            float sideX = -(y1 - y0) / length * STROKE / 2;
            float sideY = (x1 - x0) / length * STROKE / 2;
            vertex(x0 - sideX, y0 - sideY, z, color);
            vertex(x1 - sideX, y1 - sideY, z, color);
            vertex(x1 + sideX, y1 + sideY, z, color);
            vertex(x0 + sideX, y0 + sideY, z, color);
        }
    }

    private static void drawButtons(Plane plane, OculusView view, @Nullable Button hoveredButton)
    {
        float half = OculusControlPanel.BUTTON / 2;
        for (var button : Button.values())
        {
            float center = button.offset();
            plane.rect(center - half, -half, center + half, half, 0, teal(button == hoveredButton ? HOVER_FILL_ALPHA : FILL_ALPHA));

            int frame = teal(FRAME_ALPHA);
            plane.rect(center - half, half - FRAME, center + half, half, 0.002F, frame);
            plane.rect(center - half, -half, center + half, -half + FRAME, 0.002F, frame);
            plane.rect(center - half, -half + FRAME, center - half + FRAME, half - FRAME, 0.002F, frame);
            plane.rect(center + half - FRAME, -half + FRAME, center + half, half - FRAME, 0.002F, frame);

            boolean active = switch (button)
            {
                case ZOOM_OUT -> view.zoom() > 0;
                case ZOOM_IN -> view.zoom() < OculusView.ZOOM_STEPS - 1;
                case FOLLOW -> view.follow().isPresent();
                case LEY_LINES -> view.leyLines();
            };
            drawIcon(plane, button, center, teal(active ? ACTIVE_ICON_ALPHA : ICON_ALPHA / 2));
        }
    }

    private static void drawIcon(Plane plane, Button button, float x, int color)
    {
        float z = 0.004F;
        switch (button)
        {
            case ZOOM_OUT -> plane.line(x - 0.06F, 0, x + 0.06F, 0, z, color);
            case ZOOM_IN ->
            {
                plane.line(x - 0.06F, 0, x + 0.06F, 0, z, color);
                plane.line(x, -0.06F, x, 0.06F, z, color);
            }
            case FOLLOW ->
            {
                // A reticle: four corner ticks around a dot
                float outer = 0.065F, inner = 0.03F;
                plane.line(x - outer, outer, x - inner, outer, z, color);
                plane.line(x + inner, outer, x + outer, outer, z, color);
                plane.line(x - outer, -outer, x - inner, -outer, z, color);
                plane.line(x + inner, -outer, x + outer, -outer, z, color);
                plane.line(x - outer, outer, x - outer, inner, z, color);
                plane.line(x - outer, -inner, x - outer, -outer, z, color);
                plane.line(x + outer, outer, x + outer, inner, z, color);
                plane.line(x + outer, -inner, x + outer, -outer, z, color);
                plane.rect(x - 0.015F, -0.015F, x + 0.015F, 0.015F, z, color);
            }
            case LEY_LINES ->
            {
                // A ley line winding between two nodes
                plane.line(x - 0.07F, -0.04F, x - 0.02F, 0.04F, z, color);
                plane.line(x - 0.02F, 0.04F, x + 0.02F, -0.04F, z, color);
                plane.line(x + 0.02F, -0.04F, x + 0.07F, 0.04F, z, color);
                plane.rect(x - 0.085F, -0.055F, x - 0.055F, -0.025F, z, color);
                plane.rect(x + 0.055F, 0.025F, x + 0.085F, 0.055F, z, color);
            }
        }
    }

    private static int teal(int alpha)
    {
        return ARGB.color(alpha, ARGB.red(TEAL), ARGB.green(TEAL), ARGB.blue(TEAL));
    }

    private static void drawLabels(PoseStack poseStack, SubmitNodeCollector collector, OculusControlPanel panel, OculusView view, @Nullable Button hoveredButton)
    {
        var right = panel.right();
        poseStack.pushPose();
        poseStack.rotate(new Quaternionf().rotationY((float) Math.atan2(-right.z, right.x)));

        drawText(poseStack, collector, status(view), OculusControlPanel.BUTTON / 2 + 0.07F, 0xFF9FF3E8);
        if (hoveredButton != null)
            drawText(poseStack, collector, hint(hoveredButton, view), -OculusControlPanel.BUTTON / 2 - 0.05F, 0xFFE8FFFC);
        poseStack.popPose();
    }

    private static void drawText(PoseStack poseStack, SubmitNodeCollector collector, Component text, float y, int color)
    {
        var font = Minecraft.getInstance().font;
        var line = text.getVisualOrderText();
        poseStack.pushPose();
        poseStack.translate(0, y, 0.006F);
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        collector.submitText(poseStack, -font.width(line) / 2F, -font.lineHeight / 2F, line, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0);
        poseStack.popPose();
    }

    private static Component status(OculusView view)
    {
        var status = Component.translatable("voidheart.oculus.scale", view.scale());
        view.follow().ifPresentOrElse(
                target -> status.append("  ").append(Component.translatable("voidheart.oculus.following", playerName(target))),
                () -> status.append("  ").append(Component.translatable("voidheart.oculus.centered")));
        return status;
    }

    private static Component hint(Button button, OculusView view)
    {
        return switch (button)
        {
            case ZOOM_OUT -> Component.translatable("voidheart.oculus.zoom_out");
            case ZOOM_IN -> Component.translatable("voidheart.oculus.zoom_in");
            case FOLLOW -> Component.translatable("voidheart.oculus.follow");
            case LEY_LINES -> Component.translatable(view.leyLines() ? "voidheart.oculus.ley_lines.hide" : "voidheart.oculus.ley_lines.show");
        };
    }

    private static Component playerName(UUID id)
    {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info == null
                ? Component.translatable("voidheart.oculus.unknown_player").withStyle(ChatFormatting.ITALIC)
                : Component.literal(info.getProfile().name());
    }
}
