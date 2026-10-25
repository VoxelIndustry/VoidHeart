package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import me.shedaniel.rei.api.common.registry.display.ServerDisplayRegistry;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Recipes only exist on the server, displays are built there and synced to REI clients.
 */
public class VoidHeartREICommonPlugin implements REICommonPlugin
{
    @Override
    public void registerDisplaySerializer(DisplaySerializerRegistry registry)
    {
        registry.register(Identifier.fromNamespaceAndPath(MODID, "altar"), AltarRecipeDisplay.SERIALIZER);
    }

    @Override
    public void registerDisplays(ServerDisplayRegistry registry)
    {
        registry.beginRecipeFiller(AltarRecipe.class)
                .filterType(VoidHeartRecipes.ALTAR_TYPE)
                .fill(AltarRecipeDisplay::new);
    }
}
