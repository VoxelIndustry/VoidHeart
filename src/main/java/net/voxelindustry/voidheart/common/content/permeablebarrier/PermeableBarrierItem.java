package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
    public PermeableBarrierItem()
    {
        super(new Properties()
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
        CompoundTag tag = stack.getOrCreateTag();
        if (hasFirstPoint(tag))
        {
            BlockPos first = BlockPos.of(tag.getLong("firstPoint"));
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

                        stack.hurt(1, context.getLevel().getRandom(), (ServerPlayer) context.getPlayer());
                        if (stack.getDamageValue() == stack.getMaxDamage())
                        {
                            stack.setCount(0);
                            break;
                        }
                    }
                }

                tag.remove("firstPoint");
            }
        }
        else
            setFirstPoint(tag, context.getClickedPos().relative(context.getClickedFace()));
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

    private boolean hasFirstPoint(CompoundTag tag)
    {
        return tag.contains("firstPoint");
    }

    private void setFirstPoint(CompoundTag tag, BlockPos pos)
    {
        tag.putLong("firstPoint", pos.asLong());
    }
}
