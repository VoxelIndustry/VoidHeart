package net.voxelindustry.voidheart.common.content.athanor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Pure rules of brewing: which crucibles answer a recipe, and how much of each aspect is still missing.
 */
public final class AthanorMath
{
    private AthanorMath()
    {
    }

    /**
     * Pairs every ingredient with its own filled slot, in any order, and leaves no filled slot over.
     *
     * @return the slot index used by each ingredient, in ingredient order, or null when the slots don't answer the ingredients exactly
     */
    public static <I, S> int[] matchUnordered(List<I> ingredients, List<S> slots, Predicate<S> filled, BiPredicate<I, S> matches)
    {
        int filledCount = 0;
        for (var slot : slots)
        {
            if (filled.test(slot))
                filledCount++;
        }
        if (filledCount != ingredients.size())
            return null;

        var assignment = new int[ingredients.size()];
        var used = new boolean[slots.size()];
        return assign(0, ingredients, slots, filled, matches, assignment, used) ? assignment : null;
    }

    private static <I, S> boolean assign(int index, List<I> ingredients, List<S> slots, Predicate<S> filled, BiPredicate<I, S> matches, int[] assignment, boolean[] used)
    {
        if (index == ingredients.size())
            return true;

        for (int slot = 0; slot < slots.size(); slot++)
        {
            if (used[slot] || !filled.test(slots.get(slot)) || !matches.test(ingredients.get(index), slots.get(slot)))
                continue;

            used[slot] = true;
            assignment[index] = slot;
            if (assign(index + 1, ingredients, slots, filled, matches, assignment, used))
                return true;
            used[slot] = false;
        }
        return false;
    }

    /**
     * @return each aspect still short of its requirement, with the amount missing
     */
    public static <K> Map<K, Integer> missing(Map<K, Integer> required, Map<K, Integer> gathered)
    {
        var missing = new LinkedHashMap<K, Integer>();
        required.forEach((aspect, amount) ->
        {
            int left = amount - gathered.getOrDefault(aspect, 0);
            if (left > 0)
                missing.put(aspect, left);
        });
        return missing;
    }

    /**
     * @return what is left of the gathered aspects once a brew used what it required, never below zero
     */
    public static <K> Map<K, Integer> consume(Map<K, Integer> gathered, Map<K, Integer> required)
    {
        var left = new LinkedHashMap<K, Integer>();
        gathered.forEach((aspect, amount) ->
        {
            int remaining = amount - required.getOrDefault(aspect, 0);
            if (remaining > 0)
                left.put(aspect, remaining);
        });
        return left;
    }
}
