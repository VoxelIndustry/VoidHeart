package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Weaves the loose void into order for a repository, so that the price of its insights grows a quarter slower. A repository only
 * ever holds one.
 */
public class VoidLoomTile extends RepositoryBoundTile
{
    public VoidLoomTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_LOOM, pos, state);
    }
}
