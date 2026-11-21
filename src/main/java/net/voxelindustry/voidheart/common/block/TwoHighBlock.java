package net.voxelindustry.voidheart.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A block standing two blocks high. The lower half holds the block entity and drops the item; the upper half is placed with it and falls with it, and
 * breaking the upper half breaks the lower one, so the item always drops from below. Either half outlines the whole block while the other stands.
 *
 * @param <P> the {@code part} property's values, the lower half first
 */
public abstract class TwoHighBlock<P extends Enum<P> & StringRepresentable> extends Block implements EntityBlock
{
    private final P lower;
    private final P upper;

    protected TwoHighBlock(Properties properties, P lower, P upper)
    {
        super(properties);
        this.lower = lower;
        this.upper = upper;

        registerDefaultState(getStateDefinition().any().setValue(partProperty(), lower));
    }

    /**
     * Read while the block's states are built, before the constructor has run, so it must return a static field.
     */
    protected abstract EnumProperty<P> partProperty();

    /**
     * What a half collides as, or outlines as alone.
     */
    protected abstract VoxelShape halfShape(BlockState state, boolean upper);

    /**
     * What a half outlines as while the other half stands with it, reaching into the other block.
     */
    protected abstract VoxelShape outline(BlockState state, boolean upper);

    protected abstract BlockEntity newLowerEntity(BlockPos pos, BlockState state);

    /**
     * Called on both sides once the upper half is placed.
     */
    protected void placed(Level level, BlockPos pos, BlockState state, ItemStack stack)
    {
    }

    protected List<ItemStack> lowerDrops(BlockState state, LootParams.Builder builder)
    {
        return super.getDrops(state, builder);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(partProperty());
    }

    public boolean isLower(BlockState state)
    {
        return state.is(this) && state.getValue(partProperty()) == lower;
    }

    public boolean isUpper(BlockState state)
    {
        return state.is(this) && state.getValue(partProperty()) == upper;
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

        level.setBlock(pos.above(), state.setValue(partProperty(), upper), Block.UPDATE_ALL);
        placed(level, pos, state, stack);
    }

    /**
     * The upper half falls with the lower one. A lower half left alone stays.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random)
    {
        if (isUpper(state) && direction == Direction.DOWN && !isLower(neighbourState))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * Breaking the upper half breaks the lower one, which drops the item.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        var below = pos.below();
        var lowerState = level.getBlockState(below);
        if (!level.isClientSide() && isUpper(state) && isLower(lowerState))
            level.destroyBlock(below, !player.preventsBlockDrops() && player.hasCorrectToolForDrops(lowerState), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public final List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        return isUpper(state) ? List.of() : lowerDrops(state, builder);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        if (isUpper(state))
            return isLower(level.getBlockState(pos.below())) ? outline(state, true) : halfShape(state, true);
        return isUpper(level.getBlockState(pos.above())) ? outline(state, false) : halfShape(state, false);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return halfShape(state, isUpper(state));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return isLower(state) ? newLowerEntity(pos, state) : null;
    }
}
