package net.voxelindustry.voidheart.common.content.portalinterior;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext.Builder;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;

import static java.util.Collections.emptyList;

public class PortalImmersiveInteriorBlock extends Block implements BlockEntityProvider
{
    protected static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0, 0, 0, 7, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.createCuboidShape(11, 0, 0, 16, 16, 16);

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0, 0, 11, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 7);

    protected static final VoxelShape UP_SHAPE   = Block.createCuboidShape(0, 0, 0, 16, 7, 16);
    protected static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(0, 11, 0, 16, 16, 16);

    public PortalImmersiveInteriorBlock()
    {
        super(Settings.of(Material.GLASS)
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
    public List<ItemStack> getDroppedStacks(BlockState state, Builder builder)
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
        return new PortalInteriorTile();
    }

    @Override
    public boolean is(Block block)
    {
        return block == VoidHeartBlocks.PORTAL_IMMERSIVE_INTERIOR || block == VoidHeartBlocks.PORTAL_INTERIOR;
    }
}