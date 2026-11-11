package net.voxelindustry.voidheart.common.content.apparatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cells a component covers in an apparatus grid, from its top left corner. Turning it a quarter clockwise keeps it in the same corner, and it never mirrors.
 */
public record ComponentShape(List<Cell> cells)
{
    public record Cell(int x, int y)
    {
    }

    public static ComponentShape of(String... rows)
    {
        var cells = new ArrayList<Cell>();
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#')
                    cells.add(new Cell(x, y));
        return new ComponentShape(normalize(cells));
    }

    public int size()
    {
        return cells.size();
    }

    public int width()
    {
        return cells.stream().mapToInt(Cell::x).max().orElse(-1) + 1;
    }

    public int height()
    {
        return cells.stream().mapToInt(Cell::y).max().orElse(-1) + 1;
    }

    /**
     * @param quarters clockwise quarter turns, any integer
     */
    public ComponentShape rotated(int quarters)
    {
        var turned = cells;
        for (int i = 0; i < Math.floorMod(quarters, 4); i++)
            turned = turned.stream().map(cell -> new Cell(-cell.y(), cell.x())).toList();
        return new ComponentShape(normalize(turned));
    }

    private static List<Cell> normalize(List<Cell> cells)
    {
        int minX = cells.stream().mapToInt(Cell::x).min().orElse(0);
        int minY = cells.stream().mapToInt(Cell::y).min().orElse(0);
        return cells.stream()
                .map(cell -> new Cell(cell.x() - minX, cell.y() - minY))
                .sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x))
                .toList();
    }
}
