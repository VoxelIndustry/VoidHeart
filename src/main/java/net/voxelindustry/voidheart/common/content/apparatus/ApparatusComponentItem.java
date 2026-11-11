package net.voxelindustry.voidheart.common.content.apparatus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A piece set in a Shielding Apparatus from its grid.
 */
public class ApparatusComponentItem extends Item
{
    private final ApparatusComponent component;

    public ApparatusComponentItem(Properties properties, ApparatusComponent component)
    {
        super(properties.stacksTo(16));
        this.component = component;
    }

    public ApparatusComponent component()
    {
        return component;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        if (component.capacity() > 0)
            tooltip.accept(Component.translatable(MODID + ".apparatus_component.capacity", ShieldingApparatusItem.number(component.capacity() / 2)).withStyle(ChatFormatting.AQUA));
        if (component.regen() > 0)
            tooltip.accept(Component.translatable(MODID + ".apparatus_component.regen", Math.round(component.regen() * 100)).withStyle(ChatFormatting.AQUA));
        if (component.quickening() > 0)
            tooltip.accept(Component.translatable(MODID + ".apparatus_component.quickening", Math.round(component.quickening() * 100)).withStyle(ChatFormatting.AQUA));
        if (component.ward() != null)
            tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.ward." + component.ward().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD));
        if (component.breakEffect() != null)
        {
            tooltip.accept(Component.translatable(MODID + ".shielding_apparatus.break." + component.breakEffect().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.accept(Component.translatable(MODID + ".apparatus_component.one_core").withStyle(ChatFormatting.DARK_GRAY));
        }

        var shape = component.shape();
        for (int y = 0; y < shape.height(); y++)
        {
            var row = new StringBuilder();
            for (int x = 0; x < shape.width(); x++)
            {
                int cellX = x;
                int cellY = y;
                row.append(shape.cells().stream().anyMatch(cell -> cell.x() == cellX && cell.y() == cellY) ? "■" : "□");
            }
            tooltip.accept(Component.literal(row.toString()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
