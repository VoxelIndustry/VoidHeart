package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A two-block column of rubrum grown around a collapsing pearl, storing void energy where it stands. Like a cell socket
 * it discharges into the field around it, or charges from it while either half gets a redstone signal. It can't be
 * pushed, and breaking it loses its charge. Only the lower half holds the tile and drops the cell; both halves show
 * how full it is in {@link #CHARGE}.
 */
public class LargeRubrumCellBlock extends Block implements EntityBlock
{
    public static final EnumProperty<DoubleBlockHalf> HALF   = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Glowing segments of the column, from {@link LargeCellCharge#segments}. */
    public static final IntegerProperty               CHARGE = IntegerProperty.create("charge", 0, LargeCellCharge.SEGMENTS);

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public LargeRubrumCellBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(HALF, CHARGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var pos = context.getClickedPos();
        var level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context))
            return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack)
    {
        level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER)
            return true;
        var below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Either half goes when the other does, as a door's do.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbour, BlockPos neighbourPos,
                                     BlockState neighbour, RandomSource random)
    {
        var half = state.getValue(HALF);
        if (toNeighbour.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (toNeighbour == Direction.UP))
            return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, toNeighbour, neighbourPos, neighbour, random);
    }

    /**
     * Breaking the upper half in creative or without a pickaxe removes the lower one without its drop.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state)) && state.getValue(HALF) == DoubleBlockHalf.UPPER)
        {
            var below = pos.below();
            var lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER)
            {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * The tile of the cell either half belongs to.
     */
    public static @Nullable LargeRubrumCellTile tileOf(BlockGetter level, BlockPos pos, BlockState state)
    {
        var lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return level.getBlockEntity(lower) instanceof LargeRubrumCellTile tile ? tile : null;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        var tile = tileOf(level, pos, state);
        return tile != null ? tile.comparatorSignal() : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new LargeRubrumCellTile(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? createTickerHelper(type, RubrumContent.LARGE_RUBRUM_CELL_TILE, LargeRubrumCellTile::tick) : null;
    }
}
