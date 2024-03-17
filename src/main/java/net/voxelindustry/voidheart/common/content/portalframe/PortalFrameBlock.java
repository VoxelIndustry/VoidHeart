package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
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
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.*;

public class PortalFrameBlock extends Block implements BlockEntityProvider
{
    public PortalFrameBlock()
    {
        this(Settings.create()
                .mapColor(MapColor.BLACK)
                .instrument(Instrument.BASEDRUM)
                .strength(4F)
                .requiresTool()
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
                .with(NORTH, FrameConnection.NONE)
                .with(SOUTH, FrameConnection.NONE)
                .with(EAST, FrameConnection.NONE)
                .with(WEST, FrameConnection.NONE)
                .with(UP, FrameConnection.NONE)
                .with(DOWN, FrameConnection.NONE)
                .with(Properties.LIT, false)
        );
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state)
    {
        return new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder)
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

    private static FrameConnection getConnectionType(BlockState state)
    {
        if (state.isIn(VoidHeartTags.PORTAL_FRAME_TAG))
            return FrameConnection.FRAME;
        return FrameConnection.NONE;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom)
    {
        switch (direction)
        {
            case DOWN ->
            {
                var down = state.get(DOWN);

                if (down == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != down)
                    state = state.with(DOWN, newConnection);
            }
            case UP ->
            {
                var up = state.get(UP);

                if (up == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != up)
                    state = state.with(UP, newConnection);
            }
            case NORTH ->
            {
                var north = state.get(NORTH);

                if (north == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != north)
                    state = state.with(NORTH, newConnection);
            }
            case SOUTH ->
            {
                var south = state.get(SOUTH);

                if (south == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != south)
                    state = state.with(SOUTH, newConnection);
            }
            case WEST ->
            {
                var west = state.get(WEST);

                if (west == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != west)
                    state = state.with(WEST, newConnection);
            }
            case EAST ->
            {
                var east = state.get(EAST);

                if (east == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != east)
                    state = state.with(EAST, newConnection);
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
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, Properties.LIT);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalFrameTile(pos, state);
    }
}
