package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.beam.FormationError;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ForgeSelectionTest
{
    private static final EssenceType FIERY = new EssenceType(Identifier.fromNamespaceAndPath("voidheart", "fiery"), "fiery", null, null);
    private static final EssenceType STILLNESS = new EssenceType(Identifier.fromNamespaceAndPath("voidheart", "stillness"), "stillness", null, null);

    private static ShatterForgeRecipe arrogantIron;
    private static ShatterForgeRecipe seethingSteel;
    private static ShatterForgeRecipe listlessLead;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);

        arrogantIron = recipe("arrogant_iron", Items.NETHERITE_SCRAP, 3, Map.of());
        seethingSteel = recipe("seething_steel", Items.BLAZE_ROD, 3, Map.of(FIERY, 2));
        listlessLead = recipe("listless_lead", Items.PRISMARINE_SHARD, 3, Map.of(STILLNESS, 2));
    }

    private static ShatterForgeRecipe recipe(String name, net.minecraft.world.item.Item output, int monolithCount, Map<EssenceType, Integer> aspects)
    {
        return new ShatterForgeRecipe(Identifier.fromNamespaceAndPath("voidheart", name), output, Items.IRON_INGOT, 60, monolithCount, aspects, aspects.isEmpty() ? 0 : 1000);
    }

    private static List<ShatterForgeRecipe> iron()
    {
        return List.of(arrogantIron, seethingSteel, listlessLead);
    }

    @Test
    void runnable_givenAFieryBeam_thenTheFieryRecipeFirst()
    {
        var runnable = ForgeSelection.runnable(iron(), BeamComposition.of(FIERY, 3), 2);

        assertThat(runnable).containsExactly(seethingSteel, arrogantIron);
    }

    @Test
    void runnable_givenTooFewPairs_thenNothing()
    {
        assertThat(ForgeSelection.runnable(iron(), BeamComposition.of(FIERY, 3), 1)).isEmpty();
    }

    @Test
    void choose_givenEssenceForAll_thenTheMostDemanding()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, null, recipe -> true, recipe -> true);

        assertThat(choice.recipe()).isSameAs(seethingSteel);
        assertThat(choice.error()).isNull();
    }

    @Test
    void choose_givenUnpinnedAndShortOfFiery_thenFallsBackToTheNextRecipe()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, null, recipe -> recipe != seethingSteel, recipe -> true);

        assertThat(choice.recipe()).isSameAs(arrogantIron);
    }

    @Test
    void choose_givenPinnedAndShortOfFiery_thenWaitsForEssence()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, seethingSteel.getId(), recipe -> recipe != seethingSteel, recipe -> true);

        assertThat(choice.recipe()).isNull();
        assertThat(choice.error()).isEqualTo(FormationError.NOT_ENOUGH_ESSENCE);
    }

    @Test
    void choose_givenPinnedAndShortOfExperience_thenWaitsForExperience()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, seethingSteel.getId(), recipe -> true, recipe -> false);

        assertThat(choice.recipe()).isNull();
        assertThat(choice.error()).isEqualTo(FormationError.NOT_ENOUGH_EXPERIENCE);
    }

    @Test
    void choose_givenPinnedToAnAspectTheBeamLacks_thenMissingAspects()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, listlessLead.getId(), recipe -> true, recipe -> true);

        assertThat(choice.recipe()).isNull();
        assertThat(choice.error()).isEqualTo(FormationError.MISSING_ASPECTS);
    }

    @Test
    void choose_givenPinnedWithTooFewPairs_thenTooFewMonoliths()
    {
        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 1, seethingSteel.getId(), recipe -> true, recipe -> true);

        assertThat(choice.error()).isEqualTo(FormationError.TOO_FEW_MONOLITH);
    }

    @Test
    void choose_givenPinnedToARecipeForAnotherInput_thenNothingAndNoError()
    {
        var other = Identifier.fromNamespaceAndPath("voidheart", "ravenous_gold");

        var choice = ForgeSelection.choose(iron(), BeamComposition.of(FIERY, 3), 2, other, recipe -> true, recipe -> true);

        assertThat(choice.recipe()).isNull();
        assertThat(choice.error()).isNull();
    }
}
