package net.voxelindustry.voidheart.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Item displaying fixed translated lore lines.
 */
public class LoreItem extends Item
{
    private final String[] loreKeys;

    public LoreItem(Properties properties, String... loreKeys)
    {
        super(properties);
        this.loreKeys = loreKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        for (String loreKey : loreKeys)
            tooltip.accept(Component.translatable(loreKey));
    }
}
