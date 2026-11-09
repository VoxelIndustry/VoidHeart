package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W10 Contravoid reactor, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class ContravoidReactorDatagen
{
    private ContravoidReactorDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderCube(generators, ContravoidReactorContent.CONTRAVOID_REACTOR_CORE, "cut_seething_steel");
        EnergyDatagen.placeholderCube(generators, ContravoidReactorContent.REACTOR_CONTAINMENT, "cut_listless_lead");
        EnergyDatagen.placeholderCube(generators, ContravoidReactorContent.REACTOR_INTAKE, "cut_covetous_copper");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE);
        loot.dropSelf(ContravoidReactorContent.REACTOR_CONTAINMENT);
        loot.dropSelf(ContravoidReactorContent.REACTOR_INTAKE);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
