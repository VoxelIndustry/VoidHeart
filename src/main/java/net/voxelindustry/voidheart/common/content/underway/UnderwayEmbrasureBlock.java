package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An armoured block pierced by a firing slit, looked through along its axis. */
public class UnderwayEmbrasureBlock extends Block
{
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** The slit, in sixteenths of a block: as wide as the block less a jamb each side, at eye height. */
    public static final int JAMB = 2;
    public static final int SLIT_BOTTOM = 7;
    public static final int SLIT_TOP = 10;

    private static final VoxelShape ALONG_Z = Shapes.or(Block.box(0, 0, 0, 16, SLIT_BOTTOM, 16), Block.box(0, SLIT_TOP, 0, 16, 16, 16),
            Block.box(0, SLIT_BOTTOM, 0, JAMB, SLIT_TOP, 16), Block.box(16 - JAMB, SLIT_BOTTOM, 0, 16, SLIT_TOP, 16));
    private static final VoxelShape ALONG_X = Shapes.or(Block.box(0, 0, 0, 16, SLIT_BOTTOM, 16), Block.box(0, SLIT_TOP, 0, 16, 16, 16),
            Block.box(0, SLIT_BOTTOM, 0, 16, SLIT_TOP, JAMB), Block.box(0, SLIT_BOTTOM, 16 - JAMB, 16, SLIT_TOP, 16));

    public UnderwayEmbrasureBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(AXIS) == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AXIS);
    }
}
