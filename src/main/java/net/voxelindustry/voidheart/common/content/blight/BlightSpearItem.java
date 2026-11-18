package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The third void blight: a netherite spear that jabs as one, but held back and let go it is thrown, opening a hole of darkness where it lands. See
 * {@link ThrownBlightSpear}. The netherite spear's braced charge is taken off it, since holding right click winds up the throw instead.
 */
public class BlightSpearItem extends Item
{
    private static final int USE_DURATION = 72000;

    public BlightSpearItem(Properties properties)
    {
        // The netherite spear's own numbers, so it jabs the same
        super(properties.spear(ToolMaterial.NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F)
                .component(DataComponents.KINETIC_WEAPON, null)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack)
    {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity)
    {
        return USE_DURATION;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        var stack = player.getItemInHand(hand);
        if (stack.nextDamageWillBreak() || player.getCooldowns().isOnCooldown(stack))
            return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime)
    {
        if (!(entity instanceof Player player) || getUseDuration(stack, entity) - remainingTime < BlightBalance.SPEAR_CHARGE_TICKS || stack.nextDamageWillBreak())
            return false;
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, BlightBalance.SPEAR_COOLDOWN_TICKS);
        if (level instanceof ServerLevel serverLevel)
        {
            stack.hurtWithoutBreaking(1, player);
            var thrown = stack.consumeAndReturn(1, player);
            var spear = Projectile.spawnProjectileFromRotation(ThrownBlightSpear::new, serverLevel, thrown, player, 0, BlightBalance.SPEAR_THROW_SPEED, 1);
            if (player.hasInfiniteMaterials())
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1, 0.7F);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".void_blight_spear.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".void_blight_spear.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
