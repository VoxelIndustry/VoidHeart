package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.common.block.StateProperties.*;

public class VoidConduitBlock extends Block
{
    public VoidConduitBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.BLACK)
                .instrument(Instrument.BASEDRUM)
                .requiresTool()
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

        setDefaultState(getStateManager().getDefaultState()
                .with(UP, false)
                .with(DOWN, false)
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(Properties.LIT, false));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom)
    {
        var connected = StateProperties.isSideConnected(state, direction);

        var side = getSideFromDirection(direction);
        if (connected && !newState.isOf(VoidHeartBlocks.VOID_CONDUIT))
            state = state.with(side, false);
        if (!connected && newState.isOf(VoidHeartBlocks.VOID_CONDUIT))
            state = state.with(side, true);

        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST, Properties.LIT);
    }
}
