package net.voxelindustry.voidheart.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

public class VoidHeartEMIPlugin implements EmiPlugin
{
    public static final EmiStack          ALTAR_WORKSTATION  = EmiStack.of(VoidHeartBlocks.VOID_ALTAR);
    public static final EmiRecipeCategory ALTAR_EMI_CATEGORY = new EmiRecipeCategory(new Identifier(VoidHeart.MODID, "altar"), ALTAR_WORKSTATION);

    @Override
    public void register(EmiRegistry registry)
    {
        registry.addCategory(ALTAR_EMI_CATEGORY);
        registry.addWorkstation(ALTAR_EMI_CATEGORY, ALTAR_WORKSTATION);

        var manager = registry.getRecipeManager();

        for (var recipe : manager.listAllOfType(VoidHeartRecipes.ALTAR_CATEGORY.getType()))
            registry.addRecipe(new AltarEMIRecipe(recipe));
    }
}
