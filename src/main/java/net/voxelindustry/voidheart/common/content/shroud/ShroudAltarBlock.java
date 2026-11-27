package net.voxelindustry.voidheart.common.content.shroud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The altar before a shroud gate, its top cut with the outline of a Wraith Ink. An ink laid in the outline lifts the gate's seal, and taking it back lets
 * the seal settle again. It faces its gate.
 */
public class ShroudAltarBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Built to hold its gate open for good with no ink, as the way back out of a mausoleum. */
    public static final BooleanProperty KEPT = BooleanProperty.create("kept");
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 3, 16), box(2, 3, 2, 14, 12, 14), box(0, 12, 0, 16, 16, 16));

    public ShroudAltarBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(KEPT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, KEPT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof ShroudAltarTile altar) || altar.isKept())
            return InteractionResult.PASS;
        // Whatever is in hand, a hand on an inked altar takes the ink back, and one on a bare altar is told what it wants
        if (altar.holdsInk() || !stack.is(VoidHeartItems.WRAITH_INK))
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        altar.offer(stack.copyWithCount(1));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof ShroudAltarTile altar) || altar.isKept())
            return InteractionResult.PASS;
        if (!altar.holdsInk())
        {
            // Ink in the off hand gets its turn instead
            if (player.getOffhandItem().is(VoidHeartItems.WRAITH_INK))
                return InteractionResult.PASS;
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".shroud_altar.requires_ink", VoidHeartItems.WRAITH_INK.getName(VoidHeartItems.WRAITH_INK.getDefaultInstance())));
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        var taken = altar.withdraw();
        if (!player.getInventory().add(taken))
            Block.popResource(level, pos.above(), taken);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ShroudAltarTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide())
            return createTickerHelper(type, VoidHeartTiles.SHROUD_ALTAR, ShroudAltarTile::clientTick);
        return createTickerHelper(type, VoidHeartTiles.SHROUD_ALTAR, ShroudAltarTile::serverTick);
    }
}
