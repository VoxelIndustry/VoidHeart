package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for W9 Void Fissure, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * The fissure and wound are drawn only by their renderer, so their hand-written block models in assets/voidheart/models hold just a particle texture.
 */
public final class VoidFissureDatagen
{
    private VoidFissureDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidFissureContent.VOID_FISSURE, plainVariant(model("block/void_fissure"))));
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(VoidFissureContent.VOID_WOUND, plainVariant(model("block/void_wound"))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.generateFlatItem(VoidFissureContent.FISSURE_SEED, ModelTemplates.FLAT_ITEM);
    }

    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("fissure_seed", VoidFissureContent.FISSURE_SEED, VoidHeartBlocks.ELDRITCH_VOIDSTONE,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD,
                VoidHeartItems.AZURIUM_SHARD, VoidHeartItems.AZURIUM_SHARD,
                VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
    }

    /**
     * Neither drops anything: only Contravoid closes them.
     */
    public static void loot(BlockLootSubProvider loot)
    {
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
