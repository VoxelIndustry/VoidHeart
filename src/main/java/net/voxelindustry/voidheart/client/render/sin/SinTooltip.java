package net.voxelindustry.voidheart.client.render.sin;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.sin.SinBalance;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import net.voxelindustry.voidheart.common.content.sin.SinProficiency;

import java.util.List;

/**
 * A sin weapon names its sin, then speaks one line of lore to the player looking at it, which changes as that player's proficiency passes hidden stages.
 * No number, bar or percentage is ever shown.
 */
public final class SinTooltip
{
    private SinTooltip()
    {
    }

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> append(stack, lines));
    }

    private static void append(ItemStack stack, List<Component> lines)
    {
        var sin = SinProficiencies.sinOf(stack);
        if (sin == null)
            return;

        var player = Minecraft.getInstance().player;
        var proficiency = player == null ? SinProficiency.NONE : SinProficiencies.of(player);

        lines.add(Component.translatable(sin.translationKey()).withStyle(Style.EMPTY.withColor(sin.color())));
        lines.add(Component.translatable(sin.loreKey(proficiency.stage(sin))).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        // A player devoted to another sin feels it resent this weapon
        var strongest = proficiency.strongest();
        if (strongest != null && strongest != sin && proficiency.stage(strongest) >= SinBalance.RESENT_STAGE && proficiency.stage(strongest) > proficiency.stage(sin))
            lines.add(Component.translatable(strongest.resentKey()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
