package net.voxelindustry.voidheart.common.content.works;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a plan out in columns flowing to the right: raw kinds on the left, the target alone on the right, each made kind one column right of its
 * furthest ingredient. Within a column, kinds sit near the kinds they go into, so arcs cross little. Positions are in cells: x is the column, y the row
 * from the middle.
 *
 * @param columns how many columns the plan spans
 * @param rows    the most kinds any column holds
 */
public record WorksLayout<K>(Map<K, Spot> spots, int columns, int rows)
{
    private static final int SWEEPS = 4;

    public record Spot(int column, int row, float x, float y)
    {
    }

    public static <K> WorksLayout<K> of(WorksPlan<K> plan)
    {
        // How far from the target each kind is along its longest chain
        var depth = new HashMap<K, Integer>();
        depth.put(plan.target(), 0);
        for (int pass = 0; pass <= plan.nodes().size(); pass++)
        {
            var changed = false;
            for (var edge : plan.edges())
            {
                var from = depth.get(edge.product());
                if (from != null && depth.getOrDefault(edge.ingredient(), -1) < from + 1)
                {
                    depth.put(edge.ingredient(), from + 1);
                    changed = true;
                }
            }
            if (!changed)
                break;
        }
        var deepest = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        var columns = new ArrayList<List<K>>();
        for (int column = 0; column <= deepest; column++)
            columns.add(new ArrayList<>());
        for (var item : plan.nodes().keySet())
        {
            var itemDepth = depth.get(item);
            if (itemDepth != null)
                columns.get(deepest - itemDepth).add(item);
        }

        var productsOf = new HashMap<K, List<K>>();
        var ingredientsOf = new HashMap<K, List<K>>();
        for (var edge : plan.edges())
        {
            productsOf.computeIfAbsent(edge.ingredient(), item -> new ArrayList<>()).add(edge.product());
            ingredientsOf.computeIfAbsent(edge.product(), item -> new ArrayList<>()).add(edge.ingredient());
        }

        for (int sweep = 0; sweep < SWEEPS; sweep++)
        {
            // Toward the leaves, each kind follows what it goes into; back toward the target, each follows its ingredients
            if (sweep % 2 == 0)
                for (int column = deepest - 1; column >= 0; column--)
                    order(columns.get(column), rowsOf(columns.get(column + 1)), productsOf);
            else
                for (int column = 1; column <= deepest; column++)
                    order(columns.get(column), rowsOf(columns.get(column - 1)), ingredientsOf);
        }

        var rows = columns.stream().mapToInt(List::size).max().orElse(1);
        var spots = new LinkedHashMap<K, Spot>();
        for (int column = 0; column <= deepest; column++)
        {
            var kinds = columns.get(column);
            for (int row = 0; row < kinds.size(); row++)
                spots.put(kinds.get(row), new Spot(column, row, column, row - (kinds.size() - 1) / 2F));
        }
        return new WorksLayout<>(spots, deepest + 1, rows);
    }

    private static <K> Map<K, Integer> rowsOf(List<K> column)
    {
        var rows = new HashMap<K, Integer>();
        for (int row = 0; row < column.size(); row++)
            rows.put(column.get(row), row);
        return rows;
    }

    /**
     * Sorts a column by the mean row of its neighbours in the column beside it, keeping the order of kinds with none there.
     */
    private static <K> void order(List<K> column, Map<K, Integer> besideRows, Map<K, List<K>> neighbours)
    {
        var current = rowsOf(column);
        var centre = new HashMap<K, Double>();
        for (var item : column)
        {
            var rows = neighbours.getOrDefault(item, List.of()).stream().map(besideRows::get).filter(row -> row != null).toList();
            centre.put(item, rows.isEmpty() ? current.get(item) : rows.stream().mapToInt(Integer::intValue).average().orElse(0));
        }
        column.sort(Comparator.<K>comparingDouble(centre::get).thenComparingInt(current::get));
    }
}
