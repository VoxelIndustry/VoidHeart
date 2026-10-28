package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.RecipeCallback;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShatterForgeRecipe extends RecipeBase
{
    @Getter
    private int time;
    @Getter
    private int monolithCount;
    /**
     * Minimum amount of each aspect the incoming beam must carry, in monolith pairs.
     */
    @Getter
    private Map<EssenceType, Integer> aspects = Map.of();
    /**
     * Essence drained from the collectors on completion, split by each aspect's share of the requirement.
     */
    @Getter
    private int draw;
    /**
     * Experience points drained from the experience skulls sitting on the monolith pairs, least filled first.
     */
    @Getter
    private int experience;

    public ShatterForgeRecipe(Identifier identifier)
    {
        super(VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getType(), identifier);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemLike output, ItemLike input, int time, int monolithCount)
    {
        this(identifier, new ItemStack(output), new ItemStack(input), time, monolithCount);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemStack output, ItemStack input, int time, int monolithCount)
    {
        this(identifier, new ItemStackRecipeIngredient(output, false), new ItemStackRecipeIngredient(input, false), time, monolithCount);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemLike output, ItemLike input, int time, int monolithCount, Map<EssenceType, Integer> aspects, int draw)
    {
        this(identifier, new ItemStack(output), new ItemStack(input), time, monolithCount, aspects, draw);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemStack output, ItemStack input, int time, int monolithCount, Map<EssenceType, Integer> aspects, int draw)
    {
        this(identifier, new ItemStackRecipeIngredient(output, false), new ItemStackRecipeIngredient(input, false), time, monolithCount, aspects, draw);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient input, int time, int monolithCount)
    {
        this(identifier, output, input, time, monolithCount, Map.of(), 0);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient input, int time, int monolithCount, Map<EssenceType, Integer> aspects, int draw)
    {
        this(identifier);

        addInputs(ItemStack.class, input);
        addOutputs(ItemStack.class, output);

        this.time = time;
        this.monolithCount = monolithCount;
        this.aspects = Map.copyOf(aspects);
        this.draw = draw;
    }

    public ShatterForgeRecipe withExperience(int experience)
    {
        this.experience = experience;
        return this;
    }

    public int totalAspectRequirement()
    {
        return aspects.values().stream().mapToInt(Integer::intValue).sum();
    }

    public ItemStack getOutput()
    {
        return this.getRecipeOutput(ItemStack.class, 0);
    }

    public RecipeBase onCraft(RecipeCallback onCraft)
    {
        super.onCraft = onCraft;
        return this;
    }

    @Override
    public void fromJson(JsonObject json)
    {
        super.fromJson(json);

        this.time = json.get("time").getAsInt();
        this.monolithCount = json.get("monolithCount").getAsInt();
        this.draw = json.has("draw") ? json.get("draw").getAsInt() : 0;
        this.experience = json.has("experience") ? json.get("experience").getAsInt() : 0;

        var parsed = new LinkedHashMap<EssenceType, Integer>();
        if (json.has("aspects"))
        {
            for (var entry : json.getAsJsonObject("aspects").entrySet())
            {
                var id = Identifier.tryParse(entry.getKey());
                if (id == null || !EssenceRegistry.ESSENCE_REGISTRY.containsKey(id) || id.equals(EssenceRegistry.EMPTY.identifier()))
                    throw new JsonParseException("Unknown aspect '" + entry.getKey() + "' in shatter forge recipe");
                parsed.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), entry.getValue().getAsInt());
            }
        }
        this.aspects = Map.copyOf(parsed);
    }

    @Override
    public void toJson(JsonObject json)
    {
        json.addProperty("time", time);
        json.addProperty("monolithCount", monolithCount);
        if (draw != 0)
            json.addProperty("draw", draw);
        if (experience != 0)
            json.addProperty("experience", experience);
        if (!aspects.isEmpty())
        {
            var aspectsJson = new JsonObject();
            aspects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(type -> type.identifier().toString())))
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
        this.monolithCount = buffer.readInt();

        int size = buffer.readInt();
        var parsed = new LinkedHashMap<EssenceType, Integer>();
        for (int i = 0; i < size; i++)
        {
            var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(Identifier.parse(buffer.readUtf()));
            parsed.put(type, buffer.readInt());
        }
        this.aspects = Map.copyOf(parsed);
        this.draw = buffer.readInt();
        this.experience = buffer.readInt();
    }

    @Override
    public void toByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        buffer.writeInt(monolithCount);

        buffer.writeInt(aspects.size());
        aspects.forEach((type, amount) ->
        {
            buffer.writeUtf(type.identifier().toString());
            buffer.writeInt(amount);
        });
        buffer.writeInt(draw);
        buffer.writeInt(experience);
    }
}
