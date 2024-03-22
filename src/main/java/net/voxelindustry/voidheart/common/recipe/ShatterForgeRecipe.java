package net.voxelindustry.voidheart.common.recipe;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
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

    public ShatterForgeRecipe(Identifier identifier)
    {
        super(VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getType(), identifier);
    }

    public ShatterForgeRecipe(Identifier identifier, ItemStackRecipeIngredient output, ItemStackRecipeIngredient catalyst, int time, int monolithCount)
    {
        this(identifier);

        addInputs(ItemStack.class, catalyst);
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
    public void fromByteBuf(PacketByteBuf buffer)
    {
        super.fromByteBuf(buffer);

        this.time = buffer.readInt();
        this.monolithCount = buffer.readInt();
    }

    @Override
    public void toByteBuf(PacketByteBuf buffer)
    {
        super.toByteBuf(buffer);

        buffer.writeInt(time);
        buffer.writeInt(monolithCount);
    }
}
