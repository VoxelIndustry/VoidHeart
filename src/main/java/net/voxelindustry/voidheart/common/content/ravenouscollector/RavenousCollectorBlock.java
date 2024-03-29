package net.voxelindustry.voidheart.common.content.ravenouscollector;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class RavenousCollectorBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(3 / 16D, 3 / 16D, 3 / 16D, 13 / 16D, 13 / 16D, 13 / 16D);

    public RavenousCollectorBlock(Settings settings)
    {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousCollectorTile(pos, state);
    }

    private static VoxelShape makeShape()
    {
        var shape = VoxelShapes.empty();
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.5, 0.25, 0.25, 0.75, 0.5, 0.5));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.5, 0.25, 0.5, 0.75, 0.5, 0.75));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.25, 0.25, 0.5, 0.5, 0.5, 0.75));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.25, 0.25, 0.25, 0.5, 0.5, 0.5));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.25, 0.5, 0.5, 0.5, 0.75, 0.75));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.5, 0.5, 0.5, 0.75, 0.75, 0.75));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.5, 0.5, 0.25, 0.75, 0.75, 0.5));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.25, 0.5, 0.25, 0.5, 0.75, 0.5));
        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875));

        return shape;
    }
}
