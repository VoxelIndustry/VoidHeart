package net.voxelindustry.voidheart.common.content.shatterforge;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.block.BlockWithEntity.checkType;

public class ShatterForgeBlock extends Block implements BlockEntityProvider
{
    private final VoxelShape SHAPE_X = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(2, 4, 2, 14, 5, 14),
            createCuboidShape(6, 5, 4, 10, 12, 12),
            createCuboidShape(4, 12, 2, 12, 16, 14)
    );

    private final VoxelShape SHAPE_Z = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(2, 4, 2, 14, 5, 14),
            createCuboidShape(4, 5, 6, 12, 12, 10),
            createCuboidShape(2, 12, 4, 14, 16, 12)
    );

    public ShatterForgeBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.BLACK)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE)
                .luminance(state -> state.get(Properties.LIT) ? 11 : 0));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.LIT, false)
                .with(Properties.HORIZONTAL_AXIS, Axis.X));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        var forge = (ShatterForgeTile) world.getBlockEntity(pos);

        if (forge == null)
            return ActionResult.SUCCESS;

        if (forge.getStack().isEmpty())
        {
            var stackInHand = player.getStackInHand(hand);
            if (!stackInHand.isEmpty())
            {
                forge.setStack(stackInHand.copyWithCount(1));

                if (!player.isCreative())
                    stackInHand.decrement(1);
            }
        }
        else
        {
            player.getInventory().offerOrDrop(forge.getStack());
            forge.setStack(ItemStack.EMPTY);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()))
        {
            var tile = (ShatterForgeTile) world.getBlockEntity(pos);
            if (tile != null)
            {
                tile.removeItself();
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        if (state.get(Properties.HORIZONTAL_AXIS) == Axis.X)
            return SHAPE_X;
        return SHAPE_Z;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return this.getDefaultState().with(Properties.HORIZONTAL_AXIS, ctx.getHorizontalPlayerFacing().getAxis());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.LIT, Properties.HORIZONTAL_AXIS);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new ShatterForgeTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return checkType(type, VoidHeartTiles.SHATTER_FORGE, ShatterForgeTile::tick);
    }
}
