package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.common.block.StateProperties.*;

public class PortalFrameBlock extends Block implements BlockEntityProvider
{
    public PortalFrameBlock()
    {
        this(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));
    }

    public PortalFrameBlock(Settings settings)
    {
        super(settings);

        initDefaultState();
    }

    protected void initDefaultState()
    {
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(UP, false)
                .with(DOWN, false));
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state)
    {
        return new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, Builder builder)
    {
        return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == VoidHeartItems.VOID_PEARL || stack.getItem() == VoidHeartItems.LOCAL_PEARL)
        {
            if (world.isClient())
                return ActionResult.SUCCESS;

            boolean isInPocket = PortalFormer.isInPocket(world, pos, player.getUuid());
            if (!VoidPearlItem.checkPearlUseHereAndWarn(stack, isInPocket, player))
                return ActionResult.PASS;

            boolean alreadyHasFirstPoint = VoidPearlItem.doesPearlHasFirstPosition(stack);

            if (PortalLinker.voidPearlInteract(tile, tile.getWorld(), tile.getPos(), hit.getSide(), player, stack))
            {
                VoidPearlItem.sendSuccessMessage(player, stack, alreadyHasFirstPoint);
                return ActionResult.SUCCESS;
            }
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom)
    {
        switch (direction)
        {
            case DOWN -> {
                Boolean down = state.get(DOWN);
                if (down && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(DOWN, false);
                else if (!down && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(DOWN, true);
            }
            case UP -> {
                Boolean up = state.get(UP);
                if (up && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(UP, false);
                else if (!up && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(UP, true);
            }
            case NORTH -> {
                Boolean north = state.get(NORTH);
                if (north && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(NORTH, false);
                else if (!north && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(NORTH, true);
            }
            case SOUTH -> {
                Boolean south = state.get(SOUTH);
                if (south && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(SOUTH, false);
                else if (!south && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(SOUTH, true);
            }
            case WEST -> {
                Boolean west = state.get(WEST);
                if (west && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(WEST, false);
                else if (!west && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(WEST, true);
            }
            case EAST -> {
                Boolean east = state.get(EAST);
                if (east && !VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(EAST, false);
                else if (!east && VoidHeartBlocks.PORTAL_INTERIOR_TAG.contains(newState.getBlock()))
                    state = state.with(EAST, true);
            }
        }
        return state;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()))
        {
            PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);
            if (tile != null)
                tile.breakTile(BlockPos.ORIGIN);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalFrameTile(pos, state);
    }
}
