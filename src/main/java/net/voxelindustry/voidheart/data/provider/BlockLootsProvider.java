package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.concurrent.CompletableFuture;

public class BlockLootsProvider extends FabricBlockLootSubProvider
{
    public BlockLootsProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(dataOutput, registriesFuture);
    }

    @Override
    public void generate()
    {
        this.dropSelf(VoidHeartBlocks.CUT_RAVENOUS_GOLD);
        this.dropSelf(VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);
        this.dropSelf(VoidHeartBlocks.OCULUS_TABLE);
        this.dropSelf(VoidHeartBlocks.BEHOLDER);

        dropMetalFamily(VoidHeartBlocks.SEETHING_STEEL_BLOCK, VoidHeartBlocks.CUT_SEETHING_STEEL,
                VoidHeartBlocks.CUT_SEETHING_STEEL_STAIRS, VoidHeartBlocks.CUT_SEETHING_STEEL_SLAB, VoidHeartBlocks.CUT_SEETHING_STEEL_WALL);
        dropMetalFamily(VoidHeartBlocks.COVETOUS_COPPER_BLOCK, VoidHeartBlocks.CUT_COVETOUS_COPPER,
                VoidHeartBlocks.CUT_COVETOUS_COPPER_STAIRS, VoidHeartBlocks.CUT_COVETOUS_COPPER_SLAB, VoidHeartBlocks.CUT_COVETOUS_COPPER_WALL);
        dropMetalFamily(VoidHeartBlocks.LISTLESS_LEAD_BLOCK, VoidHeartBlocks.CUT_LISTLESS_LEAD,
                VoidHeartBlocks.CUT_LISTLESS_LEAD_STAIRS, VoidHeartBlocks.CUT_LISTLESS_LEAD_SLAB, VoidHeartBlocks.CUT_LISTLESS_LEAD_WALL);

        dropBundles(VoidHeartItems.AZURIUM_SHARD, VoidHeartBlocks.SMALL_AZURIUM_BUNDLE, VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE, VoidHeartBlocks.LARGE_AZURIUM_BUNDLE);
        dropBundles(VoidHeartItems.RUBRUM_SHARD, VoidHeartBlocks.SMALL_RUBRUM_BUNDLE, VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE, VoidHeartBlocks.LARGE_RUBRUM_BUNDLE);
        dropBundles(VoidHeartItems.MEMORIAM_SHARD, VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE, VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE, VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE);
    }

    private void dropBundles(Item shard, Block small, Block medium, Block large)
    {
        dropBundle(small, shard, 1, 1);
        dropBundle(medium, shard, 1, 2);
        dropBundle(large, shard, 2, 4);
    }

    /**
     * Silk touch keeps the bundle whole, anything else breaks it into shards that fortune multiplies.
     */
    private void dropBundle(Block bundle, Item shard, int min, int max)
    {
        var shards = LootItem.lootTableItem(shard)
                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
                .apply(ApplyBonusCount.addOreBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)));
        LootPoolEntryContainer.Builder<?> entry = applyExplosionDecay(bundle, shards);
        add(bundle, createSilkTouchDispatchTable(bundle, entry));
    }

    private void dropMetalFamily(Block block, Block cut, Block stairs, Block slab, Block wall)
    {
        this.dropSelf(block);
        this.dropSelf(cut);
        this.dropSelf(stairs);
        this.add(slab, this::createSlabItemTable);
        this.dropSelf(wall);
    }
}
