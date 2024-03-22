package net.voxelindustry.voidheart.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import org.joml.Vector2i;

@Getter
public class AltarEMIRecipe extends BasicEmiRecipe
{
    private final int time;

    private final Vector2i[] positions = new Vector2i[] {
            new Vector2i(36, 36),

            new Vector2i(36 - 18, 36 + 36),
            new Vector2i(36 + 18, 36 + 36),
            new Vector2i(36 + 36, 36 + 18),
            new Vector2i(36 + 36, 36 - 18),

            new Vector2i(36 - 18, 0),
            new Vector2i(36 + 18, 0),
            new Vector2i(0, 36 + 18),
            new Vector2i(0, 36 - 18),

            new Vector2i(36, 36)
    };

    public AltarEMIRecipe(AltarRecipe recipe)
    {
        super(VoidHeartEMIPlugin.ALTAR_EMI_CATEGORY, recipe.getId(), 135, 90);

        recipe.getRecipeInputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).forEach(inputs::add);
        recipe.getRecipeOutputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).forEach(outputs::add);

        time = recipe.getTime() * 50;
    }

    @Override
    public void addWidgets(WidgetHolder widgets)
    {
        for (int i = 0; i < inputs.size(); i++)
        {
            var pos = positions[i];
            widgets.addSlot(inputs.get(i), pos.x, pos.y);
        }

        if (outputs.isEmpty())
            return;

        widgets.addFillingArrow(92, 36, time);

        widgets.addSlot(outputs.get(0), 117, 36).recipeContext(this);
    }
}
