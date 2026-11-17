package net.voxelindustry.voidheart.common.content.blight;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
 * The second void blight: a slow, wide scythe whose full swings raise what they kill as wraiths fighting for the wielder. See {@link HarvestWraith}.
 * It sweeps like a sword, through the swords tag.
 */
public class BlightScytheItem extends Item
{
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath(MODID, "blight_scythe_reach");

    public BlightScytheItem(Properties properties)
    {
        super(properties.sword(ToolMaterial.NETHERITE, 0, 0)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, BlightBalance.SCYTHE_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, BlightBalance.SCYTHE_SPEED, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, BlightBalance.SCYTHE_REACH, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build())
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    /** A mob slain by a full swing of the scythe, or by its sweep, rises for its slayer. */
    public static void onDeath(LivingEntity slain, DamageSource source)
    {
        if (!(slain.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player slayer) || source.getDirectEntity() != slayer)
            return;
        if (!(slayer.getMainHandItem().getItem() instanceof BlightScytheItem) || !ChargedSwings.isCharged(slayer))
            return;
        if (!Harvest.canRise(slain.getMaxHealth(), slain instanceof Player, slain.getType().builtInRegistryHolder().is(HarvestWraith.UNHARVESTABLE), slain instanceof HarvestWraith))
            return;
        HarvestWraith.raise(level, slain, slayer);
    }

    public static void register()
    {
        ServerLivingEntityEvents.AFTER_DEATH.register(BlightScytheItem::onDeath);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".void_blight_scythe.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".void_blight_scythe.use", BlightBalance.WRAITH_MAX).withStyle(ChatFormatting.DARK_GRAY));
    }
}
