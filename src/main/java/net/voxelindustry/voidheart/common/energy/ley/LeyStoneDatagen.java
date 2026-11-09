package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.data.energy.EnergyDatagen;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

/**
 * Datagen hand-off for W4 Standing stones and wards, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class LeyStoneDatagen
{
    private LeyStoneDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        EnergyDatagen.placeholderColumn(generators, LeyStoneContent.LEY_STONE, "ley_anchor_side_inactive", "ley_anchor_top_inactive");
        EnergyDatagen.placeholderCube(generators, LeyStoneContent.LEY_WARD, "voidstone_tile_small_carved");
        EnergyDatagen.placeholderCube(generators, LeyStoneContent.LEY_GROWER, "bronze_inlaid_voidstone");
    }

    public static void itemModels(ItemModelGenerators generators)
    {

    }

    public static void recipes(EnergyRecipes recipes)
    {
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(LeyStoneContent.LEY_STONE);
        loot.dropSelf(LeyStoneContent.LEY_WARD);
        loot.dropSelf(LeyStoneContent.LEY_GROWER);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
