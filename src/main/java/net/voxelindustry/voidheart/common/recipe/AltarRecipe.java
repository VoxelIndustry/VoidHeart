package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.RecipeCallback;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

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
        super(VoidHeartRecipes.ALTAR_CATEGORY.getType(), identifier);
    }

    public AltarRecipe(Identifier identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient catalyst, int time, ItemStackRecipeIngredient... ingredients)
    {
        this(identifier);

        addInputs(ItemStack.class, catalyst, ingredients);
        addOutputs(ItemStack.class, output);

        this.time = time;
    }

    /**
     * Recipe without an output, its effect is given by an onCraft callback.
     */
    public AltarRecipe(Identifier identifier, ItemStackRecipeIngredient catalyst, int time, ItemStackRecipeIngredient... ingredients)
    {
        this(identifier);

        addInputs(ItemStack.class, catalyst, ingredients);

        this.time = time;
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
