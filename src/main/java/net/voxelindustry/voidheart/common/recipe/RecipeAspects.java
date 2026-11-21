package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The essence a recipe takes, an amount per aspect, as written in its JSON under "aspects" and sent to clients.
 */
final class RecipeAspects
{
    private static final String KEY = "aspects";

    private RecipeAspects()
    {
    }

    static Map<EssenceType, Integer> fromJson(JsonObject json, String recipeKind)
    {
        var parsed = new LinkedHashMap<EssenceType, Integer>();
        if (json.has(KEY))
        {
            for (var entry : json.getAsJsonObject(KEY).entrySet())
            {
                var id = Identifier.tryParse(entry.getKey());
                if (id == null || !EssenceRegistry.ESSENCE_REGISTRY.containsKey(id) || id.equals(EssenceRegistry.EMPTY.identifier()))
                    throw new JsonParseException("Unknown aspect '" + entry.getKey() + "' in " + recipeKind + " recipe");
                parsed.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), entry.getValue().getAsInt());
            }
        }
        return Map.copyOf(parsed);
    }

    static void toJson(JsonObject json, Map<EssenceType, Integer> aspects)
    {
        if (aspects.isEmpty())
            return;

        var aspectsJson = new JsonObject();
        aspects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(type -> type.identifier().toString())))
                .forEach(entry -> aspectsJson.addProperty(entry.getKey().identifier().toString(), entry.getValue()));
        json.add(KEY, aspectsJson);
    }

    static Map<EssenceType, Integer> fromByteBuf(RegistryFriendlyByteBuf buffer)
    {
        int size = buffer.readInt();
        var parsed = new LinkedHashMap<EssenceType, Integer>();
        for (int i = 0; i < size; i++)
        {
            var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(Identifier.parse(buffer.readUtf()));
            parsed.put(type, buffer.readInt());
        }
        return Map.copyOf(parsed);
    }

    static void toByteBuf(RegistryFriendlyByteBuf buffer, Map<EssenceType, Integer> aspects)
    {
        buffer.writeInt(aspects.size());
        aspects.forEach((type, amount) ->
        {
            buffer.writeUtf(type.identifier().toString());
            buffer.writeInt(amount);
        });
    }
}
