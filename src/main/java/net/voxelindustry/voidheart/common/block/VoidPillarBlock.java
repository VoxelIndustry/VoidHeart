package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.tile.VoidPillarTile;

public class VoidPillarBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(4, 4, 4, 11, 12, 11),
            createCuboidShape(3, 12, 3, 12, 16, 12)
    );

    public VoidPillarBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.LIT, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        world.setBlockState(pos, state.with(Properties.LIT, !state.get(Properties.LIT)));

        return ActionResult.SUCCESS;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.LIT);
    }

    @Override
    public BlockEntity createBlockEntity(BlockView world)
    {
        return new VoidPillarTile();
    }
}
