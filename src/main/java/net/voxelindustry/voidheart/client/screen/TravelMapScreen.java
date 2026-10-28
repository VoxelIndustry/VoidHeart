package net.voxelindustry.voidheart.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Top-down map of the ley lines around an anchor, north up. Clicking an awakened linked anchor travels to it.
 */
public class TravelMapScreen extends Screen
{
    private static final int MARGIN = 28;
    private static final int MIN_SPAN = 512;
    private static final int HOVER_RADIUS = 8;

    private static final int PANEL_COLOR = 0xE0120E17;
    private static final int BORDER_COLOR = 0xFF3B2C43;
    private static final int LINE_GLOW_COLOR = 0x305B3FA8;
    private static final int LINE_CORE_COLOR = 0xC08E6BE8;
    private static final int MOTE_RGB = 0xD9CCFF;
    private static final float GLOW_HALF_WIDTH = 2.5F;
    private static final float CORE_HALF_WIDTH = 0.6F;
    private static final float MOTE_SPACING = 22;
    private static final float MOTE_SPEED = 9;
    private static final int SOURCE_COLOR = 0xFFE8F8F5;
    private static final int ACTIVE_COLOR = 0xFF36C5B0;
    private static final int DORMANT_COLOR = 0xFF6E5478;
    private static final int HOVER_COLOR = 0xFF73D1DF;

    private final AnchorPayloads.TravelMap map;

    private int left, top, right, bottom;
    private double centerX, centerZ, scale;

    public TravelMapScreen(AnchorPayloads.TravelMap map)
    {
        super(Component.translatable("voidheart.travel_map.title", map.source().name()));
        this.map = map;
    }

    @Override
    protected void init()
    {
        left = MARGIN;
        right = width - MARGIN;
        top = MARGIN + 6;
        bottom = height - MARGIN;

        int minX = map.source().x(), maxX = minX, minZ = map.source().z(), maxZ = minZ;
        for (var anchor : map.linked())
        {
            minX = Math.min(minX, anchor.x());
            maxX = Math.max(maxX, anchor.x());
            minZ = Math.min(minZ, anchor.z());
            maxZ = Math.max(maxZ, anchor.z());
        }
        centerX = (minX + maxX) / 2D;
        centerZ = (minZ + maxZ) / 2D;

        double spanX = Math.max(MIN_SPAN, (maxX - minX) * 1.25);
        double spanZ = Math.max(MIN_SPAN, (maxZ - minZ) * 1.25);
        scale = Math.min((right - left) / spanX, (bottom - top) / spanZ);
    }

    private float screenX(double worldX)
    {
        return (float) ((left + right) / 2D + (worldX - centerX) * scale);
    }

    private float screenY(double worldZ)
    {
        return (float) ((top + bottom) / 2D + (worldZ - centerZ) * scale);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.fill(left, top, right, bottom, PANEL_COLOR);
        graphics.outline(left - 1, top - 1, right - left + 2, bottom - top + 2, BORDER_COLOR);
        graphics.centeredText(font, title, width / 2, top - 12, 0xFFFFFFFF);

        graphics.enableScissor(left, top, right, bottom);
        drawLeyLines(graphics);

        var hovered = hovered(mouseX, mouseY);
        for (var anchor : map.linked())
        {
            int color = anchor == hovered ? HOVER_COLOR : anchor.active() ? ACTIVE_COLOR : DORMANT_COLOR;
            marker(graphics, anchor, anchor.active() ? 5 : 4, color, !anchor.active());
        }
        marker(graphics, map.source(), 6, SOURCE_COLOR, false);
        graphics.disableScissor();

        if (map.linked().isEmpty())
            graphics.centeredText(font, Component.translatable("voidheart.travel_map.empty"), width / 2, bottom - 20, 0xFFB8A9D9);

        if (hovered != null)
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        else if (Math.hypot(mouseX - screenX(map.source().x()), mouseY - screenY(map.source().z())) <= HOVER_RADIUS)
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(map.source().name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                    Component.translatable("voidheart.travel_map.here").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)), mouseX, mouseY);
    }

    /**
     * Each line is a soft glow under a thin core, with motes drifting along it in its flow direction.
     * Lines are centered on their path and every node gets a joint so segments meet cleanly.
     */
    private void drawLeyLines(GuiGraphicsExtractor graphics)
    {
        float drift = (Util.getMillis() % 1_000_000L) / 1000F * MOTE_SPEED;
        var joints = new HashSet<Long>();

        for (var line : map.lines())
        {
            joints.add((long) line.fromX() << 32 | line.fromZ() & 0xFFFFFFFFL);
            joints.add((long) line.toX() << 32 | line.toZ() & 0xFFFFFFFFL);
            line(graphics, screenX(line.fromX()), screenY(line.fromZ()), screenX(line.toX()), screenY(line.toZ()), GLOW_HALF_WIDTH, LINE_GLOW_COLOR);
        }
        for (long joint : joints)
            joint(graphics, screenX((int) (joint >> 32)), screenY((int) joint), GLOW_HALF_WIDTH, LINE_GLOW_COLOR);
        for (var line : map.lines())
            line(graphics, screenX(line.fromX()), screenY(line.fromZ()), screenX(line.toX()), screenY(line.toZ()), CORE_HALF_WIDTH, LINE_CORE_COLOR);
        for (long joint : joints)
            joint(graphics, screenX((int) (joint >> 32)), screenY((int) joint), CORE_HALF_WIDTH + 1, LINE_CORE_COLOR);

        for (var line : map.lines())
            motes(graphics, screenX(line.fromX()), screenY(line.fromZ()), screenX(line.toX()), screenY(line.toZ()), drift);
    }

    private static void motes(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, float drift)
    {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < MOTE_SPACING)
            return;

        for (float along = drift % MOTE_SPACING; along < length; along += MOTE_SPACING)
        {
            // Fade in and out near the nodes so motes don't pop at segment ends
            float fade = Math.min(1, Math.min(along, length - along) / (MOTE_SPACING / 2));
            int alpha = (int) (fade * 255);
            float t = along / length;
            joint(graphics, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 1, alpha << 24 | MOTE_RGB);
        }
    }

    private static void joint(GuiGraphicsExtractor graphics, float x, float y, float radius, int color)
    {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) (Math.PI / 4));
        pose.scale(radius);
        graphics.fill(-1, -1, 1, 1, color);
        pose.popMatrix();
    }

    private void marker(GuiGraphicsExtractor graphics, AnchorView anchor, int size, int color, boolean hollow)
    {
        float x = screenX(anchor.x());
        float y = screenY(anchor.z());

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) (Math.PI / 4));
        graphics.fill(-size, -size, size, size, color);
        if (hollow)
            graphics.fill(-size + 2, -size + 2, size - 2, size - 2, PANEL_COLOR | 0xFF000000);
        pose.popMatrix();

        graphics.centeredText(font, anchor.name(), (int) x, (int) y + size + 5, color);
    }

    /**
     * A band centered on the segment: drawn as a unit square stretched along and across it, so its width is symmetric.
     */
    private static void line(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, float halfWidth, int color)
    {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 1)
            return;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(y2 - y1, x2 - x1));
        pose.scale(length, halfWidth);
        graphics.fill(0, -1, 1, 1, color);
        pose.popMatrix();
    }

    private List<Component> tooltip(AnchorView anchor)
    {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(anchor.name()).withStyle(anchor.active() ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

        int distance = (int) Math.hypot(anchor.x() - map.source().x(), anchor.z() - map.source().z());
        lines.add(Component.translatable("voidheart.travel_map.distance", Component.literal(Integer.toString(distance)).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));

        if (anchor.active())
            lines.add(Component.translatable("voidheart.travel_map.travel").withStyle(ChatFormatting.GREEN));
        else
            lines.add(Component.translatable("voidheart.travel_map.dormant").withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private AnchorView hovered(double mouseX, double mouseY)
    {
        AnchorView closest = null;
        double closestDistance = HOVER_RADIUS;
        for (var anchor : map.linked())
        {
            double distance = Math.hypot(mouseX - screenX(anchor.x()), mouseY - screenY(anchor.z()));
            if (distance <= closestDistance)
            {
                closest = anchor;
                closestDistance = distance;
            }
        }
        return closest;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        var target = hovered(event.x(), event.y());
        if (event.button() == 0 && target != null && target.active())
        {
            ClientPlayNetworking.send(new AnchorPayloads.TravelRequest(map.source().id(), target.id()));
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
