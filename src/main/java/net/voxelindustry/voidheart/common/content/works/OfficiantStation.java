package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Where a fold opens for a step: the block it lies on, and the height above that block the item floats at.
     */
    record Fold(BlockPos pos, float rest)
    {
    }

    /**
     * @param placed whether the step was laid; when not, its ingredient is still the Officiant's
     * @param back   what comes back to the hoard: a table's result, or a player's item found where the step was laid
     */
    record Laid(boolean placed, List<ItemStack> back)
    {
    }

    StationKind kind();

    /**
     * @return the station's own block, where its result is found
     */
    BlockPos pos();

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
     * Clears the station before a craft is laid out.
     *
     * @return what was on it before and must go back to the hoard
     */
    List<ItemStack> clear();

    /**
     * @return how many steps laying these ingredients out takes, one item each where the station shows them
     */
    int steps(List<ItemStack> ingredients);

    /**
     * Lays one step out; the last starts the craft.
     *
     * @param ingredients all of the craft's ingredients, in the order of the recipe's inputs
     * @param ticket      marks the craft on the station, so its result is told from anything else set there
     */
    Laid lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients, @Nullable UUID ticket);

    /**
     * @return whether the station is still whole enough for a step to be laid, such as an altar still standing among eight pillars
     */
    boolean ready(int step, int steps);

    /**
     * @return where the fold of a step opens
     */
    Fold foldOf(int step, int steps);

    /**
     * @return where the fold taking the result back opens
     */
    Fold resultFold();

    /**
     * @param waited ticks since the craft was laid out, after which one that never started is taken back
     * @param ticket the craft's mark, null for one laid before crafts were marked
     */
    Outcome poll(Object source, int waited, @Nullable UUID ticket);
}
