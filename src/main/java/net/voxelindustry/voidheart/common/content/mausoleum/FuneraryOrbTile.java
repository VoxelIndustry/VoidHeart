package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/** Holds nothing: it is only there for the orb's renderer. */
public class FuneraryOrbTile extends BlockEntity
{
    public FuneraryOrbTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.FUNERARY_ORB, pos, state);
    }
}
