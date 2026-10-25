package net.voxelindustry.voidheart.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * BlockItem displaying fixed translated lore lines.
 */
public class LoreBlockItem extends BlockItem
{
    private final String[] loreKeys;

    public LoreBlockItem(Block block, Properties properties, String... loreKeys)
    {
        super(block, properties);
        this.loreKeys = loreKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        for (String loreKey : loreKeys)
            tooltip.accept(Component.translatable(loreKey));
    }
}
