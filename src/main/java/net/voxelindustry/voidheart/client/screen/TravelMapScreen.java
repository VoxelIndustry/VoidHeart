package net.voxelindustry.voidheart.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorPayloads;
import net.voxelindustry.voidheart.common.ley.anchor.AnchorView;

import java.util.ArrayList;
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
    private static final int LINE_COLOR = 0x996B4FB8;
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
        for (var line : map.lines())
            line(graphics, screenX(line.fromX()), screenY(line.fromZ()), screenX(line.toX()), screenY(line.toZ()), LINE_COLOR);

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
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(map.source().name()), Component.translatable("voidheart.travel_map.here")), mouseX, mouseY);
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

    private static void line(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color)
    {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 1)
            return;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(y2 - y1, x2 - x1));
        graphics.fill(0, 0, Math.round(length), 1, color);
        pose.popMatrix();
    }

    private List<Component> tooltip(AnchorView anchor)
    {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(anchor.name()));
        int distance = (int) Math.hypot(anchor.x() - map.source().x(), anchor.z() - map.source().z());
        lines.add(Component.translatable("voidheart.anchor.distance", distance));
        lines.add(Component.translatable(anchor.active() ? "voidheart.travel_map.travel" : "voidheart.travel_map.dormant"));
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
