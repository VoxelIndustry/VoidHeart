package net.voxelindustry.voidheart.common.energy.tear;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W9 Rift, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class VoidTearDatagen
{
    private VoidTearDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderCube(generators, VoidTearContent.VOID_TEAR, "eldritch_voidstone");
        EnergyDatagen.placeholderCube(generators, VoidTearContent.VOID_WOUND, "voidstone_bricks_cracked");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(VoidTearContent.VOID_TEAR);
        loot.dropSelf(VoidTearContent.VOID_WOUND);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
