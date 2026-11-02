package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import static net.voxelindustry.voidheart.client.screen.ResearchMapView.ATLAS;
import static net.voxelindustry.voidheart.client.screen.ResearchMapView.SPRITES;

/**
 * A quill and an unlit lantern, how many insights are kept beside a mote like those around the eye, and along the bottom how close the next one is,
 * or once it is in reach, a dash for each one the knowledge held would pay for.
 * Crafting one sets the quill writing and lights the lantern for a few moments. The quill stays dim while there is nothing to write with.
 */
class ResearchInsightButton extends Button
{
    static final int WIDTH  = 74;
    static final int HEIGHT = 24;

    private static final int PLATE_COLOR  = 0xFF0B0810;
    private static final int READY_COLOR  = 0xFF16302E;
    private static final int BORDER_COLOR = 0xFF4A3860;
    private static final int LIVE_COLOR   = 0xFF36C5B0;
    private static final int HOVER_COLOR  = 0xFF7CE8D6;
    private static final int TRACK_COLOR  = 0xFF1A1424;
    private static final int CHARGE_COLOR = 0xFF2A7F74;
    private static final int COUNT_COLOR  = 0xFFA899BC;
    private static final int QUILL_DIM    = 0xFF8A7A99;
    private static final int FLAME_RGB    = 0x7CE8D6;

    private static final int ROW         = 96;
    private static final int LANTERN_W   = 14;
    private static final int LANTERN_H   = 18;
    private static final int FLAME_U     = 16;
    private static final int FLAME_W     = 6;
    private static final int FLAME_H     = 8;
    private static final int GLASS_U     = 24;
    private static final int GLASS_W     = 8;
    private static final int GLASS_H     = 8;
    private static final int HALO_U      = 40;
    private static final int HALO_SIZE   = 32;
    private static final int QUILL_U     = 76;
    private static final int QUILL_W     = 12;
    private static final int QUILL_H     = 18;
    private static final int LANTERN_X   = 5;
    private static final int QUILL_X     = 20;
    private static final int COUNT_RIGHT = 6;
    private static final int MOTE_SIZE   = 2;
    private static final int MOTE_GAP    = 2;
    private static final int MOTE_Y      = 9;
    private static final int DASH_W      = 4;
    private static final int DASH_GAP    = 2;
    static final int         DASHES_MAX  = 11;

    private static final long LIT_MILLIS    = 3800;
    private static final long WRITE_MILLIS  = 950;
    private static final long BREATH_MILLIS = 2400;
    private static final float KINDLE       = 0.06F;
    private static final float DYING        = 0.55F;
    private static final int  READY_BREATHS = 3;
    private static final float READY_REST   = 0.35F;
    private static final float READY_SWELL  = 0.35F;

    private final IntSupplier    insights;
    private final DoubleSupplier charge;
    private final IntSupplier    affordable;

    private long    craftedAt = -1;
    private long    readyAt;
    private boolean wasReady;

    ResearchInsightButton(int x, int y, Component label, IntSupplier insights, DoubleSupplier charge, IntSupplier affordable, OnPress onPress)
    {
        super(x, y, WIDTH, HEIGHT, label, onPress, DEFAULT_NARRATION);
        this.insights = insights;
        this.charge = charge;
        this.affordable = affordable;
    }

    void crafted()
    {
        craftedAt = Util.getMillis();
    }

    int lanternX()
    {
        return getX() + LANTERN_X + LANTERN_W / 2;
    }

    int lanternY()
    {
        return getY() + 3 + 11;
    }

    /**
     * How strongly the lantern burns right now: catches quickly, holds, then dies down, always with a little flicker.
     */
    private float flame(long now)
    {
        if (craftedAt < 0)
            return 0;

        float t = (now - craftedAt) / (float) LIT_MILLIS;
        if (t >= 1)
            return 0;

        float level = t < KINDLE ? t / KINDLE : t < DYING ? 1 : 1 - (t - DYING) / (1 - DYING);
        float flicker = 0.88F + 0.12F * (float) Math.sin(now / 47D) * (float) Math.sin(now / 131D);
        return level * flicker;
    }

    /**
     * Swells a few times as the next insight comes into reach, then holds still: for much of the game there is always one in reach.
     */
    private float readiness(long now)
    {
        if (!wasReady)
            readyAt = now;
        wasReady = active;

        long since = now - readyAt;
        if (since >= READY_BREATHS * BREATH_MILLIS)
            return READY_REST;
        return READY_REST + READY_SWELL * (0.5F - 0.5F * (float) Math.cos(since * Math.PI * 2 / BREATH_MILLIS));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        long now = Util.getMillis();
        int x = getX();
        int y = getY();

        graphics.fill(x, y, x + width, y + height, PLATE_COLOR);
        float readiness = readiness(now);
        if (active)
            graphics.fill(x, y, x + width, y + height, ARGB.srgbLerp(readiness, PLATE_COLOR, READY_COLOR));
        graphics.outline(x, y, width, height, !active ? BORDER_COLOR : isHoveredOrFocused() ? HOVER_COLOR : LIVE_COLOR);

        extractLantern(graphics, x + LANTERN_X, y + 3, flame(now));
        extractQuill(graphics, x + QUILL_X, y + 3, now, active || flame(now) > 0 ? 0xFFFFFFFF : QUILL_DIM);

        var font = Minecraft.getInstance().font;
        var count = Integer.toString(insights.getAsInt());
        int countX = x + width - COUNT_RIGHT - font.width(count);
        graphics.text(font, count, countX, y + 7, active ? HOVER_COLOR : COUNT_COLOR);
        graphics.fill(countX - MOTE_GAP - MOTE_SIZE, y + MOTE_Y, countX - MOTE_GAP, y + MOTE_Y + MOTE_SIZE, LIVE_COLOR);

        int trackLeft = x + 2;
        int trackWidth = width - 4;
        if (active)
        {
            for (int dash = 0; dash < affordable.getAsInt(); dash++)
            {
                int dashX = trackLeft + dash * (DASH_W + DASH_GAP);
                graphics.fill(dashX, y + height - 3, dashX + DASH_W, y + height - 2, LIVE_COLOR);
            }
            return;
        }

        graphics.fill(trackLeft, y + height - 3, trackLeft + trackWidth, y + height - 2, TRACK_COLOR);
        graphics.fill(trackLeft, y + height - 3, trackLeft + (int) (trackWidth * Math.clamp(charge.getAsDouble(), 0, 1)), y + height - 2, CHARGE_COLOR);
    }

    private static void extractLantern(GuiGraphicsExtractor graphics, int x, int y, float flame)
    {
        if (flame > 0)
        {
            int haloX = x + LANTERN_W / 2 - HALO_SIZE / 2;
            int haloY = y + 10 - HALO_SIZE / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, haloX, haloY, HALO_U, ROW, HALO_SIZE, HALO_SIZE, ATLAS, ATLAS, ARGB.color((int) (flame * 255), FLAME_RGB));
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, 0, ROW, LANTERN_W, LANTERN_H, ATLAS, ATLAS);

        if (flame > 0)
        {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x + 3, y + 6, GLASS_U, ROW, GLASS_W, GLASS_H, ATLAS, ATLAS, ARGB.color((int) (flame * 150), FLAME_RGB));
            graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x + 4, y + 6, FLAME_U, ROW, FLAME_W, FLAME_H, ATLAS, ATLAS, ARGB.color((int) (flame * 255), 0xFFFFFF));
        }
    }

    /**
     * Scratches back and forth from the nib while writing, and rests leaning against the lantern otherwise.
     */
    private void extractQuill(GuiGraphicsExtractor graphics, int x, int y, long now, int tint)
    {
        float writing = craftedAt < 0 ? 0 : 1 - (now - craftedAt) / (float) WRITE_MILLIS;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y + QUILL_H);
        if (writing > 0)
        {
            pose.translate((float) Math.sin(now / 38D) * 1.6F * writing, (float) Math.abs(Math.sin(now / 76D)) * -1.2F * writing);
            pose.rotate((float) Math.sin(now / 38D) * 0.16F * writing);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, 0, -QUILL_H, QUILL_U, ROW, QUILL_W, QUILL_H, ATLAS, ATLAS, tint);
        pose.popMatrix();
    }
}
