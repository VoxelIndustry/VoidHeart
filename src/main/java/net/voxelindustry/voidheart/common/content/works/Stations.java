package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The stations an Officiant can stand under: a Void Altar, the source pillar of a Shatter Forge, or a crafting table.
 */
final class Stations
{
    /** How long a laid out craft may wait to start before it is taken back. */
    static final int START_TIMEOUT_TICKS = 200;
    /** How long a crafting table rests after a craft, about as long as the fold showing it. */
    static final int TABLE_REST_TICKS = 20;

    /** Heights above their block that items float at: a pillar's, a forge source pillar's, the altar's catalyst, a table's result. */
    static final float PILLAR_REST       = 1.1F;
    static final float FORGE_SOURCE_REST = 1.2F;
    static final float ALTAR_REST        = 1 + 2 / 16F;
    static final float TABLE_REST        = 1.25F;

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
        return null;
    }

    private record Altar(VoidAltarTile altar) implements OfficiantStation
    {
        @Override
        public StationKind kind()
        {
            return StationKind.ALTAR;
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

        @Override
        public List<ItemStack> clear()
        {
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
        public List<ItemStack> lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients)
        {
            if (step < ingredients.size() - 1)
                altar.pillarsAround().get(step).setStack(ingredients.get(step + 1));
            else
                altar.layCatalyst(owner, ingredients.getFirst(), net.minecraft.resources.Identifier.parse(recipeId));
            return List.of();
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
        public Outcome poll(Object source, int waited)
        {
            if (altar.isCrafting())
                return Outcome.RUNNING;
            if (altar.getPinned() == null)
            {
                var result = altar.takeResult();
                return Outcome.done(result.isEmpty() ? List.of() : List.of(result));
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
        public List<ItemStack> lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients)
        {
            forge.pin(net.minecraft.resources.Identifier.parse(recipeId));
            forge.placeInput(ingredients.getFirst());
            return List.of();
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
        public Outcome poll(Object source, int waited)
        {
            if (!forge.isCrafting() && !forge.getStack().isEmpty())
                return Outcome.done(List.of(forge.takeOutput()));
            if (forge.isCrafting() || waited < START_TIMEOUT_TICKS)
                return Outcome.RUNNING;
            var input = pillar.getStack();
            pillar.setStack(ItemStack.EMPTY);
            return Outcome.failed(input.isEmpty() ? List.of() : List.of(input));
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
        public List<ItemStack> lay(int step, UUID owner, String recipeId, Object source, List<ItemStack> ingredients)
        {
            var recipe = (CraftingRecipe) ((RecipeHolder<?>) source).value();
            var slots = WorksCatalogue.slotsOf(recipe);
            var pool = new ArrayList<ItemStack>();
            ingredients.forEach(stack -> pool.add(stack.copy()));
            var input = WorksCatalogue.inputOf(recipe, slots, ingredient ->
            {
                for (var stack : pool)
                {
                    if (!stack.isEmpty() && ingredient.test(stack))
                        return stack.split(1);
                }
                return ItemStack.EMPTY;
            });
            if (!recipe.matches(input, level))
                return ingredients;

            var back = new ArrayList<ItemStack>();
            back.add(recipe.assemble(input));
            recipe.getRemainingItems(input).stream().filter(stack -> !stack.isEmpty()).forEach(back::add);
            pool.stream().filter(stack -> !stack.isEmpty()).forEach(back::add);
            return back;
        }

        @Override
        public Outcome poll(Object source, int waited)
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
