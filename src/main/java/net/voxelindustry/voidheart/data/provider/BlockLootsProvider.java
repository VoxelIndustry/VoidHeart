package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

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
