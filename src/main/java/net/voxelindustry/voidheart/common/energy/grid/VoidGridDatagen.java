package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W1 Void grid, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class VoidGridDatagen
{
    private VoidGridDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderColumn(generators, VoidGridContent.AZURIUM_PYLON, "voidstone_pillar_side", "voidstone_pillar_top");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(VoidGridContent.AZURIUM_PYLON);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
