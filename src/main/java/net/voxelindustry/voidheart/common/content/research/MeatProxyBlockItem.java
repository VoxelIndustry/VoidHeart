package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/** A Meat Proxy, telling how long it can still bear strain. */
public class MeatProxyBlockItem extends BlockItem
{
    public MeatProxyBlockItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        double left = stack.getOrDefault(VoidHeartDataComponents.PROXY_ENDURANCE, Knowledge.proxySeconds());
        tooltip.accept(Component.translatable(MODID + ".meat_proxy.endurance", (int) Math.ceil(left / 60)).withStyle(ChatFormatting.GRAY));
    }
}
