package net.voxelindustry.voidheart.common.content.works;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The mod's own recipes read straight from their JSON, keyed by item id, so plans can be tested and drawn on the real tree without starting a game.
 * Vanilla recipes aren't in the repository, so what only vanilla makes counts as raw. Rites that change one specific item, such as a sin, are left out.
 */
final class JsonWorks
{
    private static final Path GENERATED = Path.of("src/main/generated/data/voidheart/recipe");
    private static final Path HAND_WRITTEN = Path.of("src/main/resources/data/voidheart/recipe");

    private final Map<String, List<WorksRecipe<String>>> byOutput = new HashMap<>();

    JsonWorks() throws IOException
    {
        read(GENERATED.resolve("altar"), StationKind.ALTAR);
        read(GENERATED.resolve("shatterforge"), StationKind.FORGE);
        read(GENERATED, StationKind.TABLE);
        read(HAND_WRITTEN, StationKind.TABLE);
    }

    List<WorksRecipe<String>> recipesFor(String item)
    {
        return byOutput.getOrDefault(item, List.of());
    }

    private void read(Path folder, StationKind station) throws IOException
    {
        try (Stream<Path> files = Files.list(folder))
        {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList())
            {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var id = folder.getFileName() + "/" + file.getFileName().toString().replace(".json", "");
                var recipe = switch (json.get("type").getAsString())
                {
                    case "voidheart:altar", "voidheart:shatter_forge" -> steamLayer(id, station, json);
                    case "minecraft:crafting_shaped" -> shaped(id, json);
                    case "minecraft:crafting_shapeless" -> shapeless(id, json);
                    default -> null;
                };
                if (recipe != null)
                    byOutput.computeIfAbsent(recipe.outputs().getFirst().item(), item -> new ArrayList<>()).add(recipe);
            }
        }
    }

    private static WorksRecipe<String> steamLayer(String id, StationKind station, JsonObject json)
    {
        var inputs = json.getAsJsonObject("inputs").getAsJsonArray("minecraft:itemstack");
        var outputs = json.has("outputs") ? json.getAsJsonObject("outputs").getAsJsonArray("minecraft:itemstack") : null;
        if (outputs == null || outputs.isEmpty())
            return null;

        var output = outputs.get(0).getAsJsonObject();
        String made;
        if (output.has("item"))
            made = output.get("item").getAsString();
        else
            return null;
        var count = output.has("quantity") ? output.get("quantity").getAsLong() : 1;

        var ingredients = new ArrayList<WorksRecipe.Ingredient<String>>();
        for (var input : inputs)
        {
            var object = input.getAsJsonObject();
            var option = object.has("item") ? object.get("item").getAsString() : "#" + object.get("tag").getAsString();
            ingredients.add(new WorksRecipe.Ingredient<>(List.of(option), 1));
        }
        var essence = new LinkedHashMap<String, Integer>();
        if (station == StationKind.ALTAR && json.has("aspects"))
            json.getAsJsonObject("aspects").entrySet().forEach(entry -> essence.put(entry.getKey(), entry.getValue().getAsInt()));
        return new WorksRecipe<>(id, station, List.of(new WorksRecipe.Stack<>(made, count)), ingredients, List.of(), essence);
    }

    private static WorksRecipe<String> shaped(String id, JsonObject json)
    {
        var counts = new LinkedHashMap<String, Integer>();
        for (var row : json.getAsJsonArray("pattern"))
            for (var symbol : row.getAsString().toCharArray())
                if (symbol != ' ')
                    counts.merge(String.valueOf(symbol), 1, Integer::sum);
        var key = json.getAsJsonObject("key");
        var ingredients = new ArrayList<WorksRecipe.Ingredient<String>>();
        counts.forEach((symbol, count) -> ingredients.add(new WorksRecipe.Ingredient<>(options(key.get(symbol)), count)));
        return table(id, json, ingredients);
    }

    private static WorksRecipe<String> shapeless(String id, JsonObject json)
    {
        var ingredients = new ArrayList<WorksRecipe.Ingredient<String>>();
        for (var ingredient : json.getAsJsonArray("ingredients"))
            ingredients.add(new WorksRecipe.Ingredient<>(options(ingredient), 1));
        return table(id, json, ingredients);
    }

    private static WorksRecipe<String> table(String id, JsonObject json, List<WorksRecipe.Ingredient<String>> ingredients)
    {
        var result = json.getAsJsonObject("result");
        var count = result.has("count") ? result.get("count").getAsLong() : 1;
        return new WorksRecipe<>(id, StationKind.TABLE, List.of(new WorksRecipe.Stack<>(result.get("id").getAsString(), count)), ingredients, List.of(), Map.of());
    }

    private static List<String> options(JsonElement element)
    {
        if (element.isJsonArray())
        {
            var options = new ArrayList<String>();
            element.getAsJsonArray().forEach(option -> options.addAll(options(option)));
            return options;
        }
        if (element.isJsonObject())
        {
            var object = element.getAsJsonObject();
            return List.of(object.has("item") ? object.get("item").getAsString() : "#" + object.get("tag").getAsString());
        }
        return List.of(element.getAsString());
    }
}
