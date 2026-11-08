package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

public class RavenousRelayBlock extends Block implements EntityBlock
{
    /**
     * Mounted on the face it was placed against, its core always at the block center where beams meet.
     */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, Block.box(5, 0, 5, 11, 11, 11),
            Direction.DOWN, Block.box(5, 5, 5, 11, 16, 11),
            Direction.NORTH, Block.box(5, 5, 5, 11, 11, 16),
            Direction.SOUTH, Block.box(5, 5, 0, 11, 11, 11),
            Direction.WEST, Block.box(5, 5, 5, 16, 11, 11),
            Direction.EAST, Block.box(0, 5, 5, 11, 11, 11));

    public RavenousRelayBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPES.get(state.getValue(BlockStateProperties.FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousRelayTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.RAVENOUS_RELAY, RavenousRelayTile::tick);
    }
}
