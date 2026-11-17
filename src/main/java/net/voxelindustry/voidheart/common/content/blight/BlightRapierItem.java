package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The first void blight: a fast thrusting blade whose charged hits leave punctures in the dark, until enough of them unseam the target. See
 * {@link RapierStrikes}.
 */
public class BlightRapierItem extends Item
{
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath(MODID, "blight_rapier_reach");

    public BlightRapierItem(Properties properties)
    {
        super(properties.sword(ToolMaterial.NETHERITE, 0, 0)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, BlightBalance.RAPIER_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, BlightBalance.RAPIER_SPEED, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, BlightBalance.RAPIER_REACH, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build())
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        RapierStrikes.onHit(target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".void_blight_rapier.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".void_blight_rapier.use", Wound.HITS).withStyle(ChatFormatting.DARK_GRAY));
    }
}
