package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
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
    }
}
