package net.voxelindustry.voidheart.common.content.altar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every altar rite a tier-3 research unlocks drinks essence, and no earlier rite does, read from the research and the generated recipes.
 */
class AltarDraughtCoverageTest
{
    private static final Path RESEARCH = Path.of("src/main/resources/data/voidheart/voidheart/research");
    private static final Path ALTAR_RECIPES = Path.of("src/main/generated/data/voidheart/recipe/altar");

    private static JsonObject read(Path path)
    {
        try
        {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        }
        catch (IOException e)
        {
            throw new AssertionError(path.toString(), e);
        }
    }

    private static Set<String> tierThreeAltarRecipes() throws IOException
    {
        var recipes = new TreeSet<String>();
        try (Stream<Path> files = Files.list(RESEARCH))
        {
            files.filter(file -> file.toString().endsWith(".json")).map(AltarDraughtCoverageTest::read)
                    .filter(research -> research.has("tier") && research.get("tier").getAsInt() == 3 && research.has("recipes"))
                    .forEach(research -> research.getAsJsonArray("recipes").forEach(recipe ->
                    {
                        var id = recipe.getAsString();
                        if (id.startsWith("voidheart:altar/"))
                            recipes.add(id.substring("voidheart:altar/".length()));
                    }));
        }
        return recipes;
    }

    private static Set<String> drinkingRecipes() throws IOException
    {
        var recipes = new TreeSet<String>();
        try (Stream<Path> files = Files.list(ALTAR_RECIPES))
        {
            files.filter(file -> file.toString().endsWith(".json"))
                    .filter(file -> read(file).has("aspects"))
                    .forEach(file -> recipes.add(file.getFileName().toString().replace(".json", "")));
        }
        return recipes;
    }

    @Test
    void draughts_matchTheTierThreeRites() throws IOException
    {
        var tierThree = tierThreeAltarRecipes();

        assertThat(tierThree).isNotEmpty();
        assertThat(drinkingRecipes()).isEqualTo(tierThree);
    }

    @Test
    void draughts_eachTakeOneAspect() throws IOException
    {
        var aspects = new HashSet<String>();
        for (var recipe : drinkingRecipes())
        {
            var drunk = read(ALTAR_RECIPES.resolve(recipe + ".json")).getAsJsonObject("aspects");
            assertThat(drunk.size()).as(recipe).isEqualTo(1);
            drunk.entrySet().forEach(entry -> assertThat(entry.getValue().getAsInt()).as(recipe).isPositive());
            aspects.addAll(drunk.keySet());
        }
        assertThat(aspects).containsExactlyInAnyOrder("voidheart:fiery", "voidheart:stillness", "voidheart:suffering", "voidheart:contravoid");
    }
}
