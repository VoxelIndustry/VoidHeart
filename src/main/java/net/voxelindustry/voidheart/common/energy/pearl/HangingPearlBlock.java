package net.voxelindustry.voidheart.common.energy.pearl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A kindled seed hung under an upright chain, growing into a Collapsing Rubrum Pearl. It falls with its chain. Taken down unfinished it gives back
 * its seed, carrying the growth it had, so moving a pearl never wastes hours. A mature pearl is picked with an empty hand, or by breaking it.
 * <p>
 * The block draws nothing itself: the whole pearl is its block entity renderer, and the model only gives the particles of its breaking.
 */
public class HangingPearlBlock extends Block implements EntityBlock
{
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, PearlGrowth.Stage.MATURE.ordinal());

    /**
     * Hung from the chain's foot at the top of the block, down to the deepest a mature pearl reaches.
     */
    private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 16, 12);

    public HangingPearlBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(STAGE);
    }

    public static boolean isUprightChain(BlockState state)
    {
        return state.getBlock() instanceof ChainBlock && state.getValue(ChainBlock.AXIS) == Direction.Axis.Y;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return isUprightChain(level.getBlockState(pos.above()));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (direction == Direction.UP && !state.canSurvive(level, pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        var growth = stack.get(PearlContent.PEARL_GROWTH);
        if (growth != null && level.getBlockEntity(pos) instanceof HangingPearlTile pearl)
            pearl.restoreGrowth(growth);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof HangingPearlTile pearl) || !pearl.growth().isMature())
            return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel)
        {
            var picked = new ItemStack(PearlContent.COLLAPSING_RUBRUM_PEARL);
            if (!player.addItem(picked))
                player.spawnAtLocation(serverLevel, picked);
            // Gone without dropping again, since it is already in hand
            serverLevel.removeBlock(pos, false);
            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1F, 0.5F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof HangingPearlTile pearl)
            return List.of(pearl.dropped());
        return List.of(new ItemStack(PearlContent.KINDLED_FRACTAL_SEED));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HangingPearlTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, PearlContent.HANGING_PEARL_TILE, HangingPearlTile::tick);
    }
}
