package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A voidstone stem whose gold claws hold an azurium bundle placed on top. The bundle sets the pylon's field and cap; without one it stands dark.
 * Placed against a ceiling it hangs upside down, holding its bundle below.
 * <p>
 * Azurium bundles only drop themselves to silk touch, so the claws hand theirs back whole: when the pylon breaks, and when the bundle is mined off them.
 */
public class AzuriumPylonBlock extends Block implements EntityBlock
{
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 3, 13),
            Block.box(5, 3, 5, 11, 14, 11),
            Block.box(4, 14, 4, 12, 16, 12));

    private static final VoxelShape HANGING_SHAPE = Shapes.or(
            Block.box(3, 13, 3, 13, 16, 13),
            Block.box(5, 2, 5, 11, 13, 11),
            Block.box(4, 0, 4, 12, 2, 12));

    /**
     * Only the claws' face is full, so a bundle can stand on it, or hang from it, but not from the sides.
     */
    private static final VoxelShape SUPPORT         = Block.box(0, 15, 0, 16, 16, 16);
    private static final VoxelShape HANGING_SUPPORT = Block.box(0, 0, 0, 16, 1, 16);

    public AzuriumPylonBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(HANGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(HANGING, context.getClickedFace() == Direction.DOWN);
    }

    /**
     * The side its claws face, where its bundle sits: up, or down when it hangs.
     */
    public static Direction claws(BlockState state)
    {
        return state.hasProperty(HANGING) && state.getValue(HANGING) ? Direction.DOWN : Direction.UP;
    }

    public static BlockPos bundlePos(BlockState state, BlockPos pylon)
    {
        return pylon.relative(claws(state));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(HANGING) ? HANGING_SHAPE : SHAPE;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return state.getValue(HANGING) ? HANGING_SUPPORT : SUPPORT;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random)
    {
        if (direction == claws(state) && level instanceof ServerLevel serverLevel)
            VoidGrid.markDirty(serverLevel);
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        VoidGrid.markDirty(level);
        releaseBundle(level, bundlePos(state, pos), claws(state));
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * Takes the bundle out of the claws as an item, rather than letting it break into a shard.
     *
     * @return whether there was a bundle to release
     */
    public static boolean releaseBundle(ServerLevel level, BlockPos bundlePos, Direction facing)
    {
        var bundle = level.getBlockState(bundlePos);
        if (PylonBundle.of(bundle, facing) == PylonBundle.NONE)
            return false;
        level.destroyBlock(bundlePos, false);
        Block.popResource(level, bundlePos, new ItemStack(bundle.getBlock()));
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AzuriumPylonTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? createTickerHelper(type, VoidGridContent.AZURIUM_PYLON_TILE, AzuriumPylonTile::tickClient) : null;
    }
}
