package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class PermeableBarrierItem extends Item
{
    public PermeableBarrierItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.UNCOMMON)
                .stacksTo(1)
                .durability(128));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (context.getLevel().isClientSide())
            return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        BlockPos first = stack.get(VoidHeartDataComponents.BARRIER_FIRST_POINT);
        if (first != null)
        {
            BlockPos second = context.getClickedPos().relative(context.getClickedFace());

            if (arePointsSamePlane(first, second))
            {
                Direction direction = /*getDirection(first, second, context.getPlayer())*/ context.getHorizontalDirection();

                for (BlockPos pos : BlockPos.betweenClosed(first, second))
                {
                    BlockState existingState = context.getLevel().getBlockState(pos);

                    if (existingState.isAir())
                    {
                        context.getLevel().setBlockAndUpdate(pos, VoidHeartBlocks.PERMEABLE_BARRIER.defaultBlockState().setValue(BlockStateProperties.FACING, direction));

                        stack.hurtAndBreak(1, (ServerLevel) context.getLevel(), (ServerPlayer) context.getPlayer(), item -> {});
                        if (stack.isEmpty())
                            break;
                    }
                }

                stack.remove(VoidHeartDataComponents.BARRIER_FIRST_POINT);
            }
        }
        else
            stack.set(VoidHeartDataComponents.BARRIER_FIRST_POINT, context.getClickedPos().relative(context.getClickedFace()));
        return super.useOn(context);
    }

    private Direction getDirection(BlockPos first, BlockPos second, UseOnContext context)
    {
        if (first.equals(second))
            return context.getHorizontalDirection();

        return null;
    }

    private boolean arePointsSamePlane(BlockPos firstPoint, BlockPos secondPoint)
    {
        return firstPoint.getX() == secondPoint.getX() || firstPoint.getY() == secondPoint.getY() || firstPoint.getZ() == secondPoint.getZ();
    }

}
