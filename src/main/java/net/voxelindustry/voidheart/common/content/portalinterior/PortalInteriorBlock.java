package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.LevelReader;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static java.util.Collections.emptyList;

public class PortalInteriorBlock extends Block implements EntityBlock
{
    protected static final VoxelShape X_TELEPORT_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    protected static final VoxelShape Y_TELEPORT_SHAPE = Block.box(0, 6, 0, 16, 10, 16);
    protected static final VoxelShape Z_TELEPORT_SHAPE = Block.box(6, 0, 0, 10, 16, 16);

    protected static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 9, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.box(7, 0, 0, 16, 16, 16);

    protected static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 7, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 9);

    protected static final VoxelShape UP_SHAPE   = Block.box(0, 0, 0, 16, 9, 16);
    protected static final VoxelShape DOWN_SHAPE = Block.box(0, 7, 0, 16, 16, 16);

    public PortalInteriorBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_LIGHT_GREEN)
                .instrument(NoteBlockInstrument.XYLOPHONE)
                .requiresCorrectToolForDrops()
                .strength(-1.0F)
                .sound(SoundType.GLASS)
                .lightLevel(unused -> 11));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        return emptyList();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        switch (state.getValue(BlockStateProperties.FACING))
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
        switch (state.getValue(BlockStateProperties.FACING))
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
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity collider, InsideBlockEffectApplier effectApplier, boolean isPrecise)
    {
        if (collider.isPassenger() || collider.isVehicle() || !collider.canUsePortal(false))
            return;

        if (!world.isClientSide()
                && Shapes.joinIsNotEmpty(
                Shapes.create(collider.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ())), getTeleportShape(state), BooleanOp.AND))
        {
            PortalInteriorTile tile = (PortalInteriorTile) world.getBlockEntity(pos);

            if (tile == null)
                return;

            tile.teleport(collider);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return defaultBlockState().setValue(BlockStateProperties.FACING, ctx.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PortalInteriorTile(pos, state);
    }
}