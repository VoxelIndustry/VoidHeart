package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.beam.FormationError;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which recipe a Shatter Forge runs, from its beam alone. A build runs the most demanding recipe its beam and monolith pairs allow for the item on its
 * source pillar.
 */
public final class ForgeSelection
{
    private static final Comparator<ShatterForgeRecipe> MOST_DEMANDING_FIRST = Comparator.comparingInt(ShatterForgeRecipe::totalAspectRequirement)
            .thenComparingInt(ShatterForgeRecipe::getMonolithCount)
            .reversed()
            .thenComparing(recipe -> recipe.getId().toString());

    private ForgeSelection()
    {
    }

    /**
     * @param error null when a recipe was chosen, else why none could run
     */
    public record Choice(@Nullable ShatterForgeRecipe recipe, @Nullable FormationError error)
    {
        static Choice of(ShatterForgeRecipe recipe)
        {
            return new Choice(recipe, null);
        }

        static Choice none(@Nullable FormationError error)
        {
            return new Choice(null, error);
        }
    }

    public static boolean fits(ShatterForgeRecipe recipe, BeamComposition beam, int totalPairs)
    {
        return beam.satisfies(recipe.getAspects()) && totalPairs + 1 >= recipe.getMonolithCount();
    }

    /**
     * Given every recipe, this is what the build can be asked to make.
     *
     * @return the recipes the build can run, most demanding first
     */
    public static List<ShatterForgeRecipe> runnable(Collection<ShatterForgeRecipe> recipes, BeamComposition beam, int totalPairs)
    {
        return recipes.stream()
                .filter(recipe -> fits(recipe, beam, totalPairs))
                .sorted(MOST_DEMANDING_FIRST)
                .toList();
    }

    /**
     * Unpinned, a build short of essence or experience for its most demanding recipe falls back to the next one it can pay for. Pinned to a recipe, it only
     * ever runs that one, and waits for what it lacks.
     *
     * @param matching recipes taking the item on the source pillar
     */
    public static Choice choose(Collection<ShatterForgeRecipe> matching, BeamComposition beam, int totalPairs, @Nullable Identifier pinned,
                                Predicate<ShatterForgeRecipe> hasEssence, Predicate<ShatterForgeRecipe> hasExperience)
    {
        var candidates = pinned == null ? matching : matching.stream().filter(recipe -> recipe.getId().equals(pinned)).toList();
        if (candidates.isEmpty())
            return Choice.none(null);

        var passing = runnable(candidates, beam, totalPairs);
        if (passing.isEmpty())
        {
            var aspectsMet = candidates.stream().anyMatch(recipe -> beam.satisfies(recipe.getAspects()));
            return Choice.none(aspectsMet ? FormationError.TOO_FEW_MONOLITH : FormationError.MISSING_ASPECTS);
        }

        var lackingExperience = false;
        for (var recipe : passing)
        {
            if (!hasEssence.test(recipe))
                continue;
            if (!hasExperience.test(recipe))
            {
                lackingExperience = true;
                continue;
            }
            return Choice.of(recipe);
        }
        return Choice.none(lackingExperience ? FormationError.NOT_ENOUGH_EXPERIENCE : FormationError.NOT_ENOUGH_ESSENCE);
    }
}
