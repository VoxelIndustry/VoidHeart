package net.voxelindustry.voidheart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Under Insight, a row under a Kernacian Dragoon's boss bar shows how far it has adapted to each kind of damage, a kind that just went up flashing once.
 */
public final class DragoonReadout
{
    public static final int  OFFSET_Y     = 7;
    /**
     * Seven cells of this width fill the boss bar's 182 pixels.
     */
    public static final int  CELL_WIDTH   = 26;
    public static final int  ICON_SIZE    = 7;
    public static final int  BAR_X        = 9;
    public static final int  BAR_Y        = 2;
    public static final int  BAR_WIDTH    = 15;
    public static final int  BAR_HEIGHT   = 3;
    public static final int  BAR_BACK     = 0xFF1B1017;
    public static final int  BAR_FILL     = 0xFF8A3DF0;
    public static final int  BAR_FULL     = 0xFFD9C8FF;
    public static final int  FLASH        = 0xFFFFFFFF;
    public static final long FLASH_MILLIS = 500;
    public static final double SEARCH_RANGE = 64;

    private static final Identifier[] ICONS = new Identifier[Kind.values().length];

    static
    {
        for (var kind : Kind.values())
            ICONS[kind.ordinal()] = Identifier.fromNamespaceAndPath(MODID, "insight/" + kind.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Last counts seen and when each kind last rose, per dragoon.
     */
    private static final Map<Integer, Seen> SEEN = new HashMap<>();

    private DragoonReadout()
    {
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, BossEvent event)
    {
        if (!InsightSight.active())
            return;
        var dragoon = dragoonOf(event);
        if (dragoon == null)
            return;

        int packed = dragoon.adaptation();
        // Entity ids are never reused within a session, so only a handful of dragoons can pile up
        if (SEEN.size() > 16)
            SEEN.clear();
        var seen = SEEN.computeIfAbsent(dragoon.getId(), id -> new Seen(packed));
        long now = Util.getMillis();
        seen.update(packed, now);

        int top = y + OFFSET_Y;
        for (var kind : Kind.values())
        {
            int left = x + 1 + kind.ordinal() * CELL_WIDTH;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICONS[kind.ordinal()], left, top, ICON_SIZE, ICON_SIZE);

            int count = DragoonAdaptation.count(packed, kind);
            int barLeft = left + BAR_X;
            int barTop = top + BAR_Y;
            graphics.fill(RenderPipelines.GUI, barLeft, barTop, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT, BAR_BACK);
            int filled = Math.round(BAR_WIDTH * (float) count / DragoonAdaptation.MAX_HITS);
            boolean flashing = now - seen.rose[kind.ordinal()] < FLASH_MILLIS;
            int color = flashing ? FLASH : count >= DragoonAdaptation.MAX_HITS ? BAR_FULL : BAR_FILL;
            if (filled > 0)
                graphics.fill(RenderPipelines.GUI, barLeft, barTop, barLeft + filled, barTop + BAR_HEIGHT, color);
        }
    }

    /**
     * The bar's own id is the dragoon's first UUID, which loading it from disk replaces, so fall back on the nearest dragoon of the same name.
     */
    private static @Nullable KernacianDragoon dragoonOf(BossEvent event)
    {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
            return null;
        var player = minecraft.player;
        var near = minecraft.level.getEntitiesOfClass(KernacianDragoon.class, player.getBoundingBox().inflate(SEARCH_RANGE), dragoon -> !dragoon.isRemoved());
        for (var dragoon : near)
        {
            if (dragoon.getUUID().equals(event.getId()))
                return dragoon;
        }
        return near.stream()
                .filter(dragoon -> dragoon.getDisplayName().getString().equals(event.getName().getString()))
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static final class Seen
    {
        private int packed;
        private final long[] rose = new long[Kind.values().length];

        private Seen(int packed)
        {
            this.packed = packed;
            Arrays.fill(rose, Long.MIN_VALUE / 2);
        }

        private void update(int now, long millis)
        {
            if (now == packed)
                return;
            for (var kind : Kind.values())
            {
                if (DragoonAdaptation.count(now, kind) > DragoonAdaptation.count(packed, kind))
                    rose[kind.ordinal()] = millis;
            }
            packed = now;
        }
    }
}
