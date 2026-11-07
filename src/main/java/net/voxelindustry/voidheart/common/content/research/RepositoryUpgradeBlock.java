package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
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
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A block that works for a Knowledge Repository nearby, claimed by it like its columns. Whatever moves is drawn by its renderer.
 */
public class RepositoryUpgradeBlock<T extends RepositoryBoundTile> extends Block implements EntityBlock
{
    private final VoxelShape                                 shape;
    private final Supplier<BlockEntityType<T>>               type;
    private final BiFunction<BlockPos, BlockState, T>        factory;
    private final @Nullable BlockEntityTicker<T>             clientTicker;

    public RepositoryUpgradeBlock(Properties properties, VoxelShape shape, Supplier<BlockEntityType<T>> type, BiFunction<BlockPos, BlockState, T> factory,
                                  @Nullable BlockEntityTicker<T> clientTicker)
    {
        super(properties);
        this.shape = shape;
        this.type = type;
        this.factory = factory;
        this.clientTicker = clientTicker;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return factory.apply(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level world, BlockState state, BlockEntityType<E> type)
    {
        if (type != this.type.get())
            return null;
        if (world.isClientSide())
            return (BlockEntityTicker<E>) clientTicker;
        return (BlockEntityTicker<E>) (BlockEntityTicker<T>) RepositoryBoundTile::tick;
    }
}
