package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for W8 Spike, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * The block's renderer draws the leaning stake, so its block model only names the particle, and its item model is a hand-written upright stake.
 */
public final class LeySpikeDatagen
{
    private LeySpikeDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(LeySpikeContent.AZURIUM_SPIKE, plainVariant(model("block/azurium_spike"))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(LeySpikeContent.AZURIUM_SPIKE.asItem(), ItemModelUtils.plainModel(model("item/azurium_spike")));
    }

    /**
     * A large azurium bundle to drive in, since one spike gives as much as three engines at its heaviest.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("azurium_spike", LeySpikeContent.AZURIUM_SPIKE, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE,
                VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        // Only the base drops, see AzuriumSpikeBlock.getDrops
        loot.dropSelf(LeySpikeContent.AZURIUM_SPIKE);
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
