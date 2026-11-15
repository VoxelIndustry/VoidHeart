package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.RecipeCallback;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemTagRecipeIngredient;
import net.voxelindustry.steamlayer.recipe.ingredient.RecipeIngredient;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.List;

public class AltarRecipe extends RecipeBase
{
    @Getter
    private int time;
    /**
     * Experience points drained from the nearby experience skulls once every ingredient is eaten, least filled first.
     */
    @Getter
    private int experience;

    public AltarRecipe(Identifier identifier)
    {
        super(VoidHeartRecipes.ALTAR_TYPE, identifier);
    }

    /**
     * @param output a fixed stack, or an {@link net.voxelindustry.steamlayer.recipe.ingredient.ItemStackTransformIngredient} made from the catalyst
     */
    @SafeVarargs
    public AltarRecipe(Identifier identifier, RecipeIngredient<ItemStack> output, RecipeIngredient<ItemStack> catalyst, int time, RecipeIngredient<ItemStack>... ingredients)
    {
        this(identifier);

        addInputs(ItemStack.class, catalyst, ingredients);
        addOutputs(ItemStack.class, output);

        this.time = time;
    }

    /**
     * Recipe without an output, its effect is given by an onCraft callback.
     */
    @SafeVarargs
    public AltarRecipe(Identifier identifier, RecipeIngredient<ItemStack> catalyst, int time, RecipeIngredient<ItemStack>... ingredients)
    {
        this(identifier);

        addInputs(ItemStack.class, catalyst, ingredients);

        this.time = time;
    }

    /**
     * What the altar gives back for this catalyst: the fixed output, or the catalyst itself transformed.
     */
    public ItemStack resultFor(ItemStack catalyst)
    {
        return getRecipeOutput(ItemStack.class, 0, List.of(catalyst));
    }

    /**
     * A recipe that transforms its catalyst has nothing to do when the catalyst already is its result, such as a sin infused into a weapon of that sin.
     */
    public boolean changes(ItemStack catalyst)
    {
        if (!hasTransformingOutput())
            return true;
        return !ItemStack.matches(resultFor(catalyst), catalyst);
    }

    /**
     * Whether the catalyst is matched by a tag rather than by one item.
     */
    public boolean hasTagCatalyst()
    {
        var inputs = getRecipeInputs(ItemStack.class);
        return !inputs.isEmpty() && inputs.getFirst() instanceof ItemTagRecipeIngredient;
    }

    /**
     * Every catalyst the recipe takes, one stack each, for displays.
     */
    public List<ItemStack> catalystChoices()
    {
        var inputs = getRecipeInputs(ItemStack.class);
        if (inputs.isEmpty())
            return List.of();
        if (inputs.getFirst() instanceof ItemTagRecipeIngredient tag)
            return tag.getMatchingStacks();
        return List.of(inputs.getFirst().getRaw());
    }

    /**
     * The result for each of {@link #catalystChoices()}, in the same order, so a display cycles both together.
     */
    public List<ItemStack> resultChoices()
    {
        if (!hasOutputType(ItemStack.class))
            return List.of();
        return catalystChoices().stream().map(this::resultFor).toList();
    }

    public AltarRecipe withExperience(int experience)
    {
        this.experience = experience;
        return this;
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
        this.experience = json.has("experience") ? json.get("experience").getAsInt() : 0;
    }

    @Override
    public void toJson(JsonObject json)
    {
        json.addProperty("time", time);
        if (experience != 0)
            json.addProperty("experience", experience);

        super.toJson(json);
    }

    @Override
    public void fromByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();
        this.experience = buffer.readInt();
    }

    @Override
    public void toByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        buffer.writeInt(experience);
    }
}
