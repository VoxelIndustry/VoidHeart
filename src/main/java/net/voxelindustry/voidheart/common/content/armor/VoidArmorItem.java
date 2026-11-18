package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Void Armor's frame. As an item it only goes from where it was made onto a base; from there it travels worn. Its grids, racks, socket and charge are
 * data on the stack.
 */
public class VoidArmorItem extends Item
{
    public VoidArmorItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    public static long charge(ItemStack frame)
    {
        return frame.getOrDefault(RubrumContent.VOID_CHARGE, 0L);
    }

    public static ArmorStats stats(ItemStack frame)
    {
        return frame.getOrDefault(VoidArmorContent.ARMOR_GRIDS, ArmorGrids.EMPTY).stats();
    }

    /**
     * A copy of the Shielding Apparatus in the frame's socket, or an empty stack.
     */
    public static ItemStack socketed(ItemStack frame)
    {
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        frame.getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(slots);
        return slots.get(ArmorBalance.SOCKET_SLOT);
    }

    public static float fill(ItemStack frame)
    {
        long capacity = stats(frame).capacity();
        return capacity <= 0 ? 0 : Math.clamp(charge(frame) / (float) capacity, 0, 1);
    }

    public static ItemStack full(Item item)
    {
        var stack = new ItemStack(item);
        stack.set(RubrumContent.VOID_CHARGE, ArmorBalance.FRAME_CAPACITY);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".void_armor.lore"));
        tooltip.accept(Component.translatable(MODID + ".void_armor.dock").withStyle(ChatFormatting.DARK_GRAY));
    }
}
