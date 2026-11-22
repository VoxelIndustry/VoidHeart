package net.voxelindustry.voidheart.common.content.works;

import java.util.List;
import java.util.Map;

/**
 * A recipe as the planner sees it, whatever station runs it: what goes in, what comes out, what is given back, and the essence it drinks.
 *
 * @param id      the recipe's id, shown and used to pin it on its station
 * @param essence aspect id to amount, drunk once per craft
 * @param <K>     a kind of item
 */
public record WorksRecipe<K>(String id, StationKind station, List<Stack<K>> outputs, List<Ingredient<K>> inputs, List<Stack<K>> remainders,
                             Map<String, Integer> essence)
{
    public record Stack<K>(K item, long count)
    {
    }

    /**
     * Any one of the options, count times.
     */
    public record Ingredient<K>(List<K> options, long count)
    {
    }

    public WorksRecipe
    {
        outputs = List.copyOf(outputs);
        inputs = List.copyOf(inputs);
        remainders = List.copyOf(remainders);
        essence = Map.copyOf(essence);
    }

    /**
     * @return how many of the item one craft makes
     */
    public long makes(K item)
    {
        return outputs.stream().filter(stack -> stack.item().equals(item)).mapToLong(Stack::count).sum();
    }
}
