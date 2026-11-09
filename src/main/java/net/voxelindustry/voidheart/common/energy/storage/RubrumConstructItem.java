package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Construct core carrying the energy and bundles it held when broken.
 */
public class RubrumConstructItem extends BlockItem
{
    public RubrumConstructItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var contents = stack.get(RubrumContent.CONSTRUCT_CONTENTS);
        if (contents == null)
            return;

        var rubrum = contents.sockets().stream().filter(entry -> entry.bundle().isRubrum()).count();
        var azurium = contents.sockets().size() - rubrum;
        tooltip.accept(Component.translatable("voidheart.rubrum_construct.tooltip.charge",
                String.format("%,d", contents.stored()), String.format("%,d", contents.capacity())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("voidheart.rubrum_construct.tooltip.bundles", rubrum, azurium).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        var contents = stack.get(RubrumContent.CONSTRUCT_CONTENTS);
        return contents != null && contents.stored() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        var contents = stack.get(RubrumContent.CONSTRUCT_CONTENTS);
        if (contents == null || contents.capacity() <= 0)
            return 0;
        return Math.round(MAX_BAR_WIDTH * Math.min(1F, contents.stored() / (float) contents.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return RubrumCellItem.BAR_COLOR;
    }
}
