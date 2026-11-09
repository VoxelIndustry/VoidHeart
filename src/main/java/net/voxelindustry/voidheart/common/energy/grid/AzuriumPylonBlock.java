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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A voidstone stem whose gold claws hold an azurium bundle placed on top. The bundle sets the pylon's field and cap; without one it stands dark.
 * <p>
 * Azurium bundles only drop themselves to silk touch, so the claws hand theirs back whole: when the pylon breaks, and when the bundle is mined off them.
 */
public class AzuriumPylonBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 3, 13),
            Block.box(5, 3, 5, 11, 14, 11),
            Block.box(4, 14, 4, 12, 16, 12));

    /**
     * Only the top is a full face, so a bundle can stand on it but not hang from the sides.
     */
    private static final VoxelShape SUPPORT = Block.box(0, 15, 0, 16, 16, 16);

    public AzuriumPylonBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return SUPPORT;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random)
    {
        if (direction == Direction.UP && level instanceof ServerLevel serverLevel)
            VoidGrid.markDirty(serverLevel);
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        VoidGrid.markDirty(level);
        releaseBundle(level, pos.above());
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * Takes the bundle out of the claws as an item, rather than letting it break into a shard.
     *
     * @return whether there was a bundle to release
     */
    public static boolean releaseBundle(ServerLevel level, BlockPos bundlePos)
    {
        var bundle = level.getBlockState(bundlePos);
        if (PylonBundle.of(bundle) == PylonBundle.NONE)
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
