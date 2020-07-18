package net.voxelindustry.voidheart.common.block;

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
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;

public class VoidAltarBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 0, 0, 16, 6, 16),
            createCuboidShape(1, 6, 1, 15, 10, 15),
            createCuboidShape(0, 10, 0, 16, 14, 16),
            createCuboidShape(4, 14, 4, 12, 16, 12)
    );

    public VoidAltarBlock()
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
        VoidAltarTile altar = (VoidAltarTile) world.getBlockEntity(pos);

        if (altar == null)
            return ActionResult.SUCCESS;

        if (altar.getStack().isEmpty())
        {
            altar.setStack(player, ItemUtils.copyWithSize(player.getStackInHand(hand), 1));

            if (!player.isCreative())
                player.getStackInHand(hand).decrement(1);
        }
        else
        {
            if (player.getStackInHand(hand).isEmpty())
                player.setStackInHand(hand, altar.getStack());
            else
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), altar.getStack());
            altar.setStack(player, ItemStack.EMPTY);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()))
        {
            VoidAltarTile tile = (VoidAltarTile) world.getBlockEntity(pos);
            if (tile != null)
            {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack());
                tile.dropAteItems();
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
        return new VoidAltarTile();
    }
}
