package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

// Holds nothing: it only gives the rift a renderer
public class ArmillaryRiftTile extends BlockEntity
{
    public ArmillaryRiftTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.ARMILLARY_RIFT, pos, state);
    }
}
