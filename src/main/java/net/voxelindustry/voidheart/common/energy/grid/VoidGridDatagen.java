package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

/**
 * Datagen hand-off for W1 Void grid, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 */
public final class VoidGridDatagen
{
    private VoidGridDatagen()
    {
    }

    /**
     * The model is hand-written in assets/voidheart/models/block, since its claws reach into the block above.
     */
    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(VoidGridContent.AZURIUM_PYLON,
                plainVariant(ModelLocationUtils.getModelLocation(VoidGridContent.AZURIUM_PYLON))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
    }

    /**
     * The stem is a voidstone pillar, the claws ravenous gold, and azurium shards tune it to the bundle it will hold.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("azurium_pylon", VoidGridContent.AZURIUM_PYLON, VoidHeartBlocks.VOIDSTONE_PILLAR,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD,
                VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED,
                VoidHeartItems.ENDER_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(VoidGridContent.AZURIUM_PYLON);
    }

    public static void tags(EnergyTags tags)
    {
    }
}
