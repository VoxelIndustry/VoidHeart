package net.voxelindustry.voidheart.common.content.research;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the shipped tree, whose numbers are all data and easily thrown out of balance.
 */
class ResearchTreeDataTest
{
    private static final Path DATA      = Path.of("src/main/resources/data/voidheart");
    private static final Path GENERATED = Path.of("src/main/generated/data/voidheart");

    private static final KnowledgeRules RULES = new KnowledgeRules(0.1, 30, 10, 1.2);

    private static final Map<Identifier, Research> TREE = new HashMap<>();

    @BeforeAll
    static void loadTree() throws IOException
    {
        try (var files = Files.list(DATA.resolve("voidheart/research")))
        {
            for (var file : files.toList())
            {
                var name = file.getFileName().toString().replace(".json", "");
                var json = JsonParser.parseString(Files.readString(file));
                TREE.put(Identifier.fromNamespaceAndPath("voidheart", name), Research.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
    }

    @Test
    void tree_hasThreeTiersPricedAsDesigned()
    {
        assertThat(TREE.values().stream().map(Research::tier).distinct()).containsExactlyInAnyOrder(1, 2, 3);

        TREE.values().forEach(research ->
        {
            switch (research.tier())
            {
                case 1 -> assertThat(research.insightCost()).isBetween(1, 2);
                case 2 -> assertThat(research.insightCost()).isBetween(6, 8);
                default -> assertThat(research.insightCost()).isGreaterThan(8);
            }
        });
    }

    @Test
    void tree_requirementsAllExistAndRootsAreInFirstTier()
    {
        TREE.forEach((id, research) ->
        {
            assertThat(TREE.keySet()).as("requirements of %s", id).containsAll(research.requires());
            if (research.requires().isEmpty())
                assertThat(research.tier()).as("tier of root %s", id).isEqualTo(1);
        });
    }

    @Test
    void tree_nodesNeverShareASpot()
    {
        var spots = TREE.values().stream().map(research -> research.tier() + ":" + research.row()).toList();

        assertThat(spots).doesNotHaveDuplicates();
    }

    @Test
    void tree_lockedRecipesAllExist()
    {
        TREE.forEach((id, research) -> research.recipes().forEach(recipe ->
        {
            var path = "recipe/" + recipe.getPath() + ".json";
            assertThat(Files.exists(DATA.resolve(path)) || Files.exists(GENERATED.resolve(path))).as("recipe %s of %s", recipe, id).isTrue();
        }));
    }

    @Test
    void tree_neverLocksWhatARepositoryIsMadeOf()
    {
        var locked = TREE.values().stream().flatMap(research -> research.recipes().stream()).map(Identifier::getPath).toList();

        assertThat(locked).doesNotContain("void_pillars", "voidstone_bricks");
    }

    /**
     * The price of insights outgrows the base cap at the 8th one, so every research must stay payable from the archives reachable at its tier.
     */
    @Test
    void tree_everyResearchIsPayableWithinTheCapOfItsTier()
    {
        TREE.forEach((id, research) ->
        {
            var path = chainOf(id);
            TREE.entrySet().stream()
                    .filter(entry -> entry.getValue().capBonus() > 0 && entry.getValue().tier() < Math.max(research.tier(), 2))
                    .map(Map.Entry::getKey)
                    .forEach(archive -> chainOf(archive).stream().filter(step -> !path.contains(step)).forEach(path::add));

            int insights = path.stream().mapToInt(step -> TREE.get(step).insightCost()).sum();
            double cap = RULES.baseCap() + path.stream().filter(step -> !step.equals(id)).mapToDouble(step -> TREE.get(step).capBonus()).sum();

            assertThat(RULES.insightCost(insights - 1)).as("last insight towards %s, after %s", id, path).isLessThanOrEqualTo(cap);
        });
    }

    private static List<Identifier> chainOf(Identifier id)
    {
        var chain = new ArrayList<Identifier>();
        var step = id;
        while (step != null)
        {
            chain.add(step);
            var requires = TREE.get(step).requires();
            step = requires.isEmpty() ? null : requires.getFirst();
        }
        return chain;
    }
}
