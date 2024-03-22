package net.voxelindustry.voidheart.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;

@Getter
public class ShatterForgeEMIRecipe extends BasicEmiRecipe
{
    private final int time;

    public ShatterForgeEMIRecipe(ShatterForgeRecipe recipe)
    {
        super(VoidHeartEMIPlugin.SHATTER_FORGE_EMI_CATEGORY, recipe.getId(), 74, 38);

        recipe.getRecipeInputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).forEach(inputs::add);
        recipe.getRecipeOutputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).forEach(outputs::add);

        time = recipe.getTime() * 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets)
    {
        widgets.addSlot(inputs.get(0), 0, 4);

        if (outputs.isEmpty())
            return;

        widgets.addFillingArrow(24, 5, time);

        widgets.addSlot(outputs.get(0), 56, 4).recipeContext(this);
    }
}
