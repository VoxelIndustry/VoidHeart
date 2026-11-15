package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /voidheart secondheart grant|recharge <seconds>|revoke|fire}, to review the second heart without performing the rite.
 */
public final class SecondHeartCommands
{
    private SecondHeartCommands()
    {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("secondheart")
                .then(literal("grant").executes(context -> grant(context, true)))
                .then(literal("spent").executes(context -> grant(context, false)))
                .then(literal("recharge")
                        .then(argument("seconds", IntegerArgumentType.integer(0))
                                .executes(context -> recharge(context, IntegerArgumentType.getInteger(context, "seconds")))))
                .then(literal("revoke").executes(SecondHeartCommands::revoke))
                .then(literal("fire").executes(SecondHeartCommands::fire)));
    }

    private static int grant(CommandContext<CommandSourceStack> context, boolean ready) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        SecondHeart.grant(player, ready);
        context.getSource().sendSuccess(() -> Component.literal(ready ? "Second heart granted, ready" : "Second heart granted, spent"), false);
        return 1;
    }

    private static int recharge(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        SecondHeart.setRecharge(player, seconds * 20);
        context.getSource().sendSuccess(() -> Component.literal("Second heart back in " + seconds + " s"), false);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        SecondHeart.revoke(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.literal("Second heart removed"), false);
        return 1;
    }

    private static int fire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        SecondHeart.fireRite(context.getSource().getPlayerOrException());
        return 1;
    }
}
