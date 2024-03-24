package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class BlockLootsProvider extends FabricBlockLootTableProvider
{
    public BlockLootsProvider(FabricDataOutput dataOutput)
    {
        super(dataOutput);
    }

    @Override
    public void generate()
    {
        this.addDrop(VoidHeartBlocks.CUT_RAVENOUS_GOLD);
        this.addDrop(VoidHeartBlocks.RAVENOUS_GOLD_BLOCK);
    }
}
