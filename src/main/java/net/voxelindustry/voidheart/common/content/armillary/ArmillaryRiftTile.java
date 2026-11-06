package net.voxelindustry.voidheart.common.content.armillary;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * A dungeon's rift holds nothing and lives as long as the dungeon. One on a bridge of the Daedalus belongs to the connection that opened it, and goes with it.
 */
public class ArmillaryRiftTile extends BlockEntity
{
    public static final int NONE = -1;

    private static final int CHECK_INTERVAL = 20;

    @Getter
    @Setter
    private int connection = NONE;

    public ArmillaryRiftTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.ARMILLARY_RIFT, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, ArmillaryRiftTile rift)
    {
        if (rift.connection == NONE || !(world instanceof ServerLevel level) || (level.getGameTime() + pos.hashCode()) % CHECK_INTERVAL != 0)
            return;

        if (DaedalusConnections.get(level.getServer()).get(rift.connection) == null)
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        connection = tag.getIntOr("connection", NONE);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        if (connection != NONE)
            tag.putInt("connection", connection);
    }
}
