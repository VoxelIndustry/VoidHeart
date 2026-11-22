package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.ingredient.RecipeIngredient;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

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

        this.aspects = RecipeAspects.fromJson(json, "athanor");
    }

    @Override
    public void toJson(JsonObject json)
    {
        json.addProperty("time", time);
        RecipeAspects.toJson(json, aspects);

        super.toJson(json);
    }

    @Override
    public void fromByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();

        this.aspects = RecipeAspects.fromByteBuf(buffer);
    }

    @Override
    public void toByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        RecipeAspects.toByteBuf(buffer, aspects);
    }
}
