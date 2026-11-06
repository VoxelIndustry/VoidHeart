package net.voxelindustry.voidheart.common.content.research;

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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * An upright frame, its warp facing the way it was placed from.
 */
public class VoidLoomBlock extends RepositoryUpgradeBlock<VoidLoomTile>
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape ACROSS_X = Block.box(0, 0, 5, 16, 16, 11);
    private static final VoxelShape ACROSS_Z = Block.box(5, 0, 0, 11, 16, 16);

    public VoidLoomBlock(Properties properties)
    {
        super(properties, ACROSS_X, () -> VoidHeartTiles.VOID_LOOM, VoidLoomTile::new, null);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_X : ACROSS_Z;
    }
}
