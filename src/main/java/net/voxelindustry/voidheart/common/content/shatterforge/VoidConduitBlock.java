package net.voxelindustry.voidheart.common.content.shatterforge;

import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.common.block.StateProperties.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class VoidConduitBlock extends Block
{
    public VoidConduitBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .sound(SoundType.STONE));

        registerDefaultState(getStateDefinition().any()
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState newState, LevelAccessor world, BlockPos pos, BlockPos posFrom)
    {
        var connected = StateProperties.isSideConnected(state, direction);

        var side = getSideFromDirection(direction);
        if (connected && !newState.is(VoidHeartBlocks.VOID_CONDUIT))
            state = state.setValue(side, false);
        if (!connected && newState.is(VoidHeartBlocks.VOID_CONDUIT))
            state = state.setValue(side, true);

        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST, BlockStateProperties.LIT);
    }
}
