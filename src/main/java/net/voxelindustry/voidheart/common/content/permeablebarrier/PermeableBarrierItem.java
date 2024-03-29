package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class PermeableBarrierItem extends Item
{
    public PermeableBarrierItem()
    {
        super(new Settings()
                .rarity(Rarity.UNCOMMON)
                .maxCount(1)
                .maxDamage(128));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        if (context.getWorld().isClient())
            return ActionResult.SUCCESS;

        ItemStack stack = context.getStack();
        NbtCompound tag = stack.getOrCreateNbt();
        if (hasFirstPoint(tag))
        {
            BlockPos first = BlockPos.fromLong(tag.getLong("firstPoint"));
            BlockPos second = context.getBlockPos().offset(context.getSide());

            if (arePointsSamePlane(first, second))
            {
                Direction direction = /*getDirection(first, second, context.getPlayer())*/ context.getHorizontalPlayerFacing();

                for (BlockPos pos : BlockPos.iterate(first, second))
                {
                    BlockState existingState = context.getWorld().getBlockState(pos);

                    if (existingState.isAir())
                    {
                        context.getWorld().setBlockState(pos, VoidHeartBlocks.PERMEABLE_BARRIER.getDefaultState().with(Properties.FACING, direction));

                        stack.damage(1, context.getWorld().getRandom(), (ServerPlayerEntity) context.getPlayer());
                        if (stack.getDamage() == stack.getMaxDamage())
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
            setFirstPoint(tag, context.getBlockPos().offset(context.getSide()));
        return super.useOnBlock(context);
    }

    private Direction getDirection(BlockPos first, BlockPos second, ItemUsageContext context)
    {
        if (first.equals(second))
            return context.getHorizontalPlayerFacing();

        return null;
    }

    private boolean arePointsSamePlane(BlockPos firstPoint, BlockPos secondPoint)
    {
        return firstPoint.getX() == secondPoint.getX() || firstPoint.getY() == secondPoint.getY() || firstPoint.getZ() == secondPoint.getZ();
    }

    private boolean hasFirstPoint(NbtCompound tag)
    {
        return tag.contains("firstPoint");
    }

    private void setFirstPoint(NbtCompound tag, BlockPos pos)
    {
        tag.putLong("firstPoint", pos.asLong());
    }
}
