package net.voxelindustry.voidheart.common.content.shouldereye;

import eu.pb4.trinkets.api.callback.TrinketCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Worn in its own Trinkets slot of the head group, it calls a Shoulder Eye to its wearer. Using it puts it in that slot.
 */
public class ShoulderEyeItem extends Item implements TrinketCallback
{
    public ShoulderEyeItem(Properties properties)
    {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".shoulder_eye.lore").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canEquipFromUse(ItemStack stack, LivingEntity entity)
    {
        return true;
    }
}
