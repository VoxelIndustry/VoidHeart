package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceTile;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The stations an Officiant can stand under: a Void Altar, the source pillar of a Shatter Forge, a crafting table, or a Void Furnace.
 */
final class Stations
{
    /** How long a laid out craft may wait to start before it is taken back. */
    static final int START_TIMEOUT_TICKS = 200;
    /** How long a crafting table rests after a craft, as long as the fold laying its grid out and taking its result back. */
    static final int TABLE_REST_TICKS = 44;

    /** Heights above their block that items float at: a pillar's, a forge source pillar's, the altar's catalyst, a table's result. */
    static final float PILLAR_REST       = 1.1F;
    static final float FORGE_SOURCE_REST = 1.2F;
    static final float ALTAR_REST        = 1 + 2 / 16F;
    static final float TABLE_REST        = 1.25F;
    static final float FURNACE_REST      = 1.2F;

    private Stations()
    {
    }

    /**
     * @return the Shatter Forge whose source pillar stands on top of the Officiant, or null
     */
    static @Nullable ShatterForgeTile forgeAbove(Level level, BlockPos officiant)
    {
        if (level.getBlockEntity(officiant.above()) instanceof VoidPillarTile pillar && pillar.isForgeSource()
                && level.getBlockEntity(pillar.getSourceOfForge()) instanceof ShatterForgeTile forge)
            return forge;
        return null;
    }

    /**
     * @return the station on top of the Officiant, or null when it stands under nothing it serves
     */
    static @Nullable OfficiantStation above(Level level, BlockPos officiant)
    {
        var top = officiant.above();
        if (level.getBlockEntity(top) instanceof VoidAltarTile altar)
            return new Altar(altar);
        if (level.getBlockEntity(top) instanceof VoidPillarTile pillar && pillar.isForgeSource()
                && level.getBlockEntity(pillar.getSourceOfForge()) instanceof ShatterForgeTile forge)
            return new Forge(forge, pillar);
        if (level.getBlockState(top).is(Blocks.CRAFTING_TABLE))
            return new Table(level, top);
        if (level.getBlockEntity(top) instanceof VoidFurnaceTile furnace && level instanceof ServerLevel serverLevel)
            return new Furnace(serverLevel, furnace);
        return null;
    }

    /**
     * @return what is left of the craft with that ticket at a station's block the Officiant no longer stands under, taken off: the finished result of a
     * forge whose source pillar was taken away, or a furnace's whole batch, its results first; empty when there is none
     */
    static Left leftAt(Level level, @Nullable BlockPos at, @Nullable UUID ticket)
    {
        if (at != null && ticket != null && level.getBlockEntity(at) instanceof VoidFurnaceTile furnace && ticket.equals(furnace.getTicket()))
        {
            var done = furnace.smelted();
            return new Left(furnace.takeBatch(), done);
        }
        var result = forgeLeft(level, at, ticket);
        return result.isEmpty() ? new Left(List.of(), 0) : new Left(List.of(result), 1);
    }

    /**
     * @param done how many of the claim's crafts were made
     */
    record Left(List<ItemStack> items, int done)
    {
    }

    private static ItemStack forgeLeft(Level level, @Nullable BlockPos at, @Nullable UUID ticket)
    {
        if (at != null && ticket != null && level.getBlockEntity(at) instanceof ShatterForgeTile forge && ticket.equals(forge.getTicket()) && !forge.isCrafting())
        {
            // Unpinned before the output is taken, which lets the forge try to start again
            forge.pin(null);
            return forge.takeOutput();
        }
        return ItemStack.EMPTY;
    }

    private record Altar(VoidAltarTile altar) implements OfficiantStation
    {
        @Override
        public StationKind kind()
        {
            return StationKind.ALTAR;
        }

        @Override
        public BlockPos pos()
        {
            return altar.getBlockPos();
        }

        @Override
        public boolean canRun(Object source)
        {
            return source instanceof AltarRecipe;
        }

        @Override
        public boolean isFree()
        {
            return altar.isVacant() && altar.pillarsAround().size() == 8;
        }

        @Override
        public boolean canSupply(Object source)
        {
            return source instanceof AltarRecipe recipe && altar.canSupply(recipe);
        }

        /**
         * Takes what lies on the pillars, unless a rite runs: a player may have started one on what was laid, and it still needs its pillars.
         */
        @Override
        public List<ItemStack> clear()
        {
            if (altar.isCrafting())
                return List.of();
            var strays = new ArrayList<ItemStack>();
            for (var pillar : altar.pillarsAround())
            {
                if (!pillar.getStack().isEmpty())
                {
                    strays.add(pillar.getStack());
                    pillar.setStack(ItemStack.EMPTY);
                }
            }
            return strays;
        }

        @Override
        public int steps(List<ItemStack> ingredients)
        {
            return ingredients.size();
        }

        /**
         * One pillar each step, the catalyst last, so a rite never starts as a smaller recipe sharing its catalyst.
         */
        @Override
        public Laid lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients, @Nullable UUID ticket)
        {
            if (step >= ingredients.size() - 1)
                return new Laid(altar.layCatalyst(owner, ingredients.getFirst(), net.minecraft.resources.Identifier.parse(recipeId), ticket), List.of());
            // Something a player set on the pillar since it was cleared goes to the hoard rather than under the ingredient
            var pillar = altar.pillarsAround().get(step);
            var found = pillar.getStack();
            pillar.setStack(ingredients.get(step + 1));
            return new Laid(true, found.isEmpty() ? List.of() : List.of(found));
        }

        @Override
        /**
         * A rite a player started on what was laid so far stops the laying, so no later step replaces what that rite needs.
         */
        public boolean ready(int step, int steps)
        {
            return !altar.isCrafting() && altar.pillarsAround().size() == 8 && (step < steps - 1 || altar.isVacant());
        }

        @Override
        public Fold foldOf(int step, int steps)
        {
            if (step < steps - 1)
                return new Fold(altar.pillarsAround().get(step).getBlockPos(), PILLAR_REST);
            return resultFold();
        }

        @Override
        public Fold resultFold()
        {
            return new Fold(altar.getBlockPos(), ALTAR_REST);
        }

        @Override
        public Outcome poll(Object source, int waited, @Nullable UUID ticket)
        {
            // An altar put in place of the one the craft was laid on, or an item a player set down since, is never the craft's result
            if (ticket != null && !ticket.equals(altar.getTicket()))
                return Outcome.failed(List.of());
            if (altar.isCrafting())
                return Outcome.RUNNING;
            if (altar.getPinned() == null)
            {
                // A rite always leaves its result; an empty altar is one put in place of the one the craft was laid on, so the craft counts again
                var result = altar.takeResult();
                return result.isEmpty() ? Outcome.failed(List.of()) : Outcome.done(List.of(result));
            }
            if (waited < START_TIMEOUT_TICKS)
                return Outcome.RUNNING;

            // It never started: the catalyst and what lies on the pillars go back
            var back = new ArrayList<ItemStack>();
            var catalyst = altar.takeResult();
            if (!catalyst.isEmpty())
                back.add(catalyst);
            for (var pillar : altar.pillarsAround())
            {
                if (!pillar.getStack().isEmpty())
                {
                    back.add(pillar.getStack());
                    pillar.setStack(ItemStack.EMPTY);
                }
            }
            return Outcome.failed(back);
        }
    }

    private record Forge(ShatterForgeTile forge, VoidPillarTile pillar) implements OfficiantStation
    {
        @Override
        public StationKind kind()
        {
            return StationKind.FORGE;
        }

        @Override
        public BlockPos pos()
        {
            return forge.getBlockPos();
        }

        @Override
        public boolean canRun(Object source)
        {
            return source instanceof ShatterForgeRecipe recipe && forge.runnableRecipes().stream().anyMatch(runnable -> runnable.getId().equals(recipe.getId()));
        }

        @Override
        public boolean isFree()
        {
            return !forge.isCrafting() && forge.getStack().isEmpty() && pillar.getStack().isEmpty();
        }

        @Override
        public boolean canSupply(Object source)
        {
            return source instanceof ShatterForgeRecipe recipe && forge.canSupply(recipe);
        }

        @Override
        public List<ItemStack> clear()
        {
            return List.of();
        }

        @Override
        public int steps(List<ItemStack> ingredients)
        {
            return 1;
        }

        @Override
        public Laid lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients, @Nullable UUID ticket)
        {
            var found = pillar.getStack();
            if (!found.isEmpty())
                pillar.setStack(ItemStack.EMPTY);
            forge.pin(net.minecraft.resources.Identifier.parse(recipeId));
            var placed = forge.placeInput(ingredients.getFirst());
            if (placed)
                forge.setTicket(ticket);
            else
                forge.pin(null);
            return new Laid(placed, found.isEmpty() ? List.of() : List.of(found));
        }

        @Override
        public boolean ready(int step, int steps)
        {
            return !forge.isCrafting() && forge.getStack().isEmpty();
        }

        @Override
        public Fold foldOf(int step, int steps)
        {
            return resultFold();
        }

        @Override
        public Fold resultFold()
        {
            return new Fold(pillar.getBlockPos(), FORGE_SOURCE_REST);
        }

        @Override
        public Outcome poll(Object source, int waited, @Nullable UUID ticket)
        {
            // A forge put in place of the one the craft was laid on, or whose input or output a hand took, is never the craft's result
            if (ticket != null && !ticket.equals(forge.getTicket()))
                return Outcome.failed(List.of());
            // Unpinned before the output is taken, which lets the forge try to start again, so a player's own input is never held to this recipe
            if (!forge.isCrafting() && !forge.getStack().isEmpty())
            {
                forge.pin(null);
                return Outcome.done(List.of(forge.takeOutput()));
            }
            if (forge.isCrafting() || waited < START_TIMEOUT_TICKS)
                return Outcome.RUNNING;
            var input = pillar.getStack();
            pillar.setStack(ItemStack.EMPTY);
            forge.setTicket(null);
            forge.pin(null);
            return Outcome.failed(input.isEmpty() ? List.of() : List.of(input));
        }
    }

    /**
     * A Void Furnace smelts a batch of one item laid in its input in one step, and its results come back once the input is empty. It must hold nothing
     * when the batch is laid, so a player's or a hopper's items are never mixed in.
     */
    private record Furnace(ServerLevel level, VoidFurnaceTile furnace) implements OfficiantStation
    {
        @Override
        public StationKind kind()
        {
            return StationKind.FURNACE;
        }

        @Override
        public BlockPos pos()
        {
            return furnace.getBlockPos();
        }

        @Override
        public boolean canRun(Object source)
        {
            return source instanceof RecipeHolder<?> holder && holder.value() instanceof SmeltingRecipe;
        }

        @Override
        public boolean isFree()
        {
            return furnace.isEmptied() && furnace.getTicket() == null;
        }

        /**
         * A furnace no field reaches would never start; its batch waits in the hoard instead.
         */
        @Override
        public boolean canSupply(Object source)
        {
            return VoidEnergy.available(level, furnace.getBlockPos()) > 0;
        }

        @Override
        public List<ItemStack> clear()
        {
            return List.of();
        }

        @Override
        public int steps(List<ItemStack> ingredients)
        {
            return 1;
        }

        /**
         * Lays the batch only when the furnace would run the very recipe ordered on it, so an input two recipes share never makes the other's result.
         */
        @Override
        public Laid lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients, @Nullable UUID ticket)
        {
            if (ticket == null || ingredients.isEmpty() || !(source instanceof RecipeHolder<?> holder)
                    || !holder.id().equals(furnace.recipeFor(level, ingredients.getFirst())))
                return new Laid(false, List.of());
            return new Laid(furnace.lay(ingredients.getFirst(), ticket), List.of());
        }

        @Override
        public boolean ready(int step, int steps)
        {
            return furnace.isEmptied();
        }

        @Override
        public Fold foldOf(int step, int steps)
        {
            return resultFold();
        }

        @Override
        public Fold resultFold()
        {
            return new Fold(furnace.getBlockPos(), FURNACE_REST);
        }

        @Override
        public Outcome poll(Object source, int waited, @Nullable UUID ticket)
        {
            // A furnace put in place of the one the batch was laid in, or whose slots a hand or a hopper touched, holds nothing of the batch
            if (ticket == null || !ticket.equals(furnace.getTicket()))
                return Outcome.failed(List.of());
            if (furnace.batchDone())
                return Outcome.done(furnace.takeBatch());
            // Nothing smelted long after it was laid: no field reaches it any more, so the batch goes back and counts again
            if (!furnace.hasProgressed() && waited >= START_TIMEOUT_TICKS)
                return Outcome.failed(furnace.takeBatch());
            return Outcome.RUNNING;
        }
    }

    /**
     * A crafting table crafts at once: the ingredients are set in its grid and the result and remainders go straight back. It then rests a moment, so
     * the fold showing the result can close.
     */
    private record Table(Level level, BlockPos pos) implements OfficiantStation
    {
        @Override
        public StationKind kind()
        {
            return StationKind.TABLE;
        }

        @Override
        public boolean canRun(Object source)
        {
            return source instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe;
        }

        @Override
        public boolean isFree()
        {
            return true;
        }

        @Override
        public boolean canSupply(Object source)
        {
            return true;
        }

        @Override
        public List<ItemStack> clear()
        {
            return List.of();
        }

        @Override
        public int steps(List<ItemStack> ingredients)
        {
            return 1;
        }

        @Override
        public Fold foldOf(int step, int steps)
        {
            return resultFold();
        }

        @Override
        public Fold resultFold()
        {
            return new Fold(pos, TABLE_REST);
        }

        @Override
        public boolean ready(int step, int steps)
        {
            return true;
        }

        /**
         * Fills the grid with a way of giving every slot an item it accepts, then crafts. With no such way, nothing is crafted and the ingredients stay
         * the Officiant's.
         */
        @Override
        public Laid lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients, @Nullable UUID ticket)
        {
            var recipe = (CraftingRecipe) ((RecipeHolder<?>) source).value();
            var slots = WorksCatalogue.slotsOf(recipe);
            if (slots == null)
                return new Laid(false, List.of());
            var pool = new ArrayList<ItemStack>();
            ingredients.forEach(stack -> pool.add(stack.copy()));
            var needs = slots.stream().filter(java.util.Optional::isPresent).map(slot -> (java.util.function.Predicate<ItemStack>) slot.get()::test).toList();
            var counts = pool.stream().mapToInt(ItemStack::getCount).toArray();
            var picks = SlotAssignment.assign(needs, pool, counts);
            if (picks == null)
                return new Laid(false, List.of());

            var chosen = new ArrayList<ItemStack>();
            for (var pick : picks)
                chosen.add(pool.get(pick).split(1));
            var next = chosen.iterator();
            var input = WorksCatalogue.inputOf(recipe, slots, ingredient -> next.next());
            if (!recipe.matches(input, level))
                return new Laid(false, List.of());

            var back = new ArrayList<ItemStack>();
            back.add(recipe.assemble(input));
            recipe.getRemainingItems(input).stream().filter(stack -> !stack.isEmpty()).forEach(back::add);
            pool.stream().filter(stack -> !stack.isEmpty()).forEach(back::add);
            return new Laid(true, back, gridOf(input));
        }

        /**
         * @return the craft's grid as the table's nine slots, row by row: a shape centred on it, or a shapeless craft's items in reading order
         */
        private static List<ItemStack> gridOf(net.minecraft.world.item.crafting.CraftingInput input)
        {
            var grid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            if (input.width() <= 3 && input.height() <= 3)
            {
                int left = (3 - input.width()) / 2;
                int top = (3 - input.height()) / 2;
                for (int row = 0; row < input.height(); row++)
                    for (int column = 0; column < input.width(); column++)
                        grid.set((top + row) * 3 + left + column, input.getItem(column, row).copyWithCount(1));
                return grid;
            }
            int slot = 0;
            for (var stack : input.items())
                if (!stack.isEmpty() && slot < 9)
                    grid.set(slot++, stack.copyWithCount(1));
            return grid;
        }

        @Override
        public Outcome poll(Object source, int waited, @Nullable UUID ticket)
        {
            return waited < TABLE_REST_TICKS ? Outcome.RUNNING : Outcome.done(List.of());
        }
    }

    /**
     * @return the ingredients a craft interrupted at a step has not laid out yet, which go back to the hoard
     */
    static List<ItemStack> unlaid(StationKind kind, int step, List<ItemStack> ingredients)
    {
        if (ingredients.isEmpty())
            return List.of();
        if (kind != StationKind.ALTAR)
            return step == 0 ? List.copyOf(ingredients) : List.of();
        // The altar lays its pillars first, the catalyst last
        var unlaid = new ArrayList<ItemStack>();
        if (step < ingredients.size())
            unlaid.add(ingredients.getFirst());
        for (int index = step + 1; index < ingredients.size(); index++)
            unlaid.add(ingredients.get(index));
        return unlaid;
    }
}
