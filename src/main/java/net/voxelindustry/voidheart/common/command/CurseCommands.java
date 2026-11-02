package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.Curses;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Debug access to corruption and curses. {@code trigger} refuses when the surroundings would hide the curse, {@code force} doesn't.
 */
public class CurseCommands
{
    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("corruption")
                .then(literal("get")
                        .executes(context -> get(context, context.getSource().getPlayerOrException()))
                        .then(argument("target", EntityArgument.player())
                                .executes(context -> get(context, EntityArgument.getPlayer(context, "target")))))
                .then(literal("set")
                        .then(argument("level", IntegerArgumentType.integer(0, Corruption.MAX))
                                .executes(context -> set(context, List.of(context.getSource().getPlayerOrException())))
                                .then(argument("targets", EntityArgument.players())
                                        .executes(context -> set(context, EntityArgument.getPlayers(context, "targets")))))));

        builder.then(literal("curse")
                .then(strike("trigger", false))
                .then(strike("force", true))
                .then(literal("stop")
                        .executes(context -> stop(context, List.of(context.getSource().getPlayerOrException())))
                        .then(argument("targets", EntityArgument.players())
                                .executes(context -> stop(context, EntityArgument.getPlayers(context, "targets"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> strike(String name, boolean force)
    {
        return literal(name).then(argument("kind", StringArgumentType.word())
                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(CurseKind.values()).map(CurseKind::id), suggestions))
                .executes(context -> strike(context, List.of(context.getSource().getPlayerOrException()), force))
                .then(argument("targets", EntityArgument.players())
                        .executes(context -> strike(context, EntityArgument.getPlayers(context, "targets"), force))));
    }

    private static int strike(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, boolean force)
    {
        var source = context.getSource();
        var id = StringArgumentType.getString(context, "kind");
        var kind = Arrays.stream(CurseKind.values()).filter(candidate -> candidate.id().equals(id)).findFirst();
        if (kind.isEmpty())
        {
            source.sendFailure(Component.literal("No curse named " + id));
            return 0;
        }

        int struck = 0;
        for (var target : targets)
        {
            if (!force && !kind.get().canStrike(target))
            {
                source.sendFailure(Component.literal(target.getScoreboardName() + " must be outside at night for " + id + ", use force to run it anyway"));
                continue;
            }
            Curses.begin(target, kind.get());
            struck++;
        }

        int count = struck;
        if (count > 0)
            source.sendSuccess(() -> Component.literal("Struck " + count + " player(s) with " + id), true);
        return count;
    }

    private static int stop(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets)
    {
        int stopped = 0;
        for (var target : targets)
        {
            if (Curses.end(target))
                stopped++;
        }

        int count = stopped;
        context.getSource().sendSuccess(() -> Component.literal("Stopped the curse of " + count + " player(s)"), true);
        return count;
    }

    private static int get(CommandContext<CommandSourceStack> context, ServerPlayer target)
    {
        int level = Corruption.get(target);
        context.getSource().sendSuccess(() -> Component.literal(target.getScoreboardName() + " has " + level + " corruption"), false);
        return level;
    }

    private static int set(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets)
    {
        int level = IntegerArgumentType.getInteger(context, "level");
        targets.forEach(target -> Corruption.set(target, level));
        context.getSource().sendSuccess(() -> Component.literal("Set the corruption of " + targets.size() + " player(s) to " + level), true);
        return targets.size();
    }
}
