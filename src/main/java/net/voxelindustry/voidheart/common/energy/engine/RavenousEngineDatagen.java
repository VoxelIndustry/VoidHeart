package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W5 Ravenous engine, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class RavenousEngineDatagen
{
    private RavenousEngineDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderCube(generators, RavenousEngineContent.RAVENOUS_ENGINE, "cut_ravenous_gold");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(RavenousEngineContent.RAVENOUS_ENGINE);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
