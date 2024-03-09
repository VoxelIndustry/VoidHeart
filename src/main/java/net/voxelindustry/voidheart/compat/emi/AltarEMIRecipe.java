package net.voxelindustry.voidheart.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import org.joml.Vector2i;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Getter
public class AltarEMIRecipe implements EmiRecipe
{
    private final Identifier id;

    private final List<EmiIngredient> inputs;
    private final List<EmiStack>      outputs;

    private final int time;

    public AltarEMIRecipe(AltarRecipe recipe)
    {
        id = recipe.getId();

        inputs = recipe.getRecipeInputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).collect(toList());
        outputs = recipe.getRecipeOutputs(ItemStack.class).stream().map(ingredient -> EmiStack.of(ingredient.getRaw())).collect(toList());

        time = recipe.getTime() * 50;
    }

    @Override
    public EmiRecipeCategory getCategory()
    {
        return VoidHeartEMIPlugin.ALTAR_EMI_CATEGORY;
    }

    @Override
    public int getDisplayWidth()
    {
        return 135;
    }

    @Override
    public int getDisplayHeight()
    {
        return 90;
    }

    private final Vector2i[] positions = new Vector2i[]{
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
