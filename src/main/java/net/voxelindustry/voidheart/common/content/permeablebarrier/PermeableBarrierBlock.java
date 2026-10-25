package net.voxelindustry.voidheart.common.content.permeablebarrier;

import com.google.common.collect.Queues;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class PermeableBarrierBlock extends Block
{
    private static final VoxelShape UP    = box(0, 15, 0, 16, 16, 16);
    private static final VoxelShape DOWN  = box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape NORTH = box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape SOUTH = box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape WEST  = box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape EAST  = box(15, 0, 0, 16, 16, 16);

    public PermeableBarrierBlock()
    {
        super(Properties.of()
                .noOcclusion()
                .isValidSpawn(VoidHeartBlocks::never)
                .isRedstoneConductor((state, world, pos) -> state.getValue(BlockStateProperties.LIT))
                .isSuffocating((state, world, pos) -> state.getValue(BlockStateProperties.LIT))
                .strength(3F)
                .sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 11 : 0));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(BlockStateProperties.LIT, true));
    }



    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return state.getValue(BlockStateProperties.LIT) ? super.getRenderShape(state) : RenderShape.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return defaultBlockState().setValue(BlockStateProperties.FACING, ctx.getNearestLookingDirection());
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!player.isShiftKeyDown())
        {
            switchLit(world, pos, state);
            return InteractionResult.SUCCESS;
        }

        return super.use(state, world, pos, player, hand, hit);
    }

    private void switchLit(Level world, BlockPos pos, BlockState state)
    {
        ArrayDeque<BlockPos> frontier = Queues.newArrayDeque();
        Set<BlockPos> visited = new HashSet<>();
        frontier.add(pos);

        boolean newState = !state.getValue(BlockStateProperties.LIT);

        while (!frontier.isEmpty())
        {
            BlockPos current = frontier.pop();
            world.setBlockAndUpdate(current, world.getBlockState(current).setValue(BlockStateProperties.LIT, newState));

            BlockPos.MutableBlockPos adjacent = current.mutable();
            for (Direction facing : Direction.values())
            {
                adjacent.set(current);
                adjacent.move(facing);

                if (visited.contains(adjacent))
                    continue;

                BlockState adjacentState = world.getBlockState(adjacent);
                if (adjacentState.getBlock() == this)
                    frontier.add(adjacent.immutable());
            }
            visited.add(current);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        switch (state.getValue(BlockStateProperties.FACING))
        {
            case DOWN:
                return DOWN;
            case NORTH:
                return NORTH;
            case SOUTH:
                return SOUTH;
            case WEST:
                return WEST;
            case EAST:
                return EAST;
            case UP:
            default:
                return UP;
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        if(context instanceof EntityCollisionContext entityShapeContext)
        {
/*            if(entityShapeContext.getEntity())*/
        }

        return state.getValue(BlockStateProperties.LIT) ? state.getShape(world, pos) : Shapes.empty();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos)
    {
        return !state.getValue(BlockStateProperties.LIT);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean skipRendering(BlockState state, BlockState stateFrom, Direction direction)
    {
        return stateFrom.is(this) || !state.getValue(BlockStateProperties.LIT);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING, BlockStateProperties.LIT);
    }
}
