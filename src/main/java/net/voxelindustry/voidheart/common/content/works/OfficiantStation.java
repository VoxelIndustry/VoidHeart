package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A station as an Officiant works it: whether it is free and can pay for a recipe, starting a craft with the ingredients taken from the hoard, and
 * what comes back once it is over.
 */
interface OfficiantStation
{
    /**
     * @param finished whether the craft is over, done or failed
     * @param failed   whether it never ran, so its craft should count again
     * @param items    what goes back to the hoard: results, remainders, or the ingredients of a craft that never ran
     */
    record Outcome(boolean finished, boolean failed, List<ItemStack> items)
    {
        static final Outcome RUNNING = new Outcome(false, false, List.of());

        static Outcome done(List<ItemStack> items)
        {
            return new Outcome(true, false, items);
        }

        static Outcome failed(List<ItemStack> items)
        {
            return new Outcome(true, true, items);
        }
    }

    StationKind kind();

    /**
     * @param source the station's own recipe, from {@link WorksCatalogue#source}
     */
    boolean canRun(Object source);

    /**
     * @return whether nothing runs on the station, so a craft can be laid out
     */
    boolean isFree();

    /**
     * @return whether the station can pay the recipe's essence and experience now
     */
    boolean canSupply(Object source);

    /**
     * Lays the ingredients out, in the order of the recipe's inputs, and starts the craft.
     *
     * @return what was on the station before and must go back to the hoard
     */
    List<ItemStack> begin(UUID owner, String recipeId, Object source, List<ItemStack> ingredients);

    /**
     * @param waited ticks since the craft was laid out, after which one that never started is taken back
     */
    Outcome poll(Object source, int waited);
}
