package net.voxelindustry.voidheart.common.content.levitation;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.callback.TrinketCallback;
import eu.pb4.trinkets.api.component.TrinketDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.levitation.LevitationBalance.*;

/**
 * Worn in the belt slot of the legs group, it lifts every jump and softens every fall. Inside an azurium pylon's field it lets its wearer fly, see
 * {@link LevitationBelts}. Its modifiers stay out of the tooltip, which tells what it does in words.
 */
public class LevitationBeltItem extends Item implements TrinketCallback
{
    public LevitationBeltItem(Properties properties)
    {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(1)
                .component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(TrinketDataComponents.ATTRIBUTE_MODIFIERS, true)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".levitation_belt.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".levitation_belt.field").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean canEquipFromUse(ItemStack stack, LivingEntity entity)
    {
        return true;
    }

    @Override
    public void forEachTrinketModifier(ItemStack stack, TrinketSlotAccess slot, LivingEntity entity, Identifier slotIdentifier,
                                       BiConsumer<Holder<Attribute>, AttributeModifier> consumer)
    {
        consumer.accept(Attributes.JUMP_STRENGTH, new AttributeModifier(slotIdentifier.withSuffix("/jump"), JUMP_BONUS, AttributeModifier.Operation.ADD_VALUE));
        consumer.accept(Attributes.SAFE_FALL_DISTANCE,
                new AttributeModifier(slotIdentifier.withSuffix("/safe_fall"), SAFE_FALL_BONUS, AttributeModifier.Operation.ADD_VALUE));
        consumer.accept(Attributes.FALL_DAMAGE_MULTIPLIER,
                new AttributeModifier(slotIdentifier.withSuffix("/fall_damage"), FALL_DAMAGE_KEPT - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
