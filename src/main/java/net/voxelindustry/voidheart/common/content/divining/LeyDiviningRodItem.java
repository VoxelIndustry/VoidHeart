package net.voxelindustry.voidheart.common.content.divining;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Raised, it shows its bearer the ley lines and nodes around them for a few seconds, with the way and strength each line flows. Never wears out.
 */
public class LeyDiviningRodItem extends Item
{
    /**
     * Ticks before it can be raised again, a little shorter than what it shows lasts.
     */
    public static final int COOLDOWN_TICKS = 60;

    public LeyDiviningRodItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".ley_divining_rod.use", LeySight.RADIUS).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
        {
            var sight = LeySight.collect(serverLevel, player.position());
            ServerPlayNetworking.send(serverPlayer, sight);
            boolean found = !sight.lines().isEmpty() || !sight.nodes().isEmpty();
            level.playSound(null, player.blockPosition(), found ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                    found ? 0.8F : 0.5F, found ? 0.6F : 1.4F);
            if (!found)
                player.sendOverlayMessage(Component.translatable(MODID + ".ley_divining_rod.still"));
        }
        player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }
}
