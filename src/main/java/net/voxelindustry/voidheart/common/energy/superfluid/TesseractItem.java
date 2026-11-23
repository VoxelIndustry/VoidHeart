package net.voxelindustry.voidheart.common.energy.superfluid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A room folded into a room, which keeps the superfluid azurium drawn off a pearl's rings. It is used on a ringed pearl to draw off what fits, can be
 * topped up from any number of pearls, and never pours back. How full it is shows in words.
 */
public class TesseractItem extends Item
{
    public TesseractItem(Properties properties)
    {
        super(properties);
    }

    public static int held(ItemStack stack)
    {
        return Math.clamp(stack.getOrDefault(SuperfluidContent.SUPERFLUID, 0), 0, SuperfluidBalance.TESSERACT_CAPACITY);
    }

    public static int room(ItemStack stack)
    {
        return SuperfluidBalance.TESSERACT_CAPACITY - held(stack);
    }

    /**
     * Sets what it holds. An empty one carries no component, so it stays a plain item.
     */
    public static void setHeld(ItemStack stack, int units)
    {
        var clamped = Math.clamp(units, 0, SuperfluidBalance.TESSERACT_CAPACITY);
        if (clamped == 0)
            stack.remove(SuperfluidContent.SUPERFLUID);
        else
            stack.set(SuperfluidContent.SUPERFLUID, clamped);
    }

    /**
     * A brimming tesseract, the catalyst its altar recipes ask for.
     */
    public static ItemStack full()
    {
        var stack = new ItemStack(SuperfluidContent.TESSERACT);
        setHeld(stack, SuperfluidBalance.TESSERACT_CAPACITY);
        return stack;
    }

    public static SuperfluidRings.Fill fill(ItemStack stack)
    {
        return SuperfluidRings.fill(held(stack), SuperfluidBalance.TESSERACT_CAPACITY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var fill = fill(stack);
        tooltip.accept(Component.translatable(MODID + ".tesseract.fill." + fill.getSerializedName())
                .withStyle(fill == SuperfluidRings.Fill.EMPTY ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        tooltip.accept(Component.translatable(MODID + ".tesseract.lore"));
    }
}
