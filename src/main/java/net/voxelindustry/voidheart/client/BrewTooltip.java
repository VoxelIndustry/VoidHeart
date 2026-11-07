package net.voxelindustry.voidheart.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.voxelindustry.voidheart.common.content.oil.Brew;
import net.voxelindustry.voidheart.common.content.oil.Brews;

import java.util.ArrayList;
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
        lines.addAll(Math.min(1, lines.size()), lines(Brews.brewOf(stack)));
    }

    /**
     * @return one line per shard, then one for the vessel, empty for a plain brew
     */
    public static List<Component> lines(Brew brew)
    {
        var lines = new ArrayList<Component>();
        if (brew.empowered())
            lines.add(Component.translatable("voidheart.brew.empowered").withStyle(ChatFormatting.AQUA));
        if (brew.prolonged())
            lines.add(Component.translatable("voidheart.brew.prolonged").withStyle(ChatFormatting.RED));
        if (brew.remembering())
            lines.add(Component.translatable("voidheart.brew.remembering").withStyle(ChatFormatting.LIGHT_PURPLE));
        brew.vessel().ifPresent(vessel -> lines.add(Component.translatable(vessel.traitKey()).withStyle(ChatFormatting.GOLD)));
        return lines;
    }
}
