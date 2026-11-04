package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A fleshy growth on any face of a block, releasing barnacles for as long as a player stays near and it stands. It drops nothing.
 */
public class BarnacleNestBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.UP, box(2, 0, 2, 14, 7, 14),
            Direction.DOWN, box(2, 9, 2, 14, 16, 14),
            Direction.NORTH, box(2, 2, 9, 14, 14, 16),
            Direction.SOUTH, box(2, 2, 0, 14, 14, 7),
            Direction.WEST, box(9, 2, 2, 16, 14, 14),
            Direction.EAST, box(0, 2, 2, 7, 14, 14)));

    public BarnacleNestBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
    }

    public static boolean canAttach(LevelReader level, BlockPos pos, Direction facing)
    {
        var support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /**
     * Only sees loaded chunks, which is enough to keep one player's surroundings from filling up.
     */
    public static int countNestsAround(ServerLevel level, BlockPos center, int range)
    {
        int count = 0;
        int rangeSqr = range * range;
        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - range); chunkX <= SectionPos.blockToSectionCoord(center.getX() + range); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - range); chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + range); chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;

                for (var tile : chunk.getBlockEntities().values())
                {
                    if (tile instanceof BarnacleNestTile && tile.getBlockPos().distSqr(center) <= rangeSqr)
                        count++;
                }
            }
        }
        return count;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return canAttach(level, pos, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     LevelReader level,
                                     ScheduledTickAccess ticks,
                                     BlockPos pos,
                                     Direction direction,
                                     BlockPos neighbourPos,
                                     BlockState neighbour,
                                     RandomSource random)
    {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
               ? Blocks.AIR.defaultBlockState()
               : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BarnacleNestTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide() || type != VoidHeartTiles.BARNACLE_NEST)
            return null;
        return (tickLevel, pos, tickState, tile) -> ((BarnacleNestTile) tile).tickServer((ServerLevel) tickLevel);
    }
}
