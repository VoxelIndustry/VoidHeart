package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousRelayTile extends BlockEntity
{
    public RavenousRelayTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_RELAY, pos, state);
    }
}
