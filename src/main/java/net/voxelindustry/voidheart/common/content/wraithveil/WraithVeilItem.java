package net.voxelindustry.voidheart.common.content.wraithveil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Pressed to a wall, floor or ceiling, it marks the surface black the way a wraith does and opens a tunnel of shadow through to the other side,
 * which its bearer may walk until it collapses. Where the way cannot be crossed no mark shows, and using it does nothing.
 */
public class WraithVeilItem extends Item
{
    public WraithVeilItem(Properties properties)
    {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".wraith_veil.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".wraith_veil.use", WallPassagePlan.MAX_DEPTH).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        var player = context.getPlayer();
        if (player == null || !WallPassages.canStart(player))
            return InteractionResult.PASS;

        var level = context.getLevel();
        var plan = WallPassagePlan.plan(WallPassages.cells(level, player), context.getClickedPos(), context.getClickedFace(), player.position());
        if (plan == null)
            return InteractionResult.FAIL;

        if (player instanceof ServerPlayer serverPlayer)
        {
            WallPassages.begin(serverPlayer, plan);
            player.getCooldowns().addCooldown(context.getItemInHand(), WallPassagePlan.Plan.cooldownTicks());
        }
        return InteractionResult.SUCCESS;
    }
}
