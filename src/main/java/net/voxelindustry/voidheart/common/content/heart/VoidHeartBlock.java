package net.voxelindustry.voidheart.common.content.heart;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class VoidHeartBlock extends Block implements BlockEntityProvider
{
    private final VoxelShape SHAPE = Block.createCuboidShape(2, 2, 2, 14, 14, 14);

    public VoidHeartBlock()
    {
        super(Settings.of(Material.SOLID_ORGANIC)
                .noCollision()
                .strength(-1.0F, 3600000.0F)
                .dropsNothing()
                .allowsSpawning(((state, world, pos, type) -> false))
                .lightLevel(unused -> 11));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (!world.isClient())
            ((VoidHeartTile) world.getBlockEntity(pos)).playerHit(player);

        return ActionResult.SUCCESS;
    }

    @Override
    public BlockEntity createBlockEntity(BlockView world)
    {
        return new VoidHeartTile();
    }
}
