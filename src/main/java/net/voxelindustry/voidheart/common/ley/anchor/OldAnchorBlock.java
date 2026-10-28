package net.voxelindustry.voidheart.common.ley.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two block tall standing stone generated on well connected ley nodes. Awakened by a click when dormant,
 * otherwise opens the travel map to the anchors linked to it.
 */
public class OldAnchorBlock extends Block
{
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape LOWER_SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(2, 3, 2, 14, 5, 14), Block.box(4, 5, 4, 12, 16, 12));
    private static final VoxelShape UPPER_SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 8, 12), Block.box(3, 8, 3, 13, 10, 13), Block.box(6, 10, 6, 10, 16, 10));

    public OldAnchorBlock(Properties properties)
    {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? 10 : 2));
        registerDefaultState(getStateDefinition().any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(HALF, ACTIVE);
    }

    /**
     * Old Anchors are only ever generated, never placed by hand.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random)
    {
        var half = state.getValue(HALF);
        boolean towardOtherHalf = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOtherHalf && !(neighborState.is(this) && neighborState.getValue(HALF) != half))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        var lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        OldAnchors.get((ServerLevel) level).ifPresent(anchors -> anchors.use((ServerPlayer) player, lowerPos));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(ACTIVE) || state.getValue(HALF) != DoubleBlockHalf.UPPER || random.nextInt(3) != 0)
            return;

        level.addParticle(ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 1.5, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 1.5);
    }
}
