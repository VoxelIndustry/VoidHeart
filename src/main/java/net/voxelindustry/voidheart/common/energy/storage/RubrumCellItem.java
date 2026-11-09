package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A rubrum shard sealed in voidstone, carrying void energy to wherever a cell socket stands.
 */
public class RubrumCellItem extends Item
{
    /** Rubrum red, from the rubrum bundle textures. */
    public static final int BAR_COLOR = 0xE0402A;

    public RubrumCellItem(Properties properties)
    {
        super(properties);
    }

    public static long charge(ItemStack stack)
    {
        return Math.clamp(stack.getOrDefault(RubrumContent.VOID_CHARGE, 0L), 0, RubrumBalance.CELL_CAPACITY);
    }

    /**
     * An empty cell carries no component, so it stacks with a new one.
     */
    public static void setCharge(ItemStack stack, long charge)
    {
        var clamped = Math.clamp(charge, 0, RubrumBalance.CELL_CAPACITY);
        if (clamped <= 0)
            stack.remove(RubrumContent.VOID_CHARGE);
        else
            stack.set(RubrumContent.VOID_CHARGE, clamped);
    }

    public static float fill(ItemStack stack)
    {
        return charge(stack) / (float) RubrumBalance.CELL_CAPACITY;
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        return charge(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        return Math.round(MAX_BAR_WIDTH * fill(stack));
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("voidheart.rubrum_cell.tooltip",
                String.format("%,d", charge(stack)), String.format("%,d", RubrumBalance.CELL_CAPACITY)).withStyle(ChatFormatting.GRAY));
    }
}
