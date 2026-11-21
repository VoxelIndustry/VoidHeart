package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Picks a recipe's ingredients out of a hoard: for each ingredient, the first option the hoard still holds enough of once the earlier ingredients are
 * counted out.
 */
final class Ingredients
{
    private Ingredients()
    {
    }

    /**
     * @return the kind and count taken for each ingredient, in the recipe's order, or null when the hoard falls short
     */
    static @Nullable List<WorksRecipe.Stack<ItemVariant>> plan(WorksRecipe<ItemVariant> recipe, PocketHoard.Hoard hoard)
    {
        var counted = new HashMap<ItemVariant, Long>();
        var picks = new ArrayList<WorksRecipe.Stack<ItemVariant>>();
        for (var ingredient : recipe.inputs())
        {
            WorksRecipe.Stack<ItemVariant> pick = null;
            for (var option : ingredient.options())
            {
                if (hoard.count(option) - counted.getOrDefault(option, 0L) >= ingredient.count())
                {
                    pick = new WorksRecipe.Stack<>(option, ingredient.count());
                    break;
                }
            }
            if (pick == null)
                return null;
            counted.merge(pick.item(), pick.count(), Long::sum);
            picks.add(pick);
        }
        return picks;
    }

    static long count(List<WorksRecipe.Stack<ItemVariant>> picks)
    {
        return picks.stream().mapToLong(WorksRecipe.Stack::count).sum();
    }

    static long items(List<ItemStack> stacks)
    {
        return stacks.stream().mapToLong(ItemStack::getCount).sum();
    }

    /**
     * Takes the ingredients out of the hoard, one stack per ingredient in the recipe's order. Call only after {@link #plan} found them all.
     */
    static List<ItemStack> take(WorksRecipe<ItemVariant> recipe, PocketHoard.Hoard hoard)
    {
        var picks = plan(recipe, hoard);
        var stacks = new ArrayList<ItemStack>();
        if (picks == null)
            return stacks;
        for (var pick : picks)
        {
            var taken = hoard.extract(pick.item(), pick.count());
            stacks.add(pick.item().toStack((int) taken));
        }
        return stacks;
    }
}
