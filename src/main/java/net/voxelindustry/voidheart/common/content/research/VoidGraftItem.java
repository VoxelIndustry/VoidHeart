package net.voxelindustry.voidheart.common.content.research;

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
 * Eaten like the Void Amalgam, it takes the player one step further into the void. It refuses a body not yet ready for it, and is kept then.
 */
public class VoidGraftItem extends Item
{
    private static final int SICKNESS_TICKS = 200;

    private final int    attunement;
    private final String name;

    public VoidGraftItem(Properties properties, int attunement, String name)
    {
        super(properties
                .rarity(Rarity.RARE)
                .stacksTo(1)
                .food(new FoodProperties.Builder().alwaysEdible().build()));
        this.attunement = attunement;
        this.name = name;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + "." + name + ".lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + "." + name + ".flaw").withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable(MODID + "." + name + ".gift").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user)
    {
        if (!(user instanceof ServerPlayer player))
            return stack;

        var server = player.level().getServer();
        int current = Knowledge.of(server, player.getUUID()).getAttunement();
        if (current != attunement - 1)
        {
            player.sendOverlayMessage(Component.translatable(MODID + (current >= attunement ? ".graft.already" : ".graft.not_ready")));
            return stack;
        }

        Knowledge.attune(server, player.getUUID(), attunement);
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICKNESS_TICKS));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, SICKNESS_TICKS / 4));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SICKNESS_TICKS));
        world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1, 0.6F);
        player.sendSystemMessage(Component.translatable(MODID + ".graft.taken", Component.translatable(getDescriptionId())));

        stack.consume(1, player);
        return stack;
    }
}
