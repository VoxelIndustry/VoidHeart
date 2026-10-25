package net.voxelindustry.voidheart.common.content.shatterforge;

import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ShatterForgeBlock extends Block implements EntityBlock
{
    private final VoxelShape SHAPE_X = Shapes.or(
            box(1, 0, 1, 15, 4, 15),
            box(2, 4, 2, 14, 5, 14),
            box(6, 5, 4, 10, 12, 12),
            box(4, 12, 2, 12, 16, 14)
    );

    private final VoxelShape SHAPE_Z = Shapes.or(
            box(1, 0, 1, 15, 4, 15),
            box(2, 4, 2, 14, 5, 14),
            box(4, 5, 6, 12, 12, 10),
            box(2, 12, 4, 14, 16, 12)
    );

    public ShatterForgeBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3F)
                .sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 11 : 0));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Axis.X));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var forge = (ShatterForgeTile) world.getBlockEntity(pos);

        if (forge == null)
            return InteractionResult.SUCCESS;

        if (forge.getStack().isEmpty())
        {
            var stackInHand = player.getItemInHand(hand);
            if (!stackInHand.isEmpty())
            {
                forge.setStack(stackInHand.copyWithCount(1));

                if (!player.isCreative())
                    stackInHand.shrink(1);
            }
        }
        else
        {
            player.getInventory().placeItemBackInInventory(forge.getStack().copyWithCount(1));
            forge.setStack(ItemStack.EMPTY);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        if (state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Axis.X)
            return SHAPE_X;
        return SHAPE_Z;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, ctx.getHorizontalDirection().getAxis());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_AXIS);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ShatterForgeTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.SHATTER_FORGE, ShatterForgeTile::tick);
    }
}
