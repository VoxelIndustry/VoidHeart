package net.voxelindustry.voidheart.common.content.depths;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Algae standing in a column from a floor or ceiling as if held up by water that isn't there. Cutting it brings down everything past the cut.
 */
public class StrandPlantBlock extends Block
{
    public static final BooleanProperty TIP = BooleanProperty.create("tip");

    private static final VoxelShape SHAPE = Block.column(12, 0, 16);

    private final Direction growth;

    public StrandPlantBlock(Direction growth, Properties properties)
    {
        super(properties);
        this.growth = growth;
        registerDefaultState(defaultBlockState().setValue(TIP, true));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        var support = pos.relative(growth.getOpposite());
        var supporting = level.getBlockState(support);
        return supporting.is(this) || supporting.isFaceSturdy(level, support, growth);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (!state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        return direction == growth ? state.setValue(TIP, !neighbourState.is(this)) : state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var next = context.getLevel().getBlockState(context.getClickedPos().relative(growth));
        return defaultBlockState().setValue(TIP, !next.is(this));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(TIP);
    }
}
