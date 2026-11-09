package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A plinth holding one rubrum cell upright. It discharges the cell into the field around it, or charges it from the
 * field while it gets a redstone signal.
 */
public class CellSocketBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            box(2, 0, 2, 14, 8, 14),
            box(5, 8, 5, 11, 15, 11));

    public CellSocketBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(RubrumContent.RUBRUM_CELL))
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof CellSocketTile socket) || !socket.getCell().isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide())
            socket.setCell(stack.consumeAndReturn(1, player));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof CellSocketTile socket) || socket.getCell().isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            player.getInventory().placeItemBackInInventory(socket.getCell(), Prediction.SERVER_ONLY);
            socket.setCell(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CellSocketTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, RubrumContent.CELL_SOCKET_TILE, CellSocketTile::tick);
    }
}
