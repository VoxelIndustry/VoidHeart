package net.voxelindustry.voidheart.common.content.pillar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;

public class VoidPillarBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(4, 4, 4, 12, 12, 12),
            createCuboidShape(3, 12, 3, 13, 16, 13)
    );

    public VoidPillarBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .lightLevel(state -> state.get(Properties.LIT) ? 11 : 0));

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
        VoidPillarTile pillar = (VoidPillarTile) world.getBlockEntity(pos);

        if (pillar == null)
            return ActionResult.SUCCESS;

        if (pillar.getStack().isEmpty())
        {
            pillar.setStack(ItemUtils.copyWithSize(player.getStackInHand(hand), 1));

            if (!player.isCreative())
                player.getStackInHand(hand).decrement(1);
        }
        else
        {
            if (player.getStackInHand(hand).isEmpty())
                player.setStackInHand(hand, pillar.getStack());
            else
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), pillar.getStack());
            pillar.setStack(ItemStack.EMPTY);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()))
        {
            VoidPillarTile tile = (VoidPillarTile) world.getBlockEntity(pos);
            if (tile != null)
            {
                tile.removeItself();
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
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
