package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.createSimpleBlock;
import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for W11 Deep siphon, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * Its block model is hand-written in assets/voidheart/models, since its crown stands above the block, and the item shows that model.
 */
public final class DeepSiphonDatagen
{
    private DeepSiphonDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(createSimpleBlock(DeepSiphonContent.DEEP_SIPHON, plainVariant(model("block/deep_siphon"))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(DeepSiphonContent.DEEP_SIPHON.asItem(), ItemModelUtils.plainModel(model("block/deep_siphon")));
    }

    /**
     * A large azurium bundle to carry what it draws, and void pearls like the way it stands on.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("deep_siphon", DeepSiphonContent.DEEP_SIPHON, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                VoidHeartBlocks.VOIDSTONE_POLISHED, VoidHeartBlocks.VOIDSTONE_POLISHED,
                VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        loot.dropSelf(DeepSiphonContent.DEEP_SIPHON);
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
