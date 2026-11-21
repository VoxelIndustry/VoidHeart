package net.voxelindustry.voidheart.common.content.works;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Works out how to make an amount of an item from a stock and the recipes the stations can run. The target is always made in full, since an order asks
 * for more of it; for its ingredients what is in stock is used first and only the shortfall is crafted; a kind needed in several places is one node; among an ingredient's options the one most in stock is taken; what crafts give back or make
 * beyond the need goes back to the stock for the rest of the plan. A kind that would have to be made from itself, or that no recipe makes, is lacking.
 *
 * @param nodes   every kind the plan touches, in the order first reached, the target first
 * @param edges   what each made kind takes of each ingredient
 * @param lacking kinds the stock is short of and no recipe can make, by how many
 * @param essence aspect id to the essence all the crafts drink
 * @param tasks   the crafts to run, each after the crafts making its ingredients
 * @param <K>     a kind of item
 */
public record WorksPlan<K>(K target, long count, Map<K, Node<K>> nodes, List<Edge<K>> edges, Map<K, Long> lacking, Map<String, Long> essence,
                           List<Task<K>> tasks)
{
    /** Deeper than any real recipe chain, so only a runaway expansion stops here. */
    static final int MAX_DEPTH = 32;

    /**
     * @param needed    how many all the crafts and the order asked for
     * @param fromStock how many of those the stock gave
     * @param crafted   how many the crafts make, at least the rest of the need
     * @param recipe    the recipe making it, null for a kind taken from stock or lacking
     */
    public record Node<K>(K item, long needed, long fromStock, long crafted, long crafts, @Nullable WorksRecipe<K> recipe)
    {
        public boolean isMade()
        {
            return recipe != null;
        }
    }

    public record Edge<K>(K product, K ingredient, long count)
    {
    }

    public record Task<K>(WorksRecipe<K> recipe, long crafts)
    {
    }

    public boolean isComplete()
    {
        return lacking.isEmpty();
    }

    /**
     * @param recipesFor the recipes the owner's stations can run that make a kind, preferred first
     */
    public static <K> WorksPlan<K> plan(Function<K, List<WorksRecipe<K>>> recipesFor, Map<K, Long> stock, K target, long count)
    {
        var planner = new Planner<>(recipesFor, stock);
        planner.need(target, count, 0);
        return planner.result(target, count);
    }

    private static final class Planner<K>
    {
        private final Function<K, List<WorksRecipe<K>>> recipesFor;
        private final Map<K, Long> available;
        private final Map<K, long[]> counts = new LinkedHashMap<>();
        private final Map<K, WorksRecipe<K>> recipes = new HashMap<>();
        private final Map<List<K>, Long> edges = new LinkedHashMap<>();
        private final Map<K, Long> lacking = new LinkedHashMap<>();
        private final Map<String, Long> essence = new LinkedHashMap<>();
        private final List<Task<K>> tasks = new ArrayList<>();
        private final Set<K> path = new LinkedHashSet<>();

        // needed, fromStock, crafted, crafts
        private static final int NEEDED = 0, FROM_STOCK = 1, CRAFTED = 2, CRAFTS = 3;

        private Planner(Function<K, List<WorksRecipe<K>>> recipesFor, Map<K, Long> stock)
        {
            this.recipesFor = recipesFor;
            this.available = new HashMap<>(stock);
        }

        private void need(K item, long amount, int depth)
        {
            var node = counts.computeIfAbsent(item, kind -> new long[4]);
            node[NEEDED] += amount;

            var taken = depth == 0 ? 0 : Math.min(available.getOrDefault(item, 0L), amount);
            if (taken > 0)
            {
                available.merge(item, -taken, Long::sum);
                node[FROM_STOCK] += taken;
            }
            var shortfall = amount - taken;
            if (shortfall <= 0)
                return;

            var recipe = depth < MAX_DEPTH && !path.contains(item) ? choose(item) : null;
            if (recipe == null)
            {
                lacking.merge(item, shortfall, Long::sum);
                return;
            }

            var crafts = (shortfall + recipe.makes(item) - 1) / recipe.makes(item);
            recipes.putIfAbsent(item, recipe);
            node[CRAFTS] += crafts;
            node[CRAFTED] += crafts * recipe.makes(item);

            // What a craft gives back, such as a bucket, serves the next craft, so those crafts are planned one at a time
            var batch = recipe.remainders().isEmpty() ? crafts : 1;
            for (var done = 0L; done < crafts; done += batch)
                craft(item, recipe, batch, depth);

            for (var output : recipe.outputs())
            {
                var made = output.count() * crafts - (output.item().equals(item) ? shortfall : 0);
                if (made > 0)
                    available.merge(output.item(), made, Long::sum);
            }
        }

        private void craft(K item, WorksRecipe<K> recipe, long crafts, int depth)
        {
            path.add(item);
            for (var ingredient : recipe.inputs())
            {
                var wanted = ingredient.count() * crafts;
                var option = pick(ingredient, wanted);
                edges.merge(List.of(item, option), wanted, Long::sum);
                need(option, wanted, depth + 1);
            }
            path.remove(item);

            for (var remainder : recipe.remainders())
                available.merge(remainder.item(), remainder.count() * crafts, Long::sum);
            recipe.essence().forEach((aspect, amountPerCraft) -> essence.merge(aspect, (long) amountPerCraft * crafts, Long::sum));
            tasks.add(new Task<>(recipe, crafts));
        }

        /**
         * The first recipe whose ingredients don't loop back to a kind being made further up.
         */
        private @Nullable WorksRecipe<K> choose(K item)
        {
            for (var recipe : recipesFor.apply(item))
            {
                if (recipe.makes(item) <= 0)
                    continue;
                var loops = recipe.inputs().stream()
                        .anyMatch(ingredient -> ingredient.options().stream().allMatch(option -> option.equals(item) || path.contains(option)));
                if (!loops)
                    return recipe;
            }
            return null;
        }

        /**
         * The option the stock covers, else the one most in stock, else one a recipe makes, else the first.
         */
        private K pick(WorksRecipe.Ingredient<K> ingredient, long wanted)
        {
            K best = null;
            long bestHeld = -1;
            for (var option : ingredient.options())
            {
                var held = available.getOrDefault(option, 0L);
                if (held >= wanted)
                    return option;
                if (held > bestHeld)
                {
                    best = option;
                    bestHeld = held;
                }
            }
            if (bestHeld > 0)
                return best;
            for (var option : ingredient.options())
            {
                if (!path.contains(option) && !recipesFor.apply(option).isEmpty())
                    return option;
            }
            return ingredient.options().getFirst();
        }

        private WorksPlan<K> result(K target, long count)
        {
            var nodes = new LinkedHashMap<K, Node<K>>();
            counts.forEach((item, node) -> nodes.put(item, new Node<>(item, node[NEEDED], node[FROM_STOCK], node[CRAFTED], node[CRAFTS], recipes.get(item))));
            var edgeList = new ArrayList<Edge<K>>();
            edges.forEach((pair, amount) -> edgeList.add(new Edge<>(pair.get(0), pair.get(1), amount)));
            return new WorksPlan<>(target, count, Collections.unmodifiableMap(nodes), List.copyOf(edgeList), Collections.unmodifiableMap(lacking),
                    Collections.unmodifiableMap(essence), List.copyOf(tasks));
        }
    }
}
