package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.util.function.DoubleSupplier;

/**
 * A plate cut in the stone. It charges up from the left as what it needs comes in, and breathes once it can be pressed.
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

    private final DoubleSupplier charge;

    ResearchButton(int x, int y, int width, int height, DoubleSupplier charge, OnPress onPress)
    {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.charge = charge;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();

        graphics.fill(x, y, x + width, y + height, PLATE_COLOR);
        if (active)
        {
            float breath = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() * Math.PI * 2 / BREATH_MILLIS);
            graphics.fill(x, y, x + width, y + height, ARGB.srgbLerp(breath * 0.6F, CHARGE_COLOR, READY_COLOR));
        }
        else
        {
            int filled = (int) (width * Math.clamp(charge.getAsDouble(), 0, 1));
            graphics.fill(x, y, x + filled, y + height, CHARGE_COLOR);
        }

        graphics.outline(x, y, width, height, !active ? BORDER_COLOR : isHoveredOrFocused() ? HOVER_COLOR : LIVE_COLOR);
        var font = Minecraft.getInstance().font;
        graphics.centeredText(font, getMessage(), x + width / 2, y + (height - font.lineHeight) / 2 + 1, active ? LIVE_TEXT : DEAD_TEXT);
    }
}
