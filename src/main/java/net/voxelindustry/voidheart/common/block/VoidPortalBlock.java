package net.voxelindustry.voidheart.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.tile.VoidPortalTile;

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
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity collider)
    {
        if (!world.isClient() && collider.canUsePortals()
                && VoxelShapes.matchesAnywhere(
                VoxelShapes.cuboid(collider.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ())), getTeleportShape(state), BooleanBiFunction.AND))
        {
            VoidPortalTile tile = (VoidPortalTile) world.getBlockEntity(pos);

            if (tile == null)
                return;

            tile.teleport(collider);
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
        return new VoidPortalTile();
    }
}