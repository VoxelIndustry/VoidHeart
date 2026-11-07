package net.voxelindustry.voidheart.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;

import java.util.List;

/**
 * Names the oil a weapon is coated with and the hits it has left, under the weapon's name.
 */
public final class OilTooltip
{
    private OilTooltip()
    {
    }

    public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines)
    {
        var coating = OilCoating.coatingOf(stack);
        if (coating == null)
            return;

        var name = Component.translatable(coating.type().nameKey());
        var line = (OilCoating.isArrow(stack) ? name : Component.translatable("voidheart.oil.coating", name, coating.charges()))
                .withStyle(style -> style.withColor(TextColor.fromRgb(coating.type().color())));
        lines.add(Math.min(1, lines.size()), line);
    }
}
