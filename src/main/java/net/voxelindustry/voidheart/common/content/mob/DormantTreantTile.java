package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Watches for a player close enough to the trunk to be reached once the Treant is out. It keeps nothing: until then the trunk is all there is.
 */
public class DormantTreantTile extends TileBase
{
    public static final double WAKE_RANGE = 6;

    private static final int CHECK_INTERVAL = 10;

    public DormantTreantTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.DORMANT_TREANT, pos, state);
    }

    void tickServer(ServerLevel level)
    {
        if ((level.getGameTime() + getBlockPos().asLong()) % CHECK_INTERVAL != 0 || level.getDifficulty() == Difficulty.PEACEFUL)
            return;

        var pos = getBlockPos();
        var player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, WAKE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (player != null)
            DormantTreantBlock.awaken(level, pos, player, true);
    }
}
