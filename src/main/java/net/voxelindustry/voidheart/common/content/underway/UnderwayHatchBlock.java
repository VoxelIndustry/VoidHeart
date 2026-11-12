package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A plate of the hatch that closes the Underway, lying in a district's floor. A plate along the line where the two leaves meet faces that line, the others face up. */
public class UnderwayHatchBlock extends Block
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public UnderwayHatchBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }
}
