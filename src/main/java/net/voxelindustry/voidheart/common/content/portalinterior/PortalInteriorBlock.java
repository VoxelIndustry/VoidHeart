package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
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

import java.util.List;

import static java.util.Collections.emptyList;

public class PortalInteriorBlock extends Block implements BlockEntityProvider
{
    protected static final VoxelShape X_TELEPORT_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
    protected static final VoxelShape Y_TELEPORT_SHAPE = Block.createCuboidShape(0, 6, 0, 16, 10, 16);
    protected static final VoxelShape Z_TELEPORT_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

    protected static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 0, 0, 9, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.createCuboidShape(7, 0, 0, 16, 16, 16);

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0, 0, 7, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 9);

    protected static final VoxelShape UP_SHAPE   = Block.createCuboidShape(0, 0, 0, 16, 9, 16);
    protected static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(0, 7, 0, 16, 16, 16);

    public PortalInteriorBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.LIME)
                .instrument(Instrument.XYLOPHONE)
                .requiresTool()
                .strength(-1.0F)
                .sounds(BlockSoundGroup.GLASS)
                .luminance(unused -> 11));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.FACING, Direction.NORTH));
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder)
    {
        return emptyList();
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        switch (state.get(Properties.FACING))
        {
            case UP:
                return UP_SHAPE;
            case DOWN:
                return DOWN_SHAPE;
            case NORTH:
                return NORTH_SHAPE;
            case SOUTH:
                return SOUTH_SHAPE;
            case EAST:
                return EAST_SHAPE;
            case WEST:
            default:
                return WEST_SHAPE;
        }
    }

    public VoxelShape getTeleportShape(BlockState state)
    {
        switch (state.get(Properties.FACING))
        {
            case UP:
            case DOWN:
                return Y_TELEPORT_SHAPE;
            case NORTH:
            case SOUTH:
                return X_TELEPORT_SHAPE;
            case EAST:
            case WEST:
            default:
                return Z_TELEPORT_SHAPE;
        }
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity collider)
    {
        if (collider.hasVehicle() || collider.hasPassengers() || !collider.canUsePortals())
            return;

        if (!world.isClient()
                && VoxelShapes.matchesAnywhere(
                VoxelShapes.cuboid(collider.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ())), getTeleportShape(state), BooleanBiFunction.AND))
        {
            PortalInteriorTile tile = (PortalInteriorTile) world.getBlockEntity(pos);

            if (tile == null)
                return;

            tile.teleport(collider);
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
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalInteriorTile(pos, state);
    }
}