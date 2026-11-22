package net.voxelindustry.voidheart.client.screen;

import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;

/**
 * Where a grid piece's item is drawn. Pure, so it is unit tested.
 */
public final class PieceIcon
{
    private PieceIcon()
    {
    }

    /**
     * The item's top left corner in pixels from the piece's origin cell: in the middle of the piece when the item lies wholly on its cells there, as on
     * any rectangle, otherwise centred on the cell nearest the piece's middle, so it never hangs over a gap in an L or a U.
     *
     * @param cell the size of a grid cell in pixels
     * @param icon the size of the item in pixels
     * @return x then y
     */
    public static int[] offset(ComponentShape shape, int cell, int icon)
    {
        int x = (shape.width() * cell - icon) / 2;
        int y = (shape.height() * cell - icon) / 2;
        if (onCells(shape, cell, x, y, icon))
            return new int[] { x, y };
        var middle = ApparatusScreen.middle(shape);
        return new int[] { middle.x() * cell + (cell - icon) / 2, middle.y() * cell + (cell - icon) / 2 };
    }

    /**
     * Whether a square no larger than a cell lies on filled cells: its corners reach every cell it touches.
     */
    private static boolean onCells(ComponentShape shape, int cell, int x, int y, int size)
    {
        for (int px : new int[] { x, x + size - 1 })
            for (int py : new int[] { y, y + size - 1 })
                if (px < 0 || py < 0 || !ApparatusScreen.has(shape, px / cell, py / cell))
                    return false;
        return true;
    }
}
