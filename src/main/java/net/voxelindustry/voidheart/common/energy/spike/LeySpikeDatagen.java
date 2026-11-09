package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W8 Spike, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class LeySpikeDatagen
{
    private LeySpikeDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderColumn(generators, LeySpikeContent.AZURIUM_SPIKE, "voidstone_pillar_weathered_side", "voidstone_pillar_weathered_top");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(LeySpikeContent.AZURIUM_SPIKE);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
