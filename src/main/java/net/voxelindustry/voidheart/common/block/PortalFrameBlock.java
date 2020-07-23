package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.tile.PortalFrameTile;

import java.util.List;

import static java.util.Collections.singletonList;

public class PortalFrameBlock extends Block implements BlockEntityProvider
{
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty EAST  = BooleanProperty.of("east");
    public static final BooleanProperty WEST  = BooleanProperty.of("west");
    public static final BooleanProperty UP    = BooleanProperty.of("up");
    public static final BooleanProperty DOWN  = BooleanProperty.of("down");

    public PortalFrameBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));

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
        PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);
        if (player.isSneaking())
        {
            if (!world.isClient())
            {
                player.sendMessage(new LiteralText("POINTS: " + tile.getPortalPoints()), false);
            }
            return ActionResult.PASS;
        }


        if (tile == null)
            return ActionResult.PASS;

        if (world.isClient())
            return ActionResult.SUCCESS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == VoidHeartItems.VOID_HEART_PIECE)
        {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.containsUuid("player"))
                return ActionResult.PASS;

            if (tile.voidPieceInteract(hit.getSide(), player, player.getStackInHand(hand)))
            {
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
            case DOWN:
                Boolean down = state.get(DOWN);
                if (down && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(DOWN, false);
                else if (!down && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(DOWN, true);
                break;
            case UP:
                Boolean up = state.get(UP);
                if (up && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(UP, false);
                else if (!up && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(UP, true);
                break;
            case NORTH:
                Boolean north = state.get(NORTH);
                if (north && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(NORTH, false);
                else if (!north && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(NORTH, true);
                break;
            case SOUTH:
                Boolean south = state.get(SOUTH);
                if (south && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(SOUTH, false);
                else if (!south && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(SOUTH, true);
                break;
            case WEST:
                Boolean west = state.get(WEST);
                if (west && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(WEST, false);
                else if (!west && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(WEST, true);
                break;
            case EAST:
                Boolean east = state.get(EAST);
                if (east && newState.getBlock() != VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(EAST, false);
                else if (!east && newState.getBlock() == VoidHeartBlocks.PORTAL_INTERIOR)
                    state = state.with(EAST, true);
                break;
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
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockEntity createBlockEntity(BlockView world)
    {
        return new PortalFrameTile();
    }

    @Override
    public boolean is(Block block)
    {
        return block == VoidHeartBlocks.PORTAL_FRAME || block == VoidHeartBlocks.PORTAL_FRAME_CORE;
    }
}
