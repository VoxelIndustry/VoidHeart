package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.RecipeCallback;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

public class AltarRecipe extends RecipeBase
{
    @Getter
    private int time;

    public AltarRecipe(ResourceLocation identifier)
    {
        super(VoidHeartRecipes.ALTAR_CATEGORY.getType(), identifier);
    }

    public AltarRecipe(ResourceLocation identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient catalyst, int time, ItemStackRecipeIngredient... ingredients)
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
    public void fromByteBuf(FriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();
    }

    @Override
    public void toByteBuf(FriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
    }
}
