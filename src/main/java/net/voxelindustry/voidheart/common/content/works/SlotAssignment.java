package net.voxelindustry.voidheart.common.content.works;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Gives each slot of a recipe an item it accepts from a pool, trying every way rather than the first fit, so an item one slot could do without is
 * left for a slot that needs it.
 */
final class SlotAssignment
{
    private SlotAssignment()
    {
    }

    /**
     * @param slots  what each slot accepts, in order
     * @param pool   the kinds of item at hand
     * @param counts how many of each kind there are
     * @return the kind each slot takes, by its place in the pool, or null when no way fills every slot
     */
    static <T> int @Nullable [] assign(List<Predicate<T>> slots, List<T> pool, int[] counts)
    {
        var left = counts.clone();
        var picks = new int[slots.size()];
        return place(0, slots, pool, left, picks) ? picks : null;
    }

    private static <T> boolean place(int slot, List<Predicate<T>> slots, List<T> pool, int[] left, int[] picks)
    {
        if (slot == slots.size())
            return true;
        for (int kind = 0; kind < pool.size(); kind++)
        {
            if (left[kind] <= 0 || !slots.get(slot).test(pool.get(kind)))
                continue;
            left[kind]--;
            picks[slot] = kind;
            if (place(slot + 1, slots, pool, left, picks))
                return true;
            left[kind]++;
        }
        return false;
    }
}
