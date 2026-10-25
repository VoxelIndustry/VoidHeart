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
    }

    @Override
    public void fromByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();
    }

    @Override
    public void toByteBuf(RegistryFriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
    }
}
