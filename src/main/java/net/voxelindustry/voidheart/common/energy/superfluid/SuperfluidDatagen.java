package net.voxelindustry.voidheart.common.energy.superfluid;

import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.client.render.energy.superfluid.TesseractItemRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for superfluid azurium, called through EnergyDatagen by each provider. Rerun runDatagen after changing it. The levitation module
 * it is the catalyst of is made in RecipesProvider beside the other modules.
 */
public final class SuperfluidDatagen
{
    private SuperfluidDatagen()
    {
    }

    /**
     * Drawn as the turning hypercube by its own renderer, filled as far as the stack is; the base model only gives display transforms and particles.
     */
    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(SuperfluidContent.TESSERACT,
                ItemModelUtils.specialModel(Identifier.fromNamespaceAndPath(MODID, "item/tesseract_display"), new TesseractItemRender.Unbaked()));
    }

    /**
     * A void pearl's pocket walled in voidstone and folded through itself on ender and steel. Placeholder ingredients until picked from play.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var steel = VoidHeartItems.SEETHING_STEEL_INGOT;
        recipes.altar("tesseract", SuperfluidContent.TESSERACT, VoidHeartItems.VOID_PEARL,
                steel, steel, steel, steel,
                VoidHeartBlocks.VOIDSTONE, VoidHeartBlocks.VOIDSTONE,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
    }
}
