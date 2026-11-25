package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Bound to a void door of its owner's pocket, it raises a diaphane door out of thin air that opens onto it.
 */
public class RiftKeyItem extends Item
{
    public RiftKeyItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.RARE)
                .stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var stack = context.getItemInHand();

        if (!(state.getBlock() instanceof VoidDoorBlock) || stack.has(VoidHeartDataComponents.RIFT_KEY_LINK) || !level.dimension().equals(VoidHeart.VOID_WORLD_KEY))
            return InteractionResult.PASS;

        if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player)
        {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
                pos = pos.below();
            RiftKeys.bind(player, stack, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        var stack = player.getItemInHand(hand);
        var link = stack.get(VoidHeartDataComponents.RIFT_KEY_LINK);
        if (link == null)
            return InteractionResult.PASS;

        if (player instanceof ServerPlayer serverPlayer && RiftKeys.open(serverPlayer, stack, link))
            player.getCooldowns().addCooldown(stack, RiftKeys.COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var link = stack.get(VoidHeartDataComponents.RIFT_KEY_LINK);
        if (link == null)
            tooltip.accept(Component.translatable(MODID + ".rift_key.blank").withStyle(ChatFormatting.GRAY));
        else
        {
            tooltip.accept(Component.translatable(MODID + ".rift_key.opens_onto", link.ownerName()).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.accept(Component.translatable(MODID + ".rift_key.use").withStyle(ChatFormatting.GRAY));
        }
    }
}
