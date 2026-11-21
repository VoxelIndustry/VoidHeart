package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.blight.BlessedContraptionItem;
import net.voxelindustry.voidheart.common.content.blight.Blueprints;
import net.voxelindustry.voidheart.common.item.LoreItem;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a slain Kernacian Dragoon leaves. Studied in hand, it teaches how it moved the Dragoon, which brings the Void Armor's research into sight. Studying
 * spends nothing, since the frame is built around a Core.
 */
public class KernacianCoreItem extends LoreItem
{
    public KernacianCoreItem(Properties properties)
    {
        super(properties, MODID + ".kernacian_core.lore");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;

        if (!BlessedContraptionItem.learn(serverPlayer, Blueprints.VOID_ARMOR))
        {
            serverPlayer.sendOverlayMessage(Component.translatable(MODID + ".kernacian_core.known"));
            return InteractionResult.FAIL;
        }
        serverPlayer.sendOverlayMessage(Component.translatable(MODID + ".kernacian_core.studied"));
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 0.6F);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(MODID + ".kernacian_core.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
