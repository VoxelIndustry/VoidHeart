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

    private static final int VIOLET = 0xB45CFF;
    private static final int SYMBOL_BRIGHT = 0xFFEAD9FF;
    private static final int SYMBOL_DIM_ALPHA = 110;
    private static final int AURA_ALPHA = 30;
    private static final int AURA_GAZED_ALPHA = 90;
    private static final int RING_ALPHA = 170;
    private static final int RUNE_ALPHA = 130;
    private static final int RUNE_MARKS = 9;
    private static final int CIRCLE_SEGMENTS = 24;
    private static final float STROKE = 0.012F;
    private static final int STATUS_COLOR = 0xFFCDB4FF;
    private static final int HINT_COLOR = 0xFFF0E6FF;

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
            if (projector.view().asleep())
                continue;
            var panel = PANELS.get(projector.anchor);
            if (panel == null || panel.center().distanceTo(eye) > SHOW_DISTANCE)
                continue;

            poseStack.pushPose();
            var offset = panel.center().subtract(cameraState.pos);
            poseStack.translate(offset.x, offset.y, offset.z);
            var hoveredButton = hovered != null && hovered.projector() == projector ? hovered.button() : null;
            float seconds = (System.nanoTime() % 3_600_000_000_000L) / 1E9F;
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
                    drawAuras(new Plane(pose, buffer, panel), hoveredButton));
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) ->
                    drawSigils(new Plane(pose, buffer, panel), projector.view(), hoveredButton, seconds));
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
            if (projector.view().asleep())
                continue;
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

        /**
         * A soft disc, as a fan of triangles folded into quads.
         */
        void disc(float x, float y, float radius, float z, int color)
        {
            for (int i = 0; i < CIRCLE_SEGMENTS; i++)
            {
                double from = i * Math.PI * 2 / CIRCLE_SEGMENTS;
                double to = (i + 1) * Math.PI * 2 / CIRCLE_SEGMENTS;
                vertex(x, y, z, color);
                vertex(x, y, z, color);
                vertex(x + (float) Math.cos(to) * radius, y + (float) Math.sin(to) * radius, z, color);
                vertex(x + (float) Math.cos(from) * radius, y + (float) Math.sin(from) * radius, z, color);
            }
        }

        /**
         * A stroked arc around a center, angles in radians.
         */
        void arc(float x, float y, float radius, double from, double to, int segments, float z, int color)
        {
            for (int i = 0; i < segments; i++)
            {
                double a0 = from + (to - from) * i / segments;
                double a1 = from + (to - from) * (i + 1) / segments;
                line(x + (float) Math.cos(a0) * radius, y + (float) Math.sin(a0) * radius,
                        x + (float) Math.cos(a1) * radius, y + (float) Math.sin(a1) * radius, z, color);
            }
        }
    }

    /**
     * Each button is a sigil: a dim aura, a ring of turning rune marks and a carved symbol in the middle.
     * Gazing at one makes its aura flare and its runes turn faster.
     */
    private static void drawAuras(Plane plane, @Nullable Button hoveredButton)
    {
        for (var button : Button.values())
            plane.disc(button.offset(), 0, OculusControlPanel.BUTTON * 0.62F, 0, violet(button == hoveredButton ? AURA_GAZED_ALPHA : AURA_ALPHA));
    }

    private static void drawSigils(Plane plane, OculusView view, @Nullable Button hoveredButton, float seconds)
    {
        float radius = OculusControlPanel.BUTTON / 2;
        for (var button : Button.values())
        {
            float x = button.offset();
            boolean gazed = button == hoveredButton;
            plane.arc(x, 0, radius, 0, Math.PI * 2, CIRCLE_SEGMENTS, 0.002F, violet(gazed ? 255 : RING_ALPHA));

            // Marks of uneven length, as if carved, turning around the ring
            double turn = seconds * (gazed ? 1.2 : 0.25) * (button.ordinal() % 2 == 0 ? 1 : -1);
            for (int mark = 0; mark < RUNE_MARKS; mark++)
            {
                double angle = turn + mark * Math.PI * 2 / RUNE_MARKS;
                float inner = radius * (0.68F + 0.12F * ((mark * 7 + button.ordinal() * 3) % 4) / 3);
                plane.line(x + (float) Math.cos(angle) * inner, (float) Math.sin(angle) * inner,
                        x + (float) Math.cos(angle) * radius * 0.88F, (float) Math.sin(angle) * radius * 0.88F, 0.003F, violet(gazed ? 230 : RUNE_ALPHA));
            }

            boolean active = switch (button)
            {
                case ZOOM_OUT -> view.zoom() > 0;
                case ZOOM_IN -> view.zoom() < OculusView.ZOOM_STEPS - 1;
                case FOLLOW -> view.follow().isPresent();
                case LEY_LINES -> view.leyLines();
            };
            drawSymbol(plane, button, x, active || gazed ? SYMBOL_BRIGHT : ARGB.color(SYMBOL_DIM_ALPHA, ARGB.red(SYMBOL_BRIGHT), ARGB.green(SYMBOL_BRIGHT), ARGB.blue(SYMBOL_BRIGHT)));
        }
    }

    private static void drawSymbol(Plane plane, Button button, float x, int color)
    {
        float z = 0.004F;
        switch (button)
        {
            // An eye opening wide on the land
            case ZOOM_IN ->
            {
                plane.arc(x, -0.035F, 0.055F, Math.PI * 0.2, Math.PI * 0.8, 6, z, color);
                plane.arc(x, 0.035F, 0.055F, Math.PI * 1.2, Math.PI * 1.8, 6, z, color);
                plane.disc(x, 0, 0.016F, z, color);
            }
            // The same eye closing, its lashes down
            case ZOOM_OUT ->
            {
                plane.arc(x, 0.035F, 0.055F, Math.PI * 1.2, Math.PI * 1.8, 6, z, color);
                plane.line(x - 0.025F, -0.012F, x - 0.035F, -0.035F, z, color);
                plane.line(x, -0.017F, x, -0.042F, z, color);
                plane.line(x + 0.025F, -0.012F, x + 0.035F, -0.035F, z, color);
            }
            // A watching sign: a downturned triangle holding an eye's pupil
            case FOLLOW ->
            {
                plane.line(x - 0.05F, 0.035F, x + 0.05F, 0.035F, z, color);
                plane.line(x + 0.05F, 0.035F, x, -0.05F, z, color);
                plane.line(x, -0.05F, x - 0.05F, 0.035F, z, color);
                plane.disc(x, 0.008F, 0.014F, z, color);
            }
            // A ley line winding between two nodes
            case LEY_LINES ->
            {
                plane.arc(x - 0.025F, 0, 0.025F, Math.PI, Math.PI * 2, 5, z, color);
                plane.arc(x + 0.025F, 0, 0.025F, 0, Math.PI, 5, z, color);
                plane.disc(x - 0.058F, 0, 0.012F, z, color);
                plane.disc(x + 0.058F, 0, 0.012F, z, color);
            }
        }
    }

    private static int violet(int alpha)
    {
        return ARGB.color(alpha, ARGB.red(VIOLET), ARGB.green(VIOLET), ARGB.blue(VIOLET));
    }

    private static void drawLabels(PoseStack poseStack, SubmitNodeCollector collector, OculusControlPanel panel, OculusView view, @Nullable Button hoveredButton)
    {
        var right = panel.right();
        poseStack.pushPose();
        poseStack.rotate(new Quaternionf().rotationY((float) Math.atan2(-right.z, right.x)));

        drawText(poseStack, collector, status(view), OculusControlPanel.BUTTON / 2 + 0.07F, STATUS_COLOR);
        if (hoveredButton != null)
            drawText(poseStack, collector, hint(hoveredButton, view), -OculusControlPanel.BUTTON / 2 - 0.05F, HINT_COLOR);
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
