package net.voxelindustry.voidheart.common.content.athanor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The core of the athanor, a seething steel furnace whose mouth faces whoever placed it. The base of a brew is set inside it.
 */
public class AthanorBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 2, 15),
            box(2, 2, 2, 14, 14, 14),
            box(1, 14, 1, 15, 16, 15));

    public AthanorBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (isAthanorPart(heldStack))
            return InteractionResult.PASS;
        if (!(world.getBlockEntity(pos) instanceof AthanorTile athanor))
            return InteractionResult.SUCCESS;
        if (world.isClientSide())
            return InteractionResult.SUCCESS;

        var serverPlayer = (ServerPlayer) player;
        if (athanor.getStack().isEmpty())
        {
            var stackInHand = player.getItemInHand(hand);
            if (stackInHand.isEmpty())
                return InteractionResult.SUCCESS;

            athanor.setStack(serverPlayer, stackInHand.copyWithCount(1));
            if (!player.isCreative())
                stackInHand.shrink(1);

            if (!world.getBlockState(pos.above()).is(VoidHeartBlocks.ALEMBIC))
                player.sendOverlayMessage(Component.translatable(MODID + ".athanor.missing_alembic"));
        }
        else
        {
            player.getInventory().placeItemBackInInventory(athanor.getStack(), Prediction.SERVER_ONLY);
            athanor.setStack(serverPlayer, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Parts of the athanor itself are placed against it rather than set inside as a base.
     */
    private static boolean isAthanorPart(ItemStack stack)
    {
        return stack.is(VoidHeartBlocks.ALEMBIC.asItem())
                || stack.is(VoidHeartBlocks.CRUCIBLE.asItem())
                || stack.is(VoidHeartBlocks.ATHANOR.asItem());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AthanorTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.ATHANOR, AthanorTile::tick);
    }
}
