package net.voxelindustry.voidheart.common.block;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.tile.PocketPortalTile;

public class VoidPortalBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape TELEPORT_NORTH = Block.createCuboidShape(0, 0, 0, 16, 16, 1);
    private static final VoxelShape TELEPORT_SOUTH = Block.createCuboidShape(0, 0, 15, 16, 16, 16);
    private static final VoxelShape TELEPORT_WEST  = Block.createCuboidShape(0, 0, 0, 1, 16, 16);
    private static final VoxelShape TELEPORT_EAST  = Block.createCuboidShape(15, 0, 0, 16, 16, 16);
    private static final VoxelShape TELEPORT_UP    = Block.createCuboidShape(0, 15, 0, 16, 16, 16);
    private static final VoxelShape TELEPORT_DOWN  = Block.createCuboidShape(0, 0, 0, 16, 1, 16);

    public VoidPortalBlock()
    {
        super(Settings.of(Material.PORTAL)
                .noCollision()
                .strength(-1.0F)
                .sounds(BlockSoundGroup.GLASS)
                .lightLevel(unused -> 11));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.NORTH));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (player.isSneaking())
            return ActionResult.PASS;

        PocketPortalTile tile = (PocketPortalTile) world.getBlockEntity(pos);

        if (tile == null)
            return ActionResult.PASS;

        if (world.isClient())
            return ActionResult.SUCCESS;

        if (player.getStackInHand(hand).getItem() == VoidHeartItems.VOID_HEART_PIECE)
        {
            if (tile.voidPieceInteract(player, player.getStackInHand(hand)))
            {
                return ActionResult.SUCCESS;
            }
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity collider)
    {
        if (!world.isClient() && collider.canUsePortals()
                && VoxelShapes.matchesAnywhere(
                VoxelShapes.cuboid(collider.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ())), getTeleportShape(state), BooleanBiFunction.AND))
        {
            PocketPortalTile tile = (PocketPortalTile) world.getBlockEntity(pos);

            if (tile == null || tile.getLinkedWorld() == null || tile.getLinkedFacing() == null || tile.getLinkedPos() == null)
                return;

            ServerWorld destination = world.getServer().getWorld(tile.getLinkedWorldKey());

            FabricDimensions.teleport(collider, destination,
                    (entity, newWorld, direction, offsetX, offsetY) ->
                    {
                        int yaw = (tile.getFacing().getHorizontal() - tile.getLinkedFacing().getOpposite().getHorizontal()) * 90;
                        return new BlockPattern.TeleportTarget(Vec3d.of(tile.getLinkedPos()).add(0.5, 0, 0.5), collider.getVelocity(), yaw);
                    });
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        super.onStateReplaced(state, world, pos, newState, moved);

        if (!state.isOf(newState.getBlock()))
        {
            PocketPortalTile tile = (PocketPortalTile) world.getBlockEntity(pos);
            tile.breakLink();
        }
    }

    private VoxelShape getTeleportShape(BlockState state)
    {
        switch (state.get(Properties.FACING))
        {
            case NORTH:
                return TELEPORT_NORTH;
            case SOUTH:
                return TELEPORT_SOUTH;
            case WEST:
                return TELEPORT_WEST;
            case EAST:
                return TELEPORT_EAST;
            case UP:
                return TELEPORT_UP;
            case DOWN:
                return TELEPORT_DOWN;
            default:
                return VoxelShapes.fullCube();
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
        return new PocketPortalTile();
    }
}