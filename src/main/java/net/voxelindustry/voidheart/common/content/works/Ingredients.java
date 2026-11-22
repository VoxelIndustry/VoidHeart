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

    /**
     * For a recipe of one ingredient, such as a smelting recipe: the option the hoard holds most of, and how many crafts of it can start together in one
     * stack, up to most.
     *
     * @return null when the hoard holds too little of every option, or most is below one
     */
    static @Nullable WorksRecipe.Stack<ItemVariant> batch(WorksRecipe<ItemVariant> recipe, PocketHoard.Hoard hoard, long most)
    {
        if (most < 1 || recipe.inputs().size() != 1)
            return null;
        var ingredient = recipe.inputs().getFirst();
        var each = Math.max(1, ingredient.count());
        WorksRecipe.Stack<ItemVariant> best = null;
        for (var option : ingredient.options())
        {
            var crafts = hoard.count(option) / each;
            if (crafts > 0 && (best == null || crafts > best.count()))
                best = new WorksRecipe.Stack<>(option, crafts);
        }
        if (best == null)
            return null;
        var perStack = Math.max(1, best.item().toStack().getMaxStackSize() / each);
        return new WorksRecipe.Stack<>(best.item(), Math.min(Math.min(best.count(), most), perStack));
    }

    /**
     * Takes the crafts {@link #batch} finds out of the hoard, as one stack.
     */
    static List<ItemStack> takeBatch(WorksRecipe<ItemVariant> recipe, PocketHoard.Hoard hoard, int crafts)
    {
        var stacks = new ArrayList<ItemStack>();
        var pick = batch(recipe, hoard, crafts);
        if (pick == null)
            return stacks;
        var taken = hoard.extract(pick.item(), pick.count() * Math.max(1, recipe.inputs().getFirst().count()));
        stacks.add(pick.item().toStack((int) taken));
        return stacks;
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
