package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.HeartCradleTile;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /voidheart secondheart grant|spent|recharge <seconds>|revoke|fire}, to review the second heart without performing the rite, and
 * {@code ventricle <pos> fill|skip <kindling|beating> <ticks>} to hurry a Twin Ventricle's rite along.
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
                .then(literal("fire").executes(SecondHeartCommands::fire))
                .then(literal("ventricle").then(argument("pos", BlockPosArgument.blockPos())
                        .then(literal("fill").executes(SecondHeartCommands::fillBasins))
                        .then(literal("skip").then(argument("phase", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(new String[]{"kindling", "beating"}, suggestions))
                                .then(argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(SecondHeartCommands::skip)))))));
    }

    private static HeartCradleTile cradle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (context.getSource().getLevel().getBlockEntity(pos) instanceof HeartCradleTile cradle)
            return cradle;
        throw new SimpleCommandExceptionType(Component.literal("No heart cradle at " + pos.toShortString())).create();
    }

    /**
     * Fills both basins with what their walls call for, for review.
     */
    private static int fillBasins(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var cradle = cradle(context);
        var basins = cradle.basins(context.getSource().getLevel());
        if (basins == null)
        {
            context.getSource().sendFailure(Component.literal("The chamber lacks a basin"));
            return 0;
        }
        int filled = 0;
        for (var basin : basins)
        {
            var called = basin.calledFor();
            if (called != null)
                filled += basin.fillWith(called, VentricleBalance.BASIN_TANK);
        }
        int total = filled;
        context.getSource().sendSuccess(() -> Component.literal("Filled the basins with " + total), false);
        return total;
    }

    /**
     * Skips the rite ahead, as if the caller had made the offering, for review.
     */
    private static int skip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var cradle = cradle(context);
        var phase = StringArgumentType.getString(context, "phase").equals("beating") ? VentricleCeremony.Phase.BEATING : VentricleCeremony.Phase.KINDLING;
        cradle.skipTo(phase, IntegerArgumentType.getInteger(context, "ticks"), context.getSource().getPlayer());
        context.getSource().sendSuccess(() -> Component.literal("Rite skipped to " + phase.name().toLowerCase()), false);
        return 1;
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
