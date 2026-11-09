package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W11 Deep siphon, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class DeepSiphonDatagen
{
    private DeepSiphonDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderCube(generators, DeepSiphonContent.DEEP_SIPHON, "voidstone_engraved_gear");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(DeepSiphonContent.DEEP_SIPHON);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
