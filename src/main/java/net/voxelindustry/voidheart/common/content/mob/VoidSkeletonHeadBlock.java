package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Stands on a floor or hangs on a wall, like a vanilla head, and looks away from what it was placed against.
 */
public class VoidSkeletonHeadBlock extends Block
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    private static final VoxelShape FLOOR_SHAPE = box(4, 0, 4, 12, 8, 12);
    private static final Map<Direction, VoxelShape> WALL_SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, box(4, 4, 8, 12, 12, 16),
            Direction.SOUTH, box(4, 4, 0, 12, 12, 8),
            Direction.WEST, box(8, 4, 4, 16, 12, 12),
            Direction.EAST, box(0, 4, 4, 8, 12, 12)));

    public VoidSkeletonHeadBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(WALL) ? WALL_SHAPES.get(state.getValue(FACING)) : FLOOR_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var face = context.getClickedFace();
        if (face.getAxis().isHorizontal())
            return defaultBlockState().setValue(FACING, face).setValue(WALL, true);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WALL);
    }
}
