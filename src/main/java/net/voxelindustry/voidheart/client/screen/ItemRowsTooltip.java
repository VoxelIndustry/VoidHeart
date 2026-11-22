package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Items listed one under the other in a tooltip, each with its icon, how many, and its name.
 */
public record ItemRowsTooltip(List<Row> rows) implements TooltipComponent
{
    public record Row(ItemStack item, long count, int color)
    {
    }

    public static final class Client implements ClientTooltipComponent
    {
        private static final int ROW_H  = 18;
        private static final int TEXT_X = 20;

        private final ItemRowsTooltip tooltip;

        public Client(ItemRowsTooltip tooltip)
        {
            this.tooltip = tooltip;
        }

        private static String line(Row row)
        {
            return HoardScreen.countText(row.count()) + " " + row.item().getHoverName().getString();
        }

        @Override
        public int getHeight(Font font)
        {
            return tooltip.rows().size() * ROW_H;
        }

        @Override
        public int getWidth(Font font)
        {
            return TEXT_X + tooltip.rows().stream().mapToInt(row -> font.width(line(row))).max().orElse(0);
        }

        @Override
        public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y)
        {
            for (int index = 0; index < tooltip.rows().size(); index++)
            {
                var row = tooltip.rows().get(index);
                graphics.text(font, line(row), x + TEXT_X, y + index * ROW_H + (ROW_H - font.lineHeight) / 2, row.color(), true);
            }
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics)
        {
            for (int index = 0; index < tooltip.rows().size(); index++)
                graphics.item(tooltip.rows().get(index).item(), x, y + index * ROW_H);
        }
    }
}
