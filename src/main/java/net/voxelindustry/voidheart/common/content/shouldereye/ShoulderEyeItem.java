package net.voxelindustry.voidheart.common.content.shouldereye;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Binds a Shoulder Eye to whoever uses it. Sneaking with an empty hand on the eye gives it back as this item.
 */
public class ShoulderEyeItem extends Item
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
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (ShoulderEyes.hasEye(player))
        {
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".shoulder_eye.already_bound"));
            return InteractionResult.FAIL;
        }

        if (player instanceof ServerPlayer serverPlayer)
        {
            ShoulderEyes.bind(serverPlayer);
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
