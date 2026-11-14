package net.voxelindustry.voidheart.client.apparatus;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps the shield out of the way in first person. Its points are rune shields laid see-through over the armor row, two points each as armor counts them,
 * with empty sockets for what is still to refill; past ten shields a second, paler layer goes over the first. A few runes also flare at the edge of the
 * crosshair on the side a hit came from.
 * <p>
 * Per frame: up to twenty 9x9 sprites for the row and three per live hit around the crosshair.
 */
public final class ShieldHud
{
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shield_runes.png");

    private static final Identifier SHIELD_FULL  = Identifier.fromNamespaceAndPath(MODID, "hud/shield_rune_full");
    private static final Identifier SHIELD_HALF  = Identifier.fromNamespaceAndPath(MODID, "hud/shield_rune_half");
    private static final Identifier SHIELD_EMPTY = Identifier.fromNamespaceAndPath(MODID, "hud/shield_rune_empty");

    private static final int   ICONS          = 10;
    private static final int   ICON_SIZE      = 9;
    private static final int   ICON_STEP      = 8;
    private static final float POINTS_PER_ICON = 2;
    /** The armor row's place, as vanilla works it out above the health rows. */
    private static final int   BASE_FROM_BOTTOM = 39;
    private static final int   ROW_ABOVE      = 10;

    private static final float FULL_ALPHA     = 0.78F;
    private static final float EMPTY_ALPHA    = 0.4F;
    private static final int   LAYER_RGB      = 0xC9F7DD;
    private static final int   BROKEN_RGB     = ShieldRuneRender.BROKEN_COLOR;
    private static final int   FLASH_RGB      = 0xFFFFFF;
    private static final long  FLASH_MILLIS   = 260;
    /** The shield refilling next pulses while it fills. */
    private static final float PULSE_SPEED    = 0.012F;
    private static final float PULSE_LOW      = 0.3F;
    private static final float REFILL_ALPHA   = 0.7F;

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
    private static long  hitAt;

    private ShieldHud()
    {
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isSpectator())
            return;

        extractShields(graphics, minecraft);
        if (minecraft.options.getCameraType().isFirstPerson())
            extractRunes(graphics, minecraft, delta.getGameTimeDeltaPartialTick(false));
    }

    /**
     * The top of the armor row: one row above the health rows, which close up as more of them are needed.
     */
    private static int armorRowY(GuiGraphicsExtractor graphics, Player player)
    {
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Mth.ceil(player.getHealth()));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        return graphics.guiHeight() - BASE_FROM_BOTTOM - (healthRows - 1) * rowHeight - ROW_ABOVE;
    }

    private static void extractShields(GuiGraphicsExtractor graphics, Minecraft minecraft)
    {
        var state = ShieldingApparatus.state(minecraft.player);
        if (state.capacity() <= 0 || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer())
        {
            lastPoints = -1;
            return;
        }

        long now = Util.getMillis();
        if (lastPoints >= 0 && state.points() < lastPoints - 0.01F)
            hitAt = now;
        lastPoints = state.points();
        boolean flashing = now - hitAt < FLASH_MILLIS;

        int left = graphics.guiWidth() / 2 - 91;
        int y = armorRowY(graphics, minecraft.player);
        int sockets = Math.min(ICONS, Mth.ceil(state.capacity() / POINTS_PER_ICON));
        int socketRgb = state.points() <= 0 ? BROKEN_RGB : ShieldRuneRender.COLOR;
        for (int icon = 0; icon < sockets; icon++)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHIELD_EMPTY, left + icon * ICON_STEP, y, ICON_SIZE, ICON_SIZE, ARGB.color(EMPTY_ALPHA, socketRgb));

        // Points in halves, as armor counts them, so a shield half filled shows a half rune
        int halves = Mth.floor(state.points());
        int perLayer = ICONS * (int) POINTS_PER_ICON;
        for (int layer = 0; layer * perLayer < halves; layer++)
        {
            int rgb = flashing ? FLASH_RGB : layerRgb(layer);
            for (int icon = 0; icon < ICONS; icon++)
            {
                int filled = halves - layer * perLayer - icon * 2;
                if (filled <= 0)
                    break;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, filled >= 2 ? SHIELD_FULL : SHIELD_HALF, left + icon * ICON_STEP, y, ICON_SIZE, ICON_SIZE,
                        ARGB.color(FULL_ALPHA, rgb));
            }
        }

        // The half point refilling next glows in and out over its socket until it is whole
        if (!state.isFull() && state.delay() <= 0 && halves < Mth.floor(state.capacity()))
        {
            int icon = halves % perLayer / 2;
            float pulse = Mth.lerp(0.5F + 0.5F * Mth.sin(now * PULSE_SPEED), PULSE_LOW, 1);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, halves % 2 == 0 ? SHIELD_HALF : SHIELD_FULL, left + icon * ICON_STEP, y, ICON_SIZE, ICON_SIZE,
                    ARGB.color(FULL_ALPHA * REFILL_ALPHA * pulse, layerRgb(halves / perLayer)));
        }
    }

    private static int layerRgb(int layer)
    {
        return layer == 0 ? ShieldRuneRender.COLOR : LAYER_RGB;
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
