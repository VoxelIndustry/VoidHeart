package net.voxelindustry.voidheart.common.energy.engine;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
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
 * Datagen hand-off for W5 Ravenous engine, called through EnergyDatagen by each provider. Rerun runDatagen after changing it.
 * Its block and item models are hand-written in assets/voidheart/models, since the maw is hollow.
 */
public final class RavenousEngineDatagen
{
    private RavenousEngineDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(RavenousEngineContent.RAVENOUS_ENGINE)
                .with(PropertyDispatch.initial(RavenousEngineBlock.PART)
                        .select(RavenousEngineBlock.Part.MAW, plainVariant(model("block/ravenous_engine_maw")))
                        .select(RavenousEngineBlock.Part.CHAMBER, plainVariant(model("block/ravenous_engine_chamber")))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(RavenousEngineContent.RAVENOUS_ENGINE.asItem(), ItemModelUtils.plainModel(model("item/ravenous_engine")));
    }

    /**
     * A medium azurium bundle per engine, so that a row of engines costs more than one reactor (energy-numbers.md, risk 1).
     */
    public static void recipes(EnergyRecipes recipes)
    {
        recipes.altar("ravenous_engine", RavenousEngineContent.RAVENOUS_ENGINE, VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                VoidHeartBlocks.VOIDSTONE_BRICKS, VoidHeartBlocks.VOIDSTONE_BRICKS,
                VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        // The engine's getDrops keeps its essence on the item, this table stands for the plain drop
        loot.dropSelf(RavenousEngineContent.RAVENOUS_ENGINE);
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
