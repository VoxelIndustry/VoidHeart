package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.voxelindustry.steamlayer.recipe.RecipeBase;
import net.voxelindustry.steamlayer.recipe.RecipeCallback;
import net.voxelindustry.steamlayer.recipe.ingredient.ItemStackRecipeIngredient;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

public class ShatterForgeRecipe extends RecipeBase
{
    @Getter
    private int time;
    @Getter
    private int monolithCount;

    public ShatterForgeRecipe(ResourceLocation identifier)
    {
        super(VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getType(), identifier);
    }

    public ShatterForgeRecipe(ResourceLocation identifier, ItemLike output, ItemLike input, int time, int monolithCount)
    {
        this(identifier, new ItemStack(output), new ItemStack(input), time, monolithCount);
    }

    public ShatterForgeRecipe(ResourceLocation identifier, ItemStack output, ItemStack input, int time, int monolithCount)
    {
        this(identifier, new ItemStackRecipeIngredient(output, false), new ItemStackRecipeIngredient(input, false), time, monolithCount);
    }

    public ShatterForgeRecipe(ResourceLocation identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient input, int time, int monolithCount)
    {
        this(identifier);

        addInputs(ItemStack.class, input);
        addOutputs(ItemStack.class, output);

        this.time = time;
        this.monolithCount = monolithCount;
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
    }

    @Override
    public void toJson(JsonObject json)
    {
        json.addProperty("time", time);
        json.addProperty("monolithCount", monolithCount);

        super.toJson(json);
    }

    @Override
    public void fromByteBuf(FriendlyByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();
        this.monolithCount = buffer.readInt();
    }

    @Override
    public void toByteBuf(FriendlyByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        buffer.writeInt(monolithCount);
    }
}
