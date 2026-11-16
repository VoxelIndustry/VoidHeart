package net.voxelindustry.voidheart.common.energy.gnawer;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.data.energy.EnergyRecipes;
import net.voxelindustry.voidheart.data.energy.EnergyTags;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Datagen hand-off for the Gnawer, called through EnergyDatagen by each provider. Rerun runDatagen after changing it. Both block models are hand-written
 * in assets/voidheart/models, and the items show them.
 */
public final class GnawerDatagen
{
    private GnawerDatagen()
    {
    }

    public static void blockModels(BlockModelGenerators generators)
    {
        // Keeping and erasing look the same here, the jaw's teeth tell them apart
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(GnawerContent.GNAWER, plainVariant(model("block/gnawer"))));
        generators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(GnawerContent.GNAWING_STAKE, plainVariant(model("block/gnawing_stake"))));
    }

    public static void itemModels(ItemModelGenerators generators)
    {
        generators.itemModelOutput.accept(GnawerContent.GNAWER.asItem(), ItemModelUtils.plainModel(model("block/gnawer")));
        generators.itemModelOutput.accept(GnawerContent.GNAWING_STAKE.asItem(), ItemModelUtils.plainModel(model("block/gnawing_stake")));
    }

    /**
     * Placeholders until real costs are picked from play: Ravenous Gold around a Ravenous Engine, and two stakes from an azurium shard over iron bars.
     */
    public static void recipes(EnergyRecipes recipes)
    {
        var provider = recipes.provider();
        var output = recipes.output();
        var gold = VoidHeartItems.RAVENOUS_GOLD_INGOT;

        provider.shaped(RecipeCategory.TOOLS, GnawerContent.GNAWER)
                .pattern("GGG").pattern("GEG").pattern("GGG")
                .define('G', gold)
                .define('E', RavenousEngineContent.RAVENOUS_ENGINE)
                .unlockedBy(provider.getHasName(RavenousEngineContent.RAVENOUS_ENGINE), provider.has(RavenousEngineContent.RAVENOUS_ENGINE))
                .save(output);

        provider.shaped(RecipeCategory.TOOLS, GnawerContent.GNAWING_STAKE, 2)
                .pattern("A").pattern("B").pattern("B")
                .define('A', VoidHeartItems.AZURIUM_SHARD)
                .define('B', Items.IRON_BARS)
                .unlockedBy(provider.getHasName(VoidHeartItems.AZURIUM_SHARD), provider.has(VoidHeartItems.AZURIUM_SHARD))
                .save(output);
    }

    public static void loot(BlockLootSubProvider loot)
    {
        // The Gnawer's lent pickaxe drops from the tile, the table stands for the block itself
        loot.dropSelf(GnawerContent.GNAWER);
        loot.dropSelf(GnawerContent.GNAWING_STAKE);
    }

    public static void tags(EnergyTags tags)
    {
    }

    private static Identifier model(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
