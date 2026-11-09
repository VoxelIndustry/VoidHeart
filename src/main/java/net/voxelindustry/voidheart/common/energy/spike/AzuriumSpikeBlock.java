package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A two-block azurium stake driven into the ground: the base, bound by its collar, holds the tile; the tip stands above it. Its renderer draws both,
 * leaning as they were driven in, so neither has a model of its own.
 * <p>
 * Using it with an empty hand bleeds a larger share of the flow, sneaking a smaller one.
 */
public class AzuriumSpikeBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape BASE_SHAPE    = Shapes.or(Block.box(5, 0, 5, 11, 3, 11), Block.box(6, 3, 6, 10, 16, 10));
    private static final VoxelShape TIP_SHAPE     = Block.box(6, 0, 6, 10, 15, 10);
    private static final VoxelShape BASE_OUTLINE  = Shapes.or(BASE_SHAPE, TIP_SHAPE.move(0, 1, 0));
    private static final VoxelShape TIP_OUTLINE   = Shapes.or(BASE_SHAPE.move(0, -1, 0), TIP_SHAPE);

    public AzuriumSpikeBlock(Properties properties)
    {
        super(properties);

        registerDefaultState(getStateDefinition().any().setValue(PART, Part.BASE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var above = context.getClickedPos().above();
        var level = context.getLevel();
        if (above.getY() > level.getMaxY() || !level.getBlockState(above).canBeReplaced(context))
            return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        level.setBlock(pos.above(), state.setValue(PART, Part.TIP), Block.UPDATE_ALL);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AzuriumSpikeTile spike)
            spike.driveIn(level.getRandom());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        var base = state.getValue(PART) == Part.TIP ? pos.below() : pos;
        if (!(level.getBlockEntity(base) instanceof AzuriumSpikeTile spike))
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            spike.cycleSetting(player.isShiftKeyDown() ? -1 : 1);
            player.sendOverlayMessage(Component.translatable(MODID + ".spike.setting", Math.round(LeySpikeMath.share(spike.setting()) * 100)));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The tip falls with the base.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (state.getValue(PART) == Part.TIP && direction == Direction.DOWN && !isBase(neighbourState))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Breaking the tip pulls the whole stake out, which drops from the base.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var below = pos.below();
        var baseState = level.getBlockState(below);
        if (!level.isClientSide() && state.getValue(PART) == Part.TIP && isBase(baseState))
            level.destroyBlock(below, !player.preventsBlockDrops() && player.hasCorrectToolForDrops(baseState), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        if (state.getValue(PART) == Part.TIP)
            return List.of();
        return super.getDrops(state, builder);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        if (state.getValue(PART) == Part.TIP)
            return isBase(level.getBlockState(pos.below())) ? TIP_OUTLINE : TIP_SHAPE;
        return isTip(level.getBlockState(pos.above())) ? BASE_OUTLINE : BASE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(PART) == Part.TIP ? TIP_SHAPE : BASE_SHAPE;
    }

    private boolean isBase(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == Part.BASE;
    }

    private boolean isTip(BlockState state)
    {
        return state.is(this) && state.getValue(PART) == Part.TIP;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(PART) == Part.BASE ? new AzuriumSpikeTile(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return state.getValue(PART) == Part.BASE ? createTickerHelper(type, LeySpikeContent.AZURIUM_SPIKE_TILE, AzuriumSpikeTile::tick) : null;
    }

    public enum Part implements StringRepresentable
    {
        BASE("base"),
        TIP("tip");

        private final String name;

        Part(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }
}
