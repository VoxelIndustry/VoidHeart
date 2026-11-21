package net.voxelindustry.voidheart.common.content.oculus;

import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Projects the land around it as a hologram. Its controls float above the table and are handled client side.
 */
public class OculusTableBlock extends Block implements EntityBlock
{
    /**
     * Height of the table top, kept low so the land projected above it stays at a comfortable eye level.
     */
    public static final float HEIGHT = 8 / 16F;

    /**
     * Whether a table block stands on that side. The raised border only runs along the sides where none does,
     * framing the whole table rather than each of its blocks.
     */
    private static final Map<Direction, BooleanProperty> JOINED = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.EAST, BlockStateProperties.EAST);

    private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 8, 16);
    private static final Map<Direction, VoxelShape> BORDERS = Map.of(
            Direction.NORTH, Block.box(0, 8, 0, 16, 9, 1),
            Direction.SOUTH, Block.box(0, 8, 15, 16, 9, 16),
            Direction.WEST, Block.box(0, 8, 0, 1, 9, 16),
            Direction.EAST, Block.box(15, 8, 0, 16, 9, 16));

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public OculusTableBlock(Properties properties)
    {
        super(properties);

        var state = getStateDefinition().any();
        for (var joined : JOINED.values())
            state = state.setValue(joined, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        JOINED.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var state = defaultBlockState();
        for (var entry : JOINED.entrySet())
            state = state.setValue(entry.getValue(), context.getLevel().getBlockState(context.getClickedPos().relative(entry.getKey())).is(this));
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random)
    {
        var joined = JOINED.get(direction);
        return joined == null ? state : state.setValue(joined, neighborState.is(this));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shapes.computeIfAbsent(state, key ->
        {
            var shape = BASE;
            for (var entry : JOINED.entrySet())
            {
                if (!key.getValue(entry.getValue()))
                    shape = Shapes.or(shape, BORDERS.get(entry.getKey()));
            }
            return shape;
        });
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state)
    {
        return true;
    }

    /**
     * A Deep memoriam that recorded a place is set on the table, which shows that place instead of its own land until it is taken back.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(WorksContent.MEMORIAM_LENS))
            return setLens(stack, level, pos, player);
        var memory = OculusMemory.of(stack);
        if (memory == null)
        {
            boolean deepMemoriam = CrystalBundles.bundleOf(stack) instanceof Block bundle && CrystalBundles.kindOfBundle(bundle) == CrystalKind.MEMORIAM
                                   && CrystalBundles.habitOf(stack) == CrystalHabit.DEEP;
            if (!deepMemoriam)
                return InteractionResult.PASS;
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".oculus_table.memory.none"));
            return InteractionResult.FAIL;
        }
        if (level.isClientSide())
            return InteractionResult.SUCCESS;
        var tile = OculusTableStructure.getTile(level, pos);
        if (tile == null)
            return InteractionResult.PASS;
        if (tile.getView().memory().isPresent())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".oculus_table.memory.full"));
            return InteractionResult.FAIL;
        }
        OculusTableStructure.remember(level, pos, Optional.of(memory));
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 0.7F);
        return InteractionResult.SUCCESS;
    }

    /**
     * A Memoriam Lens set in the table turns it to planning works, once no memory is set in it.
     */
    private static InteractionResult setLens(ItemStack stack, Level level, BlockPos pos, Player player)
    {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;
        var tile = OculusTableStructure.getTile(level, pos);
        if (tile == null)
            return InteractionResult.PASS;
        if (tile.getView().reckoning() || tile.getView().memory().isPresent())
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".oculus_table.lens.full"));
            return InteractionResult.FAIL;
        }
        OculusTableStructure.updateView(level, pos, view -> view.withReckoning(true));
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /**
     * With a lens set in, an empty hand opens the Reckoning on the user's own hoard, and sneaking takes the lens back. Otherwise it takes the memory
     * back, and the table shows its own land again.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        var tile = OculusTableStructure.getTile(level, pos);
        if (tile != null && tile.getView().reckoning())
        {
            if (level.isClientSide())
                return InteractionResult.SUCCESS;
            if (player.isShiftKeyDown())
            {
                OculusTableStructure.updateView(level, pos, view -> view.withReckoning(false));
                player.getInventory().placeItemBackInInventory(new ItemStack(WorksContent.MEMORIAM_LENS), Prediction.SERVER_ONLY);
                level.playSound(null, pos, SoundEvents.MEDIUM_AMETHYST_BUD_BREAK, SoundSource.BLOCKS, 1, 1.2F);
            }
            else if (player instanceof ServerPlayer serverPlayer)
                HoardMenu.open(serverPlayer, serverPlayer.getUUID(), pos, true);
            return InteractionResult.SUCCESS;
        }
        if (tile == null || tile.getView().memory().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide())
            return InteractionResult.SUCCESS;
        var memory = tile.getView().memory().get();
        OculusTableStructure.remember(level, pos, Optional.empty());
        player.getInventory().placeItemBackInInventory(memory.toStack(), Prediction.SERVER_ONLY);
        level.playSound(null, pos, SoundEvents.MEDIUM_AMETHYST_BUD_BREAK, SoundSource.BLOCKS, 1, 0.9F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(level, pos, state, placer, itemStack);

        if (level.isClientSide())
            return;
        var tile = OculusTableStructure.getTile(level, pos);
        if (tile != null && placer instanceof Player player)
            tile.setOwner(Optional.of(player.getUUID()));
        OculusTableStructure.onBlockAdded(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)
    {
        if (level.isClientSide())
            return;
        var tile = OculusTableStructure.getTile(level, pos);
        if (tile != null && tile.getView().asleep() != level.hasNeighborSignal(pos))
            OculusTableStructure.updatePower(level, pos);
    }

    /**
     * The removed block may have been the one keeping what remains of the table asleep.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        for (var direction : Direction.Plane.HORIZONTAL)
            OculusTableStructure.updatePower(level, pos.relative(direction));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new OculusTableTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.OCULUS_TABLE, OculusTableTile::serverTick);
    }
}
