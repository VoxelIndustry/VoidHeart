package net.voxelindustry.voidheart.common.content.avengingreach;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Eaten to grow one more void arm, each in turn. It refuses a body that lacks the arms before it, or has this one already, and is kept then.
 */
public class AvengingReachItem extends Item
{
    private static final int SICKNESS_TICKS = 100;

    /** Which arm this grows, from 1. */
    private final int number;

    public AvengingReachItem(Properties properties, int number)
    {
        super(properties
                .rarity(number >= ReachArms.MAX_ARMS ? Rarity.EPIC : number >= 3 ? Rarity.RARE : Rarity.UNCOMMON)
                .stacksTo(1)
                .food(new FoodProperties.Builder().alwaysEdible().build()));
        this.number = number;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".avenging_reach.lore." + number).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".avenging_reach.gift").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user)
    {
        if (!(user instanceof ServerPlayer player))
            return stack;

        int owned = AvengingReach.armsOf(player);
        if (!ReachArms.canTake(owned, number))
        {
            player.sendOverlayMessage(Component.translatable(MODID + (owned >= number ? ".avenging_reach.already" : ".avenging_reach.not_ready")));
            return stack;
        }

        AvengingReach.setArms(player, number);
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICKNESS_TICKS));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SICKNESS_TICKS / 2));
        world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1, 0.5F);
        player.sendSystemMessage(Component.translatable(MODID + ".avenging_reach.taken." + number));

        stack.consume(1, player);
        return stack;
    }
}
