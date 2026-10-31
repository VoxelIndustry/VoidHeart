package net.voxelindustry.voidheart.common.content.permeablebarrier;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class PermeableBarrierTile extends TileBase
{
    @Getter
    private BarrierConfig config = BarrierConfig.EMPTY;

    public PermeableBarrierTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.PERMEABLE_BARRIER, pos, state);
    }

    public void setConfig(BarrierConfig config)
    {
        if (this.config.equals(config))
            return;
        this.config = config;
        setChanged();
        sync();

        if (level != null && !level.isClientSide())
        {
            var lit = PermeableBarrierBlock.withDisguisedLight(getBlockState(), config);
            if (lit != getBlockState())
                level.setBlock(worldPosition, lit, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        config = nbt.read("config", BarrierConfig.CODEC).orElse(BarrierConfig.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("config", BarrierConfig.CODEC, config);
    }
}
