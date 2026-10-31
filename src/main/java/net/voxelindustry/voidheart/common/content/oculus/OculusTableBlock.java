package net.voxelindustry.voidheart.common.content.oculus;

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
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

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
