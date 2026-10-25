package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageItemBlock extends BlockItem
{
    public RavenousStorageItemBlock(Block block, Item.Properties properties)
    {
        super(block, properties
                .stacksTo(1)
                .durability(RavenousCollectorTile.CAPACITY)
        );
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
        else
            tooltip.accept(Component.translatable(MODID + ".ravenous_collector.untyped"));
    }
}
