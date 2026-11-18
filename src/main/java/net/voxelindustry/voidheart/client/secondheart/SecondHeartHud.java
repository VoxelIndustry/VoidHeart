package net.voxelindustry.voidheart.client.secondheart;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;
import net.voxelindustry.voidheart.common.content.secondheart.HeartShown;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The second heart on the player's own screen. A small dark heart left of the health bar, drawn like the rune shields: a faint hollow outline
 * while it recharges, filled and softly beating when ready, with one bright flash as it comes back. While the heart dissolves and reforms its
 * player, their view darkens from the edges to near black, then clears from the centre, to a slow double heartbeat that quickens.
 * <p>
 * Per frame: one or two 9x9 sprites, and two full-screen quads while dissolving.
 */
public final class SecondHeartHud
{
    private static final Identifier FULL     = Identifier.fromNamespaceAndPath(MODID, "hud/second_heart_full");
    private static final Identifier EMPTY    = Identifier.fromNamespaceAndPath(MODID, "hud/second_heart_empty");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/second_heart_vignette.png");

    private static final int VIGNETTE_SIZE    = 128;
    private static final int ICON_SIZE        = 9;
    /** Left of the health bar's first heart, with a pixel of gap. */
    private static final int ICON_OFFSET      = 10;
    private static final int HEALTH_FROM_BOTTOM = 39;

    private static final int   HEART_RGB      = 0xB58CFF;
    private static final int   FLASH_RGB      = 0xFFFFFF;
    private static final float READY_ALPHA    = 0.9F;
    private static final float HOLLOW_ALPHA   = 0.35F;
    /** One lub-dub every two seconds, each beat brightening the heart toward this colour. */
    private static final long  BEAT_MILLIS    = 2000;
    private static final long  DUB_MILLIS     = 260;
    private static final float BEAT_DECAY     = 0.012F;
    private static final int   BEAT_RGB       = 0xE6D4FF;
    private static final long  FLASH_MILLIS   = 700;

    private static final int   DARK_RGB       = 0x07040C;
    private static final float DARK_MAX       = 0.94F;
    private static final float DARK_TINT_ALPHA = 0.25F;
    private static final int   DARK_TINT_RGB  = 0x3A1660;

    /** Ticks into the sequence at which the local heartbeat sounds, slow as the body comes apart and quicker as it returns. */
    private static final int[] PULSES         = {3, 8, 20, 25, 33, 37, 43, 47, 52, 55};

    private static HeartShown lastShown = HeartShown.NONE;
    private static long       readyAt   = -FLASH_MILLIS;
    private static int        lastElapsed = -1;

    private SecondHeartHud()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        var player = minecraft.player;
        if (player == null)
        {
            lastShown = HeartShown.NONE;
            return;
        }
        var shown = player.getAttachedOrElse(SecondHeart.SHOWN, HeartShown.NONE);
        if (lastShown == HeartShown.HOLLOW && shown == HeartShown.READY)
            readyAt = Util.getMillis();
        lastShown = shown;

        int elapsed = (int) SecondHeart.elapsed(player, 0);
        if (elapsed >= 0 && elapsed != lastElapsed)
            for (int pulse : PULSES)
                if (pulse == elapsed)
                    player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), VoidHeartSounds.SECOND_HEART_PULSE, SoundSource.PLAYERS,
                            1, 1, false);
        lastElapsed = elapsed;
    }

    public static void extractIcon(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isSpectator() || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer())
            return;
        var shown = player.getAttachedOrElse(SecondHeart.SHOWN, HeartShown.NONE);
        if (shown == HeartShown.NONE)
            return;

        int x = graphics.guiWidth() / 2 - 91 - ICON_OFFSET;
        int y = graphics.guiHeight() - HEALTH_FROM_BOTTOM;
        long now = Util.getMillis();

        if (shown == HeartShown.HOLLOW)
        {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, y, ICON_SIZE, ICON_SIZE, ARGB.color(HOLLOW_ALPHA, HEART_RGB));
            return;
        }

        float flash = Math.max(0, 1 - (now - readyAt) / (float) FLASH_MILLIS);
        int rgb = ARGB.srgbLerp(beat(now), HEART_RGB, BEAT_RGB);
        if (flash > 0)
            rgb = ARGB.srgbLerp(flash, rgb, FLASH_RGB);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, y, ICON_SIZE, ICON_SIZE, ARGB.color(READY_ALPHA, rgb));
    }

    /**
     * @return how bright the heart is from its beat, a quick rise on each of the lub and the dub then a fall
     */
    static float beat(long millis)
    {
        long t = millis % BEAT_MILLIS;
        float lub = (float) Math.exp(-t * BEAT_DECAY);
        float dub = t < DUB_MILLIS ? 0 : 0.7F * (float) Math.exp(-(t - DUB_MILLIS) * BEAT_DECAY);
        return Mth.clamp(lub + dub, 0, 1);
    }

    public static void extractView(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null)
            return;
        float elapsed = SecondHeart.elapsed(player, delta.getGameTimeDeltaPartialTick(false));
        if (elapsed < 0 || elapsed >= HeartRules.BURST_TICK)
            return;

        // Edges first as it darkens, so the centre is the last to go and the first to come back
        float dark = HeartRules.gone(elapsed);
        float edges = Mth.clamp(dark * 2, 0, 1);
        float centre = Mth.clamp(dark * 2 - 1, 0, 1);
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, ARGB.color(edges * DARK_MAX, DARK_RGB));
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, ARGB.color(edges * DARK_TINT_ALPHA, DARK_TINT_RGB));
        if (centre > 0)
            graphics.fill(0, 0, w, h, ARGB.color(centre * DARK_MAX, DARK_RGB));
    }
}
