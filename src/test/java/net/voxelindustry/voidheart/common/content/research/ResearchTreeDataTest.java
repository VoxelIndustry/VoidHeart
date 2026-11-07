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
import java.util.HashSet;
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

    private static final KnowledgeRules RULES = new KnowledgeRules(0.1, 30, 10, 1.1);
    private static final int[] COLUMNS_BUILT_BY_TIER = { 6, 45, 72 };

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
    void tree_everyConstellationHasResearch()
    {
        assertThat(TREE.values().stream().map(Research::constellation).distinct()).containsExactlyInAnyOrder(Constellation.values());
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

    /**
     * Clients are not told recipes, so what a research shows as unlocked is written beside them and has to keep up.
     */
    @Test
    void tree_unlocksNameWhatItsRecipesMake() throws IOException
    {
        for (var entry : TREE.entrySet())
        {
            var made = new HashSet<String>();
            for (var recipe : entry.getValue().recipes())
                made.add(madeBy(recipe));

            assertThat(entry.getValue().unlocks().stream().map(Identifier::toString)).as("unlocks of %s", entry.getKey()).containsExactlyInAnyOrderElementsOf(made);
        }
    }

    /**
     * An altar recipe with no output gives back a block entity's own item through its first input, as the Void Heart does.
     */
    private static String madeBy(Identifier recipe) throws IOException
    {
        var path = "recipe/" + recipe.getPath() + ".json";
        var file = Files.exists(DATA.resolve(path)) ? DATA.resolve(path) : GENERATED.resolve(path);
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

        if (json.has("result"))
            return json.getAsJsonObject("result").get("id").getAsString();

        var outputs = json.getAsJsonObject("outputs").getAsJsonArray("minecraft:itemstack");
        if (outputs != null)
            return outputs.get(0).getAsJsonObject().get("item").getAsString();
        return json.getAsJsonObject("inputs").getAsJsonArray("minecraft:itemstack").get(0).getAsJsonObject().get("item").getAsString();
    }

    /**
     * Whatever a research unlocks can be clicked on, so it needs something to say.
     */
    @Test
    void tree_everyUnlockHasAPage() throws IOException
    {
        var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/voidheart/lang/en_us.json"))).getAsJsonObject();

        for (var research : TREE.values())
        {
            for (var unlock : research.unlocks())
                assertThat(lang.has("voidheart.page." + unlock.getPath())).as("page of %s", unlock).isTrue();
        }
    }

    /**
     * A mention of a research that does not exist would read as a raw key instead of a link.
     */
    @Test
    void texts_onlyMentionResearchesThatExist() throws IOException
    {
        var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/voidheart/lang/en_us.json"))).getAsJsonObject();
        var mention = java.util.regex.Pattern.compile("\\[\\[([a-z0-9_]+)]]");

        for (var entry : lang.entrySet())
        {
            var matcher = mention.matcher(entry.getValue().getAsString());
            while (matcher.find())
                assertThat(TREE).as("%s mentions %s", entry.getKey(), matcher.group(1)).containsKey(Identifier.fromNamespaceAndPath("voidheart", matcher.group(1)));
        }
    }

    /**
     * A mention of an item links to its page, which only what a research unlocks has.
     */
    @Test
    void texts_onlyMentionItemsWithPages() throws IOException
    {
        var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/voidheart/lang/en_us.json"))).getAsJsonObject();
        var mention = java.util.regex.Pattern.compile("\\{\\{([a-z0-9_]+)}}");
        var unlocked = TREE.values().stream().flatMap(research -> research.unlocks().stream()).toList();

        for (var entry : lang.entrySet())
        {
            var matcher = mention.matcher(entry.getValue().getAsString());
            while (matcher.find())
                assertThat(unlocked).as("%s mentions %s", entry.getKey(), matcher.group(1)).contains(Identifier.fromNamespaceAndPath("voidheart", matcher.group(1)));
        }
    }

    /**
     * Mouse buttons are shown as icons from the controls font wherever the repository explains something.
     */
    @Test
    void texts_showMouseButtonsAsIcons() throws IOException
    {
        var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/voidheart/lang/en_us.json"))).getAsJsonObject();

        for (var entry : lang.entrySet())
        {
            if (entry.getKey().startsWith("voidheart.page.") || entry.getKey().startsWith("voidheart.scene."))
                assertThat(entry.getValue().getAsString().toLowerCase(java.util.Locale.ROOT)).as(entry.getKey()).doesNotContain("right-click", "left-click", "middle-click");
        }
    }

    @Test
    void tree_neverLocksWhatARepositoryIsMadeOf()
    {
        var locked = TREE.values().stream().flatMap(research -> research.recipes().stream()).map(Identifier::getPath).toList();

        assertThat(locked).doesNotContain("void_pillars", "voidstone_bricks");
    }

    /**
     * The price of insights outgrows the base cap at the 13th one, so every research must stay payable from the archives reachable at its tier, with a fair number of columns built by then.
     */
    @Test
    void tree_everyResearchIsPayableWithinTheCapOfItsTier()
    {
        TREE.forEach((id, research) ->
        {
            var path = chainOf(id);
            TREE.entrySet().stream()
                    .filter(entry -> entry.getValue().columnCapacity() > 0 && entry.getValue().tier() < Math.max(research.tier(), 2))
                    .map(Map.Entry::getKey)
                    .forEach(archive -> chainOf(archive).stream().filter(step -> !path.contains(step)).forEach(path::add));

            int insights = path.stream().mapToInt(step -> TREE.get(step).insightCost()).sum();
            double cap = RULES.baseCap() + path.stream().filter(step -> !step.equals(id))
                    .filter(step -> TREE.get(step).columnCapacity() > 0)
                    .mapToDouble(step -> TREE.get(step).columnCapacity() * COLUMNS_BUILT_BY_TIER[TREE.get(step).tier() - 1])
                    .max().orElse(0);

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
