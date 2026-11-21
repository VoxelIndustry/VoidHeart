package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * An upright stone slab on a small base, its carved ring facing whoever set it, and the one behind them.
 */
public class LeyWardBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape ALONG_X = Shapes.or(Block.box(2, 0, 4.5, 14, 2, 11.5), Block.box(3, 2, 6.5, 13, 16, 9.5));
    private static final VoxelShape ALONG_Z = Shapes.or(Block.box(4.5, 0, 2, 11.5, 2, 14), Block.box(6.5, 2, 3, 9.5, 16, 13));

    public LeyWardBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_X : ALONG_Z;
    }

    /**
     * An imprinted Scoured memoriam bundle sets into the ward, one at a time, and the ward then keeps the creature it remembers out of its reach.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.get(VoidHeartDataComponents.CRYSTAL_IMPRINT) == null || CrystalBundles.habitOf(stack) != CrystalHabit.SCOURED)
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LeyWardTile ward))
            return InteractionResult.SUCCESS;
        if (!ward.memory().isEmpty())
        {
            player.sendOverlayMessage(Component.translatable("voidheart.ley_ward.memory.full"));
            return InteractionResult.FAIL;
        }
        ward.setMemory(stack);
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1F, 0.7F);
        return InteractionResult.SUCCESS;
    }

    /**
     * An empty hand takes the memory back out.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof LeyWardTile ward) || ward.memory().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;
        var memory = ward.memory().copy();
        ward.setMemory(ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(memory, Prediction.SERVER_ONLY);
        level.playSound(null, pos, SoundEvents.MEDIUM_AMETHYST_BUD_BREAK, SoundSource.BLOCKS, 1F, 1F);
        return InteractionResult.SUCCESS;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new LeyWardTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, LeyStoneContent.LEY_WARD_TILE, level.isClientSide() ? LeyWardTile::clientTick : LeyWardTile::serverTick);
    }
}
