package net.voxelindustry.voidheart.common.content.athanor;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CrucibleBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            box(3, 0, 3, 13, 2, 13),
            box(2, 2, 2, 14, 9, 14),
            box(3, 9, 3, 13, 10, 13));

    public CrucibleBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(world.getBlockEntity(pos) instanceof CrucibleTile crucible) || player.isShiftKeyDown())
            return InteractionResult.SUCCESS;

        if (crucible.getStack().isEmpty())
        {
            var stackInHand = player.getItemInHand(hand);
            if (stackInHand.isEmpty())
                return InteractionResult.SUCCESS;

            crucible.setStack(stackInHand.copyWithCount(1));
            if (!player.isCreative())
                stackInHand.shrink(1);
        }
        else
        {
            player.getInventory().placeItemBackInInventory(crucible.getStack(), Prediction.SERVER_ONLY);
            crucible.setStack(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CrucibleTile(pos, state);
    }
}
