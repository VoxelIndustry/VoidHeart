package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.function.Consumer;

/**
 * Ravenous storage or storage matrix carrying the essence it held when broken.
 */
public class RavenousStorageBlockItem extends BlockItem
{
    public RavenousStorageBlockItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null)
        {
            tooltip.accept(Component.translatable(storage.type().translatableName()));
            tooltip.accept(Component.literal(storage.amount() + " / " + storage.maximumAmount()));
        }

        var matrix = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (matrix != null)
        {
            for (int index = 0; index < matrix.storageCount(); index++)
            {
                var type = matrix.types()[index];
                if (type != null && matrix.amounts()[index] > 0)
                    tooltip.accept(Component.translatable(type.translatableName())
                            .append(Component.literal("  " + matrix.amounts()[index] + " / " + matrix.maximumAmount())));
            }
        }
    }
}
