package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A voidstone wall with four niches in its front, each holding a rubrum cell. Like a cell socket, it discharges its
 * cells into the field around it, or charges them from the field while it gets a redstone signal.
 */
public class SocketWallBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SocketWallBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * A cell goes in the niche it was pressed into, or the first free one when that niche is taken or another face was
     * used.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(RubrumContent.RUBRUM_CELL))
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof SocketWallTile wall))
            return InteractionResult.PASS;

        var slot = hitSlot(state, pos, hit);
        if (slot < 0 || !wall.getCell(slot).isEmpty())
            slot = wall.firstEmpty();
        if (slot < 0)
            return InteractionResult.PASS;

        if (!level.isClientSide())
            wall.setCell(slot, stack.consumeAndReturn(1, player));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        var slot = hitSlot(state, pos, hit);
        if (slot < 0 || !(level.getBlockEntity(pos) instanceof SocketWallTile wall) || wall.getCell(slot).isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            player.getInventory().placeItemBackInInventory(wall.getCell(slot), Prediction.SERVER_ONLY);
            wall.setCell(slot, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * @return the niche under the hit, or -1 when a face other than the front was hit
     */
    private static int hitSlot(BlockState state, BlockPos pos, BlockHitResult hit)
    {
        var facing = state.getValue(FACING);
        if (hit.getDirection() != facing)
            return -1;
        return SocketWallGeometry.slotAt(facing, hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        return level.getBlockEntity(pos) instanceof SocketWallTile wall ? wall.comparatorSignal() : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SocketWallTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, RubrumContent.SOCKET_WALL_TILE, SocketWallTile::tick);
    }
}
