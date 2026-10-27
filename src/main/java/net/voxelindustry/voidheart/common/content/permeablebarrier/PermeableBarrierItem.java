package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

/**
 * Fills the rectangle between two clicked points with barrier blocks.
 */
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
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;

        var stack = context.getItemInHand();
        var second = context.getClickedPos().relative(context.getClickedFace());
        BlockPos first = stack.get(VoidHeartDataComponents.BARRIER_FIRST_POINT);
        if (first == null)
        {
            stack.set(VoidHeartDataComponents.BARRIER_FIRST_POINT, second);
            return InteractionResult.SUCCESS;
        }
        stack.remove(VoidHeartDataComponents.BARRIER_FIRST_POINT);

        if (!arePointsSamePlane(first, second))
            return InteractionResult.FAIL;

        BlockPos firstPlaced = null;
        for (var pos : BlockPos.betweenClosed(first, second))
        {
            if (!level.getBlockState(pos).isAir() || !PermeableBarrierStructure.canExtend(level, pos, player))
                continue;

            level.setBlockAndUpdate(pos, VoidHeartBlocks.PERMEABLE_BARRIER.defaultBlockState());
            if (firstPlaced == null)
                firstPlaced = pos.immutable();

            stack.hurtAndBreak(1, level, player, item -> {});
            if (stack.isEmpty())
                break;
        }

        if (firstPlaced != null)
            PermeableBarrierStructure.onBlockAdded(level, firstPlaced, player.getUUID());
        return InteractionResult.SUCCESS;
    }

    private boolean arePointsSamePlane(BlockPos firstPoint, BlockPos secondPoint)
    {
        return firstPoint.getX() == secondPoint.getX() || firstPoint.getY() == secondPoint.getY() || firstPoint.getZ() == secondPoint.getZ();
    }
}
