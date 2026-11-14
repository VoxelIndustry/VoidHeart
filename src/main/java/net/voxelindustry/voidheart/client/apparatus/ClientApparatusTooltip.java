package net.voxelindustry.voidheart.client.apparatus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusTooltip;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;

/**
 * A small map of an apparatus grid under its name: each piece a block of its colour, ruled along its outer edges, where it sits.
 */
public class ClientApparatusTooltip implements ClientTooltipComponent
{
    private static final int   CELL        = 9;
    private static final int   MARGIN      = 3;
    private static final int   FRAME_COLOR = 0xFF0B0810;
    private static final int   RULE_COLOR  = 0xFF4A3860;
    private static final int   EMPTY_COLOR = 0xFF16101E;
    private static final float PIECE_TINT  = 0.45F;

    private final ApparatusTooltip tooltip;

    public ClientApparatusTooltip(ApparatusTooltip tooltip)
    {
        this.tooltip = tooltip;
    }

    private int side()
    {
        return tooltip.size() * CELL + 1;
    }

    @Override
    public int getHeight(Font font)
    {
        return side() + 2 + MARGIN;
    }

    @Override
    public int getWidth(Font font)
    {
        return side() + 2;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics)
    {
        int side = side();
        graphics.fill(x, y, x + side + 2, y + side + 2, FRAME_COLOR);
        graphics.outline(x, y, side + 2, side + 2, RULE_COLOR);
        int left = x + 1;
        int top = y + 1;
        for (int cellY = 0; cellY < tooltip.size(); cellY++)
            for (int cellX = 0; cellX < tooltip.size(); cellX++)
                graphics.fill(left + cellX * CELL + 1, top + cellY * CELL + 1, left + cellX * CELL + CELL, top + cellY * CELL + CELL, EMPTY_COLOR);

        for (var placement : tooltip.grid().valid(tooltip.size()))
        {
            var shape = placement.shape();
            int rgb = 0xFF000000 | placement.component().rgb();
            int fill = ARGB.srgbLerp(PIECE_TINT, FRAME_COLOR, rgb);
            for (var cell : shape.cells())
            {
                int cellLeft = left + (placement.x() + cell.x()) * CELL;
                int cellTop = top + (placement.y() + cell.y()) * CELL;
                // Each cell reaches over the rule to a neighbour of the same piece, so a piece reads as one block
                int right = cellLeft + CELL + (has(shape, cell.x() + 1, cell.y()) ? 1 : 0);
                int bottom = cellTop + CELL + (has(shape, cell.x(), cell.y() + 1) ? 1 : 0);
                graphics.fill(cellLeft + 1, cellTop + 1, right, bottom, fill);
                if (!has(shape, cell.x(), cell.y() - 1))
                    graphics.fill(cellLeft + 1, cellTop + 1, cellLeft + CELL, cellTop + 2, rgb);
                if (!has(shape, cell.x(), cell.y() + 1))
                    graphics.fill(cellLeft + 1, cellTop + CELL - 1, cellLeft + CELL, cellTop + CELL, rgb);
                if (!has(shape, cell.x() - 1, cell.y()))
                    graphics.fill(cellLeft + 1, cellTop + 1, cellLeft + 2, cellTop + CELL, rgb);
                if (!has(shape, cell.x() + 1, cell.y()))
                    graphics.fill(cellLeft + CELL - 1, cellTop + 1, cellLeft + CELL, cellTop + CELL, rgb);
            }
        }
    }

    private static boolean has(ComponentShape shape, int x, int y)
    {
        return shape.cells().stream().anyMatch(cell -> cell.x() == x && cell.y() == y);
    }
}
