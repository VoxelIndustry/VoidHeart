package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.ingredient.RecipeIngredient;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A brew of the athanor: the base set in its core, one reagent from each crucible in any order, and the aspects its intake gathers first.
 */
public class AthanorRecipe extends RecipeBase
{
    @Getter
    private int time;
    /**
     * Essence of each aspect gathered from the relay network before the brew can start, used up when it completes.
     */
    @Getter
    private Map<EssenceType, Integer> aspects = Map.of();

    public AthanorRecipe(Identifier identifier)
    {
        super(VoidHeartRecipes.ATHANOR_CATEGORY.getType(), identifier);
    }

    public AthanorRecipe(Identifier identifier, ItemStack output, ItemStack base, int time, Map<EssenceType, Integer> aspects, ItemStack... reagents)
    {
        this(identifier);

        var reagentIngredients = new ItemStackRecipeIngredient[reagents.length];
        for (int i = 0; i < reagents.length; i++)
            reagentIngredients[i] = new ItemStackRecipeIngredient(reagents[i], false);

        addInputs(ItemStack.class, new ItemStackRecipeIngredient(base, false), reagentIngredients);
        addOutputs(ItemStack.class, new ItemStackRecipeIngredient(output, false));

        this.time = time;
        this.aspects = Map.copyOf(aspects);
    }

    public RecipeIngredient<ItemStack> getBase()
    {
        return getRecipeInputs(ItemStack.class).get(0);
    }

    public List<RecipeIngredient<ItemStack>> getReagents()
    {
        var inputs = getRecipeInputs(ItemStack.class);
        return inputs.subList(1, inputs.size());
    }

    public ItemStack getOutput()
    {
        return getRecipeOutput(ItemStack.class, 0);
    }

    @Override
    public void fromJson(JsonObject json)
    {
        super.fromJson(json);

        this.time = json.get("time").getAsInt();

        var parsed = new LinkedHashMap<EssenceType, Integer>();
        if (json.has("aspects"))
        {
            for (var entry : json.getAsJsonObject("aspects").entrySet())
            {
                var id = Identifier.tryParse(entry.getKey());
                if (id == null || !EssenceRegistry.ESSENCE_REGISTRY.containsKey(id) || id.equals(EssenceRegistry.EMPTY.identifier()))
                    throw new JsonParseException("Unknown aspect '" + entry.getKey() + "' in athanor recipe");
                parsed.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), entry.getValue().getAsInt());
            }
        }
        this.aspects = Map.copyOf(parsed);
    }

    @Override
    public void toJson(JsonObject json)
    {
        json.addProperty("time", time);
        if (!aspects.isEmpty())
        {
            var aspectsJson = new JsonObject();
            aspects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(type -> type.identifier().toString())))
                    .forEach(entry -> aspectsJson.addProperty(entry.getKey().identifier().toString(), entry.getValue()));
            json.add("aspects", aspectsJson);
        }

        super.toJson(json);
    }

    @Override
    public void fromByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();

        int size = buffer.readInt();
        var parsed = new LinkedHashMap<EssenceType, Integer>();
        for (int i = 0; i < size; i++)
        {
            var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(Identifier.parse(buffer.readUtf()));
            parsed.put(type, buffer.readInt());
        }
        this.aspects = Map.copyOf(parsed);
    }

    @Override
    public void toByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        buffer.writeInt(aspects.size());
        aspects.forEach((type, amount) ->
        {
            buffer.writeUtf(type.identifier().toString());
            buffer.writeInt(amount);
        });
    }
}
