package net.voxelindustry.voidheart.common.content.permeablebarrier;

import com.google.common.collect.Queues;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class PermeableBarrierBlock extends Block
{
    private static final VoxelShape UP    = createCuboidShape(0, 15, 0, 16, 16, 16);
    private static final VoxelShape DOWN  = createCuboidShape(0, 0, 0, 16, 1, 16);
    private static final VoxelShape NORTH = createCuboidShape(0, 0, 0, 16, 16, 1);
    private static final VoxelShape SOUTH = createCuboidShape(0, 0, 15, 16, 16, 16);
    private static final VoxelShape WEST  = createCuboidShape(0, 0, 0, 1, 16, 16);
    private static final VoxelShape EAST  = createCuboidShape(15, 0, 0, 16, 16, 16);

    public PermeableBarrierBlock()
    {
        super(Settings.create()
                .nonOpaque()
                .allowsSpawning(VoidHeartBlocks::never)
                .solidBlock((state, world, pos) -> state.get(Properties.LIT))
                .suffocates((state, world, pos) -> state.get(Properties.LIT))
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .luminance(state -> state.get(Properties.LIT) ? 11 : 0));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.UP)
                .with(Properties.LIT, true));
    }



    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return state.get(Properties.LIT) ? super.getRenderType(state) : BlockRenderType.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return getDefaultState().with(Properties.FACING, ctx.getPlayerLookDirection());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (!player.isSneaking())
        {
            switchLit(world, pos, state);
            return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    private void switchLit(World world, BlockPos pos, BlockState state)
    {
        ArrayDeque<BlockPos> frontier = Queues.newArrayDeque();
        Set<BlockPos> visited = new HashSet<>();
        frontier.add(pos);

        boolean newState = !state.get(Properties.LIT);

        while (!frontier.isEmpty())
        {
            BlockPos current = frontier.pop();
            world.setBlockState(current, world.getBlockState(current).with(Properties.LIT, newState));

            BlockPos.Mutable adjacent = current.mutableCopy();
            for (Direction facing : Direction.values())
            {
                adjacent.set(current);
                adjacent.move(facing);

                if (visited.contains(adjacent))
                    continue;

                BlockState adjacentState = world.getBlockState(adjacent);
                if (adjacentState.getBlock() == this)
                    frontier.add(adjacent.toImmutable());
            }
            visited.add(current);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        switch (state.get(Properties.FACING))
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
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        if(context instanceof EntityShapeContext entityShapeContext)
        {
/*            if(entityShapeContext.getEntity())*/
        }

        return state.get(Properties.LIT) ? state.getOutlineShape(world, pos) : VoxelShapes.empty();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return !state.get(Properties.LIT);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction)
    {
        return stateFrom.isOf(this) || !state.get(Properties.LIT);
    }

    @Override
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type)
    {
        return false;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.FACING, Properties.LIT);
    }
}
