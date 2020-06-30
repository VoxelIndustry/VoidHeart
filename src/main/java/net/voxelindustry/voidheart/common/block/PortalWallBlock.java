package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.tile.PortalWallTile;

public class PortalWallBlock extends Block implements BlockEntityProvider
{
    public PortalWallBlock()
    {
        super(Settings.of(Material.STONE)
                .noCollision()
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.NORTH));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        PortalWallTile tile = (PortalWallTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

        if (world.isClient())
            return ActionResult.SUCCESS;

        if (player.getStackInHand(hand).getItem() == VoidHeartItems.VOID_HEART_PIECE)
        {
            if (tile.voidPieceInteract(hit.getSide(), player, player.getStackInHand(hand)))
            {
                return ActionResult.SUCCESS;
            }
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        super.onStateReplaced(state, world, pos, newState, moved);

        if (!state.isOf(newState.getBlock()))
        {
            PortalWallTile tile = (PortalWallTile) world.getBlockEntity(pos);
            tile.breakLink();
        }
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return getDefaultState().with(Properties.FACING, ctx.getSide());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation)
    {
        return state.with(Properties.FACING, rotation.rotate(state.get(Properties.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror)
    {
        return state.rotate(mirror.getRotation(state.get(Properties.FACING)));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.FACING);
    }

    @Override
    public BlockEntity createBlockEntity(BlockView world)
    {
        return new PortalWallTile();
    }
}
