package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.item.PortalCoreItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.*;

public class PortalFrameBlock extends Block implements EntityBlock
{
    public PortalFrameBlock(Properties settings)
    {
        super(settings);

        initDefaultState();
    }

    protected void initDefaultState()
    {
        registerDefaultState(getStateDefinition().any()
                .setValue(NORTH, FrameConnection.NONE)
                .setValue(SOUTH, FrameConnection.NONE)
                .setValue(EAST, FrameConnection.NONE)
                .setValue(WEST, FrameConnection.NONE)
                .setValue(UP, FrameConnection.NONE)
                .setValue(DOWN, FrameConnection.NONE)
                .setValue(BlockStateProperties.LIT, false)
        );
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData)
    {
        return new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        return singletonList(new ItemStack(VoidHeartBlocks.VOIDSTONE_BRICKS));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        var tile = world.getBlockEntity(pos);

        if (!(tile instanceof PortalFrameTile))
            return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() == VoidHeartItems.PORTAL_CORE)
        {
            if (world.isClientSide())
                return InteractionResult.SUCCESS;

            boolean isInPocket = PortalFormer.isInPocket(world, pos, player.getUUID());
            if (!PortalCoreItem.checkPearlUseHereAndWarn(stack, isInPocket, player))
                return InteractionResult.PASS;

            boolean alreadyHasFirstPoint = PortalCoreItem.doesPearlHasFirstPosition(stack);

            if (PortalLinker.voidIronEyeInteract((PortalFrameTile) tile, tile.getLevel(), tile.getBlockPos(), hit.getDirection(), player, stack))
            {
                PortalCoreItem.sendSuccessMessage(player, stack, alreadyHasFirstPoint);
                return InteractionResult.SUCCESS;
            }
        }

        return super.useItemOn(heldStack, state, world, pos, player, hand, hit);
    }

    private static FrameConnection getConnectionType(BlockState state)
    {
        if (state.is(VoidHeartTags.PORTAL_FRAME_TAG))
            return FrameConnection.FRAME;
        return FrameConnection.NONE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos posFrom, BlockState newState, RandomSource random)
    {
        switch (direction)
        {
            case DOWN ->
            {
                var down = state.getValue(DOWN);

                if (down == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != down)
                    state = state.setValue(DOWN, newConnection);
            }
            case UP ->
            {
                var up = state.getValue(UP);

                if (up == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != up)
                    state = state.setValue(UP, newConnection);
            }
            case NORTH ->
            {
                var north = state.getValue(NORTH);

                if (north == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != north)
                    state = state.setValue(NORTH, newConnection);
            }
            case SOUTH ->
            {
                var south = state.getValue(SOUTH);

                if (south == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != south)
                    state = state.setValue(SOUTH, newConnection);
            }
            case WEST ->
            {
                var west = state.getValue(WEST);

                if (west == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != west)
                    state = state.setValue(WEST, newConnection);
            }
            case EAST ->
            {
                var east = state.getValue(EAST);

                if (east == FrameConnection.INTERIOR)
                    return state;

                var newConnection = getConnectionType(newState);
                if (newConnection != east)
                    state = state.setValue(EAST, newConnection);
            }
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, BlockStateProperties.LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalFrameTile(pos, state);
    }
}
