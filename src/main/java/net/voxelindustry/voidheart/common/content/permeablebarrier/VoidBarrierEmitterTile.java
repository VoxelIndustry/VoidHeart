package net.voxelindustry.voidheart.common.content.permeablebarrier;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

public class VoidBarrierEmitterTile extends TileBase
{
    @Getter
    @Setter
    private UUID owner;

    public VoidBarrierEmitterTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_BARRIER_EMITTER, pos, state);
    }
}
