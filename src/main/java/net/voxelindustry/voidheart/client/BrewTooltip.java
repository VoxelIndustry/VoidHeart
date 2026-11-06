package net.voxelindustry.voidheart.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.voxelindustry.voidheart.common.content.oil.Brews;

import java.util.List;

/**
 * Says how a brew was refined, under its name: a stronger or longer brew, and the vessel it was brewed in with what that vessel asks back.
 */
public final class BrewTooltip
{
    private BrewTooltip()
    {
    }

    public static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines)
    {
        var brew = Brews.brewOf(stack);
        if (brew.isPlain())
            return;

        int at = Math.min(1, lines.size());
        if (brew.empowered())
            lines.add(at++, Component.translatable("voidheart.brew.empowered").withStyle(ChatFormatting.AQUA));
        if (brew.prolonged())
            lines.add(at++, Component.translatable("voidheart.brew.prolonged").withStyle(ChatFormatting.RED));
        if (brew.vessel().isPresent())
            lines.add(at, Component.translatable(brew.vessel().get().traitKey()).withStyle(ChatFormatting.GOLD));
    }
}
