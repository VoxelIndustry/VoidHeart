package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /voidheart armor wear|leave|charge <fraction>|toggle night_vision|spectral_lens}, to review the Void Armor without a base: {@code wear} closes a
 * fresh, full frame around the player at once, {@code leave} lets them out at once as holding sneak would, {@code charge} sets the worn frame's charge as a
 * share of its capacity, so its load shedding and going limp can be seen, and {@code toggle} switches one of its sights until its hotbar does.
 */
public final class ArmorCommands
{
    private ArmorCommands()
    {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("armor")
                .then(literal("wear").executes(ArmorCommands::wear))
                .then(literal("leave").executes(ArmorCommands::leave))
                .then(literal("charge").then(argument("fraction", FloatArgumentType.floatArg(0, 1))
                        .executes(context -> charge(context, FloatArgumentType.getFloat(context, "fraction")))))
                .then(literal("toggle")
                        .then(literal("night_vision").executes(context -> toggle(context, ArmorModule.NIGHT_VISION)))
                        .then(literal("spectral_lens").executes(context -> toggle(context, ArmorModule.SPECTRAL_LENS)))));
    }

    private static int wear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var frame = VoidArmorItem.full(VoidArmorContent.VOID_ARMOR);
        var refusal = ArmorWear.refusal(player, frame, player.position());
        if (refusal != null)
        {
            context.getSource().sendFailure(refusal);
            return 0;
        }
        ArmorWear.stepIn(player, frame, player.position());
        context.getSource().sendSuccess(() -> Component.literal("Stepping into a fresh frame"), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        if (!ArmorWear.wearing(player))
        {
            context.getSource().sendFailure(Component.literal("Not wearing a frame"));
            return 0;
        }
        ArmorWear.stepOut(player);
        context.getSource().sendSuccess(() -> Component.literal("Stepped out of the frame"), false);
        return 1;
    }

    private static int charge(CommandContext<CommandSourceStack> context, float fraction) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var worn = ArmorWear.worn(player);
        if (worn == null)
        {
            context.getSource().sendFailure(Component.literal("Not wearing a frame"));
            return 0;
        }
        long charge = Math.round(VoidArmorItem.stats(worn.frame()).capacity() * (double) fraction);
        ArmorWear.setCharge(player, charge);
        context.getSource().sendSuccess(() -> Component.literal("Frame charge set to " + charge), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> context, ArmorModule module) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        if (!ArmorWear.toggle(player, module))
        {
            context.getSource().sendFailure(Component.literal("No working " + module.getSerializedName() + " in a worn frame"));
            return 0;
        }
        var on = ArmorWear.worn(player).on().contains(module);
        context.getSource().sendSuccess(() -> Component.literal(module.getSerializedName() + (on ? " on" : " off")), false);
        return 1;
    }
}
