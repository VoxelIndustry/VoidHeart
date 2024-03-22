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
    public static final EmiStack ALTAR_WORKSTATION = EmiStack.of(VoidHeartBlocks.VOID_ALTAR);
    public static final EmiRecipeCategory ALTAR_EMI_CATEGORY = new EmiRecipeCategory(new Identifier(VoidHeart.MODID, "altar"), ALTAR_WORKSTATION);

    public static final EmiStack SHATTER_FORGE_WORKSTATION = EmiStack.of(VoidHeartBlocks.SHATTER_FORGE);
    public static final EmiRecipeCategory SHATTER_FORGE_EMI_CATEGORY = new EmiRecipeCategory(new Identifier(VoidHeart.MODID, "shatter_forge"), SHATTER_FORGE_WORKSTATION);

    @Override
    public void register(EmiRegistry registry)
    {
        registry.addCategory(ALTAR_EMI_CATEGORY);
        registry.addCategory(SHATTER_FORGE_EMI_CATEGORY);

        registry.addWorkstation(ALTAR_EMI_CATEGORY, ALTAR_WORKSTATION);
        registry.addWorkstation(SHATTER_FORGE_EMI_CATEGORY, SHATTER_FORGE_WORKSTATION);

        var manager = registry.getRecipeManager();

        for (var recipe : manager.listAllOfType(VoidHeartRecipes.ALTAR_CATEGORY.getType()))
            registry.addRecipe(new AltarEMIRecipe(recipe));

        for (var recipe : manager.listAllOfType(VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getType()))
            registry.addRecipe(new ShatterForgeEMIRecipe(recipe));
    }
}
