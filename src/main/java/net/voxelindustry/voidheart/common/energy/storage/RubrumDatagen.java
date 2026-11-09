package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W2 Rubrum storage, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class RubrumDatagen
{
    private RubrumDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderCube(generators, RubrumContent.RUBRUM_CONSTRUCT_CORE, "voidstone_engraved_eye");
        EnergyDatagen.placeholderCube(generators, RubrumContent.RUBRUM_CONSTRUCT_FRAME, "cut_arrogant_iron");
        EnergyDatagen.placeholderCube(generators, RubrumContent.CELL_SOCKET, "iron_inlaid_voidstone");
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        EnergyDatagen.placeholderItem(generators, RubrumContent.RUBRUM_CELL, "rubrum_shard");
    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_CORE);
        loot.dropSelf(RubrumContent.RUBRUM_CONSTRUCT_FRAME);
        loot.dropSelf(RubrumContent.CELL_SOCKET);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
