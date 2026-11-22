package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * A plate cut in the stone, which breathes while it can be pressed. A warning plate breathes rose, for an action that goes ahead though something is
 * missing; a selected plate, such as the tab shown, stays lit and underlined.
 */
class ResearchButton extends Button
{
    private static final int  PLATE_COLOR     = 0xFF0B0810;
    private static final int  CHARGE_COLOR    = 0xFF1F3F3C;
    private static final int  READY_COLOR     = 0xFF16463F;
    private static final int  BORDER_COLOR    = 0xFF4A3860;
    private static final int  LIVE_COLOR      = 0xFF36C5B0;
    private static final int  HOVER_COLOR     = 0xFF7CE8D6;
    private static final int  LIVE_TEXT       = 0xFFE8F8F5;
    private static final int  DEAD_TEXT       = 0xFF9A8AAE;
    private static final long BREATH_MILLIS   = 2400;
    private static final int  WARN_CHARGE     = 0xFF3A1A22;
    private static final int  WARN_READY      = 0xFF4E2230;
    private static final int  WARN_COLOR      = ResearchScreen.LACK_COLOR;
    private static final int  WARN_TEXT       = 0xFFFBE4E8;

    private final boolean quiet;
    /** Whether pressing it goes ahead though something is missing, drawn rose. */
    boolean warning;
    /** Whether it stands for what is shown, drawn lit and underlined. */
    boolean selected;

    ResearchButton(int x, int y, int width, int height, OnPress onPress)
    {
        this(x, y, width, height, false, onPress);
    }

    /**
     * @param quiet for what is there if wanted but should not be invited: it neither breathes nor lights up until the cursor is on it
     */
    ResearchButton(int x, int y, int width, int height, boolean quiet, OnPress onPress)
    {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.quiet = quiet;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();
        boolean lit = selected || active && (!quiet || isHoveredOrFocused());

        graphics.fill(x, y, x + width, y + height, PLATE_COLOR);
        if (active && !quiet)
        {
            float breath = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() * Math.PI * 2 / BREATH_MILLIS);
            graphics.fill(x, y, x + width, y + height, warning ? ARGB.srgbLerp(breath * 0.6F, WARN_CHARGE, WARN_READY)
                    : ARGB.srgbLerp(breath * 0.6F, CHARGE_COLOR, READY_COLOR));
        }

        int border = !lit ? BORDER_COLOR : warning ? WARN_COLOR : isHoveredOrFocused() && !selected ? HOVER_COLOR : LIVE_COLOR;
        graphics.outline(x, y, width, height, border);
        var font = Minecraft.getInstance().font;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        graphics.centeredText(font, getMessage(), x + width / 2, textY, !lit ? DEAD_TEXT : warning ? WARN_TEXT : LIVE_TEXT);
        if (selected)
        {
            int textW = font.width(getMessage());
            graphics.fill(x + (width - textW) / 2, y + height - 2, x + (width + textW) / 2, y + height - 1, LIVE_COLOR);
        }
    }
}
