package net.voxelindustry.voidheart.common.energy.ley;

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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A two-block menhir that bends a spur off a ley line toward a base. Like a door, it has a lower and an upper half that break together, and only the lower
 * one holds the tile and drops the stone. Its channel faces whoever raised it.
 */
public class LeyStoneBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction>       FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF   = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // The menhir narrows as it rises, so a block-wide box would hide that
    private static final VoxelShape LOWER = Shapes.or(Block.box(3, 0, 4, 13, 12, 12), Block.box(4, 12, 4.5, 13, 16, 11.5));
    private static final VoxelShape UPPER = Shapes.or(Block.box(4, 0, 4.5, 13, 5, 11.5), Block.box(5, 5, 5, 13, 12, 11), Block.box(6.5, 12, 5.5, 12.5, 15, 10.5));

    private static final Map<Direction, VoxelShape> LOWER_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = new EnumMap<>(Direction.class);

    static
    {
        for (var facing : Direction.Plane.HORIZONTAL)
        {
            LOWER_SHAPES.put(facing, rotate(LOWER, facing));
            UPPER_SHAPES.put(facing, rotate(UPPER, facing));
        }
    }

    public LeyStoneBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    private static VoxelShape rotate(VoxelShape north, Direction facing)
    {
        if (facing == Direction.NORTH)
            return north;
        var rotated = new VoxelShape[] { Shapes.empty() };
        north.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
        {
            var box = switch (facing)
            {
                case SOUTH -> Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                case EAST -> Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
                default -> Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
            };
            rotated[0] = Shapes.or(rotated[0], box);
        });
        return rotated[0];
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var pos = context.getClickedPos();
        var level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context))
            return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
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
     * Either half goes when the other does, as a door's do, and the lower half's loot table is the only one that drops the stone.
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

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state)))
            preventDropFromLowerHalf(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void preventDropFromLowerHalf(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER)
            return;
        var below = pos.below();
        var lower = level.getBlockState(below);
        if (lower.getBlock() instanceof LeyStoneBlock && lower.getValue(HALF) == DoubleBlockHalf.LOWER)
        {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.levelEvent(player, 2001, below, Block.getId(lower));
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new LeyStoneTile(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? createTickerHelper(type, LeyStoneContent.LEY_STONE_TILE, LeyStoneTile::clientTick) : null;
    }
}
