package net.voxelindustry.voidheart.client.apparatus;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps the shield out of the way in first person: a hairline over the hearts that shows only while the shield is not full or just changed,
 * and a few runes flaring at the edge of the crosshair on the side a hit came from.
 */
public final class ShieldHud
{
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shield_runes.png");

    private static final int   BAR_WIDTH      = 81;
    /** Above the hearts, in the pixel row between them and the armor. */
    private static final int   BAR_FROM_BOTTOM = 40;
    private static final int   BAR_BACK       = 0x50040208;
    private static final int   BAR_FILL       = 0xFF000000 | ShieldRuneRender.COLOR;
    private static final int   BAR_REFILLING  = 0xFF2F6B4C;
    private static final int   BAR_EMPTY      = 0xFFEF8A98;
    private static final long  LINGER_MILLIS  = 2500;
    private static final long  FADE_MILLIS    = 600;

    private static final int   RUNE_SIZE      = 7;
    private static final int   RUNE_RADIUS    = 24;
    private static final float RUNE_SPREAD    = 0.32F;
    private static final int   RUNE_COUNT     = 3;
    private static final float RUNE_ALPHA     = 0.7F;
    private static final int   RUNE_COLOR     = ShieldRuneRender.COLOR;
    private static final int   RUNE_BROKEN    = ShieldRuneRender.BROKEN_COLOR;
    private static final int   ATLAS          = 64;
    private static final int   CELL           = 16;

    private static float lastPoints = -1;
    private static long  changedAt;

    private ShieldHud()
    {
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isSpectator())
            return;

        extractBar(graphics, minecraft);
        if (minecraft.options.getCameraType().isFirstPerson())
            extractRunes(graphics, minecraft, delta.getGameTimeDeltaPartialTick(false));
    }

    private static void extractBar(GuiGraphicsExtractor graphics, Minecraft minecraft)
    {
        var state = ShieldingApparatus.state(minecraft.player);
        if (state.capacity() <= 0 || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer())
        {
            lastPoints = -1;
            return;
        }

        long now = Util.getMillis();
        if (Math.abs(state.points() - lastPoints) > 0.01F)
        {
            lastPoints = state.points();
            changedAt = now;
        }

        float shown = 1;
        if (state.isFull())
        {
            long since = now - changedAt - LINGER_MILLIS;
            shown = since <= 0 ? 1 : 1 - Math.min(1, since / (float) FADE_MILLIS);
        }
        if (shown <= 0)
            return;

        int left = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - BAR_FROM_BOTTOM;
        int filled = Math.round(BAR_WIDTH * state.points() / state.capacity());
        int fill = state.points() <= 0 ? BAR_EMPTY : state.delay() > 0 || state.isFull() ? BAR_FILL : BAR_REFILLING;

        graphics.fill(left, y, left + BAR_WIDTH, y + 1, ARGB.multiplyAlpha(BAR_BACK, shown));
        if (filled > 0)
            graphics.fill(left, y, left + filled, y + 1, ARGB.multiplyAlpha(fill, shown));
        else
            graphics.fill(left, y, left + 2, y + 1, ARGB.multiplyAlpha(BAR_EMPTY, shown));
    }

    private static void extractRunes(GuiGraphicsExtractor graphics, Minecraft minecraft, float partial)
    {
        var player = minecraft.player;
        var hits = ShieldHits.of(player.getId());
        if (hits.isEmpty())
            return;

        float now = player.level().getGameTime() + partial;
        float lookYaw = player.getViewYRot(partial) * Mth.DEG_TO_RAD;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        for (var hit : hits)
        {
            float age = now - hit.tick();
            if (age < 0 || age >= ShieldRuneRender.LIFE_TICKS)
                continue;
            float fade = 1 - age / ShieldRuneRender.LIFE_TICKS;

            // Angle of the hit around the view, 0 straight ahead and growing clockwise
            float hitYaw = (float) Mth.atan2(-hit.from().x(), hit.from().z());
            float angle = Mth.wrapDegrees((hitYaw - lookYaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;

            int color = ARGB.color(Math.round(255 * RUNE_ALPHA * fade * Mth.lerp(hit.strength(), 0.5F, 1)), hit.broke() ? RUNE_BROKEN : RUNE_COLOR);
            for (int rune = 0; rune < RUNE_COUNT; rune++)
            {
                float at = angle + (rune - (RUNE_COUNT - 1) / 2F) * RUNE_SPREAD;
                int x = centerX + Math.round(Mth.sin(at) * RUNE_RADIUS) - RUNE_SIZE / 2;
                int y = centerY - Math.round(Mth.cos(at) * RUNE_RADIUS) - RUNE_SIZE / 2;
                int glyph = (int) ((hit.tick() * 5 + rune * 7) & 15);
                graphics.blit(RenderPipelines.GUI_TEXTURED, RUNES, x, y, (glyph % 4) * CELL, (glyph / 4) * CELL, RUNE_SIZE, RUNE_SIZE, CELL, CELL, ATLAS, ATLAS, color);
            }
        }
    }
}
