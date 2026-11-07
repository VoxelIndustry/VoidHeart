package net.voxelindustry.voidheart.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;

/**
 * A drop of the oil's color in the slot's top right corner, and a bar of the charges left along the bottom, above the durability bar when that
 * shows. The weapon's own glint is left alone.
 */
public final class OilDecoration
{
    private static final int BAR_WIDTH    = 13;
    private static final int BAR_LEFT     = 2;
    private static final int BAR_TOP      = 13;
    /**
     * Where the bar moves up to when the durability bar takes the bottom.
     */
    private static final int BAR_TOP_ABOVE_DURABILITY = 11;
    private static final int OUTLINE      = 0xFF1B1017;
    private static final int DROP_SHINE   = 0xFFFFE9A0;

    private OilDecoration()
    {
    }

    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y)
    {
        var coating = OilCoating.coatingOf(stack);
        if (coating == null)
            return;

        int color = ARGB.opaque(coating.type().color());

        // A drop five pixels tall: point, two widening rows, then the rounded bottom, with a dark outline so it reads on any icon
        int left = x + 11;
        int top = y;
        graphics.fill(RenderPipelines.GUI, left + 1, top, left + 3, top + 1, OUTLINE);
        graphics.fill(RenderPipelines.GUI, left, top + 1, left + 4, top + 5, OUTLINE);
        graphics.fill(RenderPipelines.GUI, left + 1, top + 5, left + 3, top + 6, OUTLINE);
        graphics.fill(RenderPipelines.GUI, left + 1, top + 1, left + 3, top + 5, color);
        graphics.fill(RenderPipelines.GUI, left + 1, top + 2, left + 2, top + 3, DROP_SHINE);

        // An arrow's coat lasts one hit, so it has no charges to show
        if (OilCoating.isArrow(stack))
            return;

        int barTop = y + (stack.isBarVisible() ? BAR_TOP_ABOVE_DURABILITY : BAR_TOP);
        int barLeft = x + BAR_LEFT;
        int filled = Math.max(1, Math.round(BAR_WIDTH * Math.min(1, coating.charges() / (float) coating.capacity())));
        graphics.fill(RenderPipelines.GUI, barLeft, barTop, barLeft + BAR_WIDTH, barTop + 2, 0xFF000000);
        graphics.fill(RenderPipelines.GUI, barLeft, barTop, barLeft + filled, barTop + 1, color);
    }
}
