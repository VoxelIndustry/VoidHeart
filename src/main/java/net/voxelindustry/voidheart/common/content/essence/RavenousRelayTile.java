package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousRelayTile extends BlockEntity
{
    public RavenousRelayTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_RELAY, pos, state);
    }
}
