package net.voxelindustry.voidheart.common.content.works;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorksPlanTest
{
    private final Map<String, List<WorksRecipe<String>>> recipes = new HashMap<>();

    private WorksRecipe<String> recipe(String output, long makes, StationKind station, Object... inputs)
    {
        var ingredients = new ArrayList<WorksRecipe.Ingredient<String>>();
        for (int i = 0; i < inputs.length; i += 2)
        {
            var options = inputs[i] instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of(String.valueOf(inputs[i]));
            ingredients.add(new WorksRecipe.Ingredient<>(options, ((Number) inputs[i + 1]).longValue()));
        }
        var recipe = new WorksRecipe<>(output, station, List.of(new WorksRecipe.Stack<>(output, makes)), ingredients, List.of(), Map.of());
        recipes.computeIfAbsent(output, item -> new ArrayList<>()).add(recipe);
        return recipe;
    }

    private WorksPlan<String> plan(Map<String, Long> stock, String target, long count)
    {
        return WorksPlan.plan(item -> recipes.getOrDefault(item, List.of()), stock, target, count);
    }

    private static void assertIngredientsComeFirst(WorksPlan<String> plan)
    {
        var made = new HashSet<String>();
        for (var task : plan.tasks())
        {
            for (var ingredient : task.recipe().inputs())
                for (var option : ingredient.options())
                    if (plan.nodes().containsKey(option) && plan.nodes().get(option).isMade() && plan.edges().stream()
                            .anyMatch(edge -> edge.product().equals(task.recipe().outputs().getFirst().item()) && edge.ingredient().equals(option)))
                        assertThat(made).as("%s made before %s", option, task.recipe().id()).contains(option);
            made.add(task.recipe().outputs().getFirst().item());
        }
    }

    @Test
    void plan_givenTheTargetInStock_thenCraftsNothing()
    {
        recipe("pearl", 1, StationKind.ALTAR, "shard", 8);

        var plan = plan(Map.of("pearl", 5L), "pearl", 3);

        assertThat(plan.tasks()).isEmpty();
        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.nodes().get("pearl").fromStock()).isEqualTo(3);
    }

    @Test
    void plan_givenPartOfTheTargetInStock_thenCraftsOnlyTheShortfall()
    {
        recipe("pearl", 1, StationKind.ALTAR, "shard", 8);

        var plan = plan(Map.of("pearl", 1L, "shard", 100L), "pearl", 3);

        assertThat(plan.tasks()).singleElement().extracting(WorksPlan.Task::crafts).isEqualTo(2L);
        assertThat(plan.nodes().get("shard").needed()).isEqualTo(16);
    }

    @Test
    void plan_givenAnIntermediateNeededTwice_thenOneNodeCountsBoth()
    {
        recipe("shard", 4, StationKind.TABLE, "ender_pearl", 1);
        recipe("pearl", 1, StationKind.ALTAR, "shard", 8);
        recipe("frame", 1, StationKind.ALTAR, "pearl", 2, "gold", 1);
        recipe("lens", 1, StationKind.ALTAR, "pearl", 1);
        recipe("armor", 1, StationKind.ALTAR, "frame", 1, "lens", 1);

        var plan = plan(Map.of("ender_pearl", 64L, "gold", 64L), "armor", 1);

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.nodes().get("pearl").crafted()).isEqualTo(3);
        assertThat(plan.nodes().get("shard").crafted()).isEqualTo(24);
        assertThat(plan.nodes().get("ender_pearl").needed()).isEqualTo(6);
        assertIngredientsComeFirst(plan);
    }

    @Test
    void plan_givenAnIngredientWithOptions_thenTakesTheOneInStock()
    {
        recipe("bench", 1, StationKind.TABLE, List.of("oak", "birch"), 4);

        var plan = plan(Map.of("birch", 10L), "bench", 1);

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.nodes()).containsKey("birch").doesNotContainKey("oak");
    }

    @Test
    void plan_givenARecipeMakingSeveral_thenRoundsUpAndKeepsTheSurplusForLater()
    {
        recipe("iron", 2, StationKind.FORGE, "raw_iron", 1);
        recipe("plate", 1, StationKind.ALTAR, "iron", 3);
        recipe("rivet", 1, StationKind.ALTAR, "iron", 1);
        recipe("hull", 1, StationKind.ALTAR, "plate", 1, "rivet", 1);

        var plan = plan(Map.of("raw_iron", 10L), "hull", 1);

        assertThat(plan.nodes().get("raw_iron").needed()).as("the plate's spare iron makes the rivet").isEqualTo(2);
        assertThat(plan.nodes().get("iron").crafted()).isEqualTo(4);
    }

    @Test
    void plan_givenWhatNoRecipeMakes_thenItIsLacking()
    {
        recipe("pearl", 1, StationKind.ALTAR, "shard", 8);

        var plan = plan(Map.of("shard", 5L), "pearl", 1);

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.lacking()).containsExactly(Map.entry("shard", 3L));
    }

    @Test
    void plan_givenRecipesMakingEachOther_thenStopsAndCallsItLacking()
    {
        recipe("block", 1, StationKind.TABLE, "ingot", 9);
        recipe("ingot", 9, StationKind.TABLE, "block", 1);

        var plan = plan(Map.of(), "block", 1);

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.lacking()).containsExactly(Map.entry("ingot", 9L));
        assertThat(plan.nodes().get("ingot").isMade()).isFalse();
    }

    @Test
    void plan_givenARemainder_thenItGoesBackForTheNextCraft()
    {
        var bucketBack = new WorksRecipe<>("cake", StationKind.TABLE, List.of(new WorksRecipe.Stack<>("cake", 1L)),
                List.of(new WorksRecipe.Ingredient<>(List.of("milk_bucket"), 1)), List.of(new WorksRecipe.Stack<>("bucket", 1L)), Map.of());
        recipes.put("cake", List.of(bucketBack));
        recipe("milk_bucket", 1, StationKind.TABLE, "bucket", 1, "milk", 1);
        recipe("feast", 1, StationKind.TABLE, "cake", 2);

        var plan = plan(Map.of("bucket", 1L, "milk", 5L), "feast", 1);

        assertThat(plan.isComplete()).as("lacking %s", plan.lacking()).isTrue();
        assertThat(plan.nodes().get("bucket").fromStock()).isEqualTo(2);
    }

    @Test
    void plan_givenEssence_thenSumsItPerCraft()
    {
        var drinking = new WorksRecipe<>("frame", StationKind.ALTAR, List.of(new WorksRecipe.Stack<>("frame", 1L)),
                List.of(new WorksRecipe.Ingredient<>(List.of("core"), 1)), List.of(), Map.of("voidheart:fiery", 6000));
        recipes.put("frame", List.of(drinking));

        var plan = plan(Map.of("core", 2L), "frame", 2);

        assertThat(plan.essence()).containsExactly(Map.entry("voidheart:fiery", 12000L));
    }

    @Test
    void layout_putsTheTargetAloneOnTheRightAndRawKindsLeft()
    {
        recipe("shard", 4, StationKind.TABLE, "ender_pearl", 1);
        recipe("pearl", 1, StationKind.ALTAR, "shard", 8);
        recipe("frame", 1, StationKind.ALTAR, "pearl", 2, "gold", 1);
        recipe("armor", 1, StationKind.ALTAR, "frame", 1, "pearl", 1, "gold", 2);

        var plan = plan(Map.of("ender_pearl", 64L, "gold", 64L), "armor", 1);
        var layout = WorksLayout.of(plan);

        var target = layout.spots().get("armor");
        assertThat(target.column()).isEqualTo(layout.columns() - 1);
        assertThat(layout.spots().values().stream().filter(spot -> spot.column() == target.column())).hasSize(1);
        assertThat(layout.spots().get("ender_pearl").column()).isZero();
        assertThat(layout.spots().get("pearl").column()).isLessThan(layout.spots().get("frame").column());
        assertThat(layout.spots().values().stream().map(spot -> spot.column() + ":" + spot.row()).distinct()).hasSize(layout.spots().size());
    }
}
