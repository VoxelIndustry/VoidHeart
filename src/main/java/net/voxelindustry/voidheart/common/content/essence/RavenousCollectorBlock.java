package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.block.BlockWithEntity.checkType;

public class RavenousCollectorBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(3 / 16D, 3 / 16D, 3 / 16D, 13 / 16D, 13 / 16D, 13 / 16D);

    public RavenousCollectorBlock(Settings settings)
    {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        var stackInHand = player.getStackInHand(hand);
        if (stackInHand.isEmpty())
            return ActionResult.PASS;

        var collector = ((RavenousCollectorTile) world.getBlockEntity(pos));

        if (collector == null)
            return ActionResult.PASS;

        if (!collector.storage().isUnattuned())
            return ActionResult.PASS;

        var essenceType = EssenceRegistry.getTypeFromCatalyst(stackInHand);

        if (essenceType == null)
            return ActionResult.PASS;

        collector.storage().attune(essenceType);

        return ActionResult.SUCCESS;
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return checkType(type, VoidHeartTiles.RAVENOUS_COLLECTOR, RavenousCollectorTile::tick);
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
