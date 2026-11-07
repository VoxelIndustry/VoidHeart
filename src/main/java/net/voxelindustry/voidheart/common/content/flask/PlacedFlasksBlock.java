package net.voxelindustry.voidheart.common.content.flask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

/**
 * Up to four flasks set down on a block. They are placed by sneaking with one in hand, since a plain use drinks or applies it, and a plain use on
 * them takes the last one back. The block has no item of its own and drops the flasks it holds.
 */
public class PlacedFlasksBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 9, 14);

    public PlacedFlasksBlock(Properties properties)
    {
        super(properties);
    }

    /**
     * Sets a held flask down where the player aims, beside the flasks already there when there's room, or on the face clicked.
     */
    public static InteractionResult place(Player player, Level world, InteractionHand hand, BlockHitResult hit)
    {
        var stack = player.getItemInHand(hand);
        if (player.isSpectator() || !player.isSecondaryUseActive() || !stack.is(VoidHeartTags.PLACEABLE_FLASKS))
            return InteractionResult.PASS;

        var pos = hit.getBlockPos();
        if (!(world.getBlockState(pos).is(VoidHeartBlocks.PLACED_FLASKS) && world.getBlockEntity(pos) instanceof PlacedFlasksTile tile && !tile.isFull()))
        {
            pos = pos.relative(hit.getDirection());
            var target = world.getBlockState(pos);
            boolean joinsFlasks = target.is(VoidHeartBlocks.PLACED_FLASKS) && world.getBlockEntity(pos) instanceof PlacedFlasksTile there && !there.isFull();
            if (!joinsFlasks && !(target.canBeReplaced() && target.getFluidState().isEmpty() && canStandOn(world, pos)
                                  && world.isUnobstructed(VoidHeartBlocks.PLACED_FLASKS.defaultBlockState(), pos, CollisionContext.placementContext(player))))
                return InteractionResult.PASS;
        }
        if (!world.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack))
            return InteractionResult.PASS;

        if (!world.isClientSide())
        {
            if (!world.getBlockState(pos).is(VoidHeartBlocks.PLACED_FLASKS))
                world.setBlockAndUpdate(pos, VoidHeartBlocks.PLACED_FLASKS.defaultBlockState());
            if (world.getBlockEntity(pos) instanceof PlacedFlasksTile tile && tile.add(stack))
            {
                stack.consume(1, player);
                world.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean canStandOn(LevelReader world, BlockPos pos)
    {
        return canSupportCenter(world, pos.below(), Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(world.getBlockEntity(pos) instanceof PlacedFlasksTile tile))
            return InteractionResult.PASS;
        if (world.isClientSide())
            return InteractionResult.SUCCESS;

        var flask = tile.removeLast();
        if (!flask.isEmpty())
        {
            player.getInventory().placeItemBackInInventory(flask, Prediction.SERVER_ONLY);
            world.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.8F, 1.3F);
        }
        if (tile.getFlasks().isEmpty())
            world.removeBlock(pos, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos)
    {
        return canStandOn(world, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random)
    {
        if (direction == Direction.DOWN && !canSurvive(state, world, pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, world, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData)
    {
        return world.getBlockEntity(pos) instanceof PlacedFlasksTile tile ? tile.peekLast().copy() : ItemStack.EMPTY;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PlacedFlasksTile(pos, state);
    }
}
