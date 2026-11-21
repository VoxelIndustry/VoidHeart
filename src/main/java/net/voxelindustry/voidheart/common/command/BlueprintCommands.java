package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.blight.BlessedContraptionItem;
import net.voxelindustry.voidheart.common.content.blight.Blueprints;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Teaches or takes back the designs of blessed contraptions, for review: {@code /voidheart blueprint learn|forget <blueprint>|all} and {@code list}.
 */
public class BlueprintCommands
{
    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("blueprint")
                .then(literal("list").executes(BlueprintCommands::list))
                .then(literal("learn")
                        .then(literal("all").executes(context -> learnAll(context, true)))
                        .then(argument("blueprint", IdentifierArgument.id()).suggests(BlueprintCommands::suggest).executes(context -> learn(context, true))))
                .then(literal("forget")
                        .then(literal("all").executes(context -> learnAll(context, false)))
                        .then(argument("blueprint", IdentifierArgument.id()).suggests(BlueprintCommands::suggest).executes(context -> learn(context, false)))));
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
                                         SuggestionsBuilder suggestions)
    {
        return SharedSuggestionProvider.suggestResource(Blueprints.all(Knowledge.tree(context.getSource().registryAccess())), suggestions);
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var server = context.getSource().getServer();
        var pool = Blueprints.all(Knowledge.tree(server.registryAccess()));
        var known = Knowledge.of(server, player.getUUID()).getBlueprints();
        var shown = pool.stream().map(blueprint -> (known.contains(blueprint) ? "+ " : "- ") + blueprint).collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.literal(shown.isEmpty() ? "no blueprints" : shown), false);
        return pool.size();
    }

    private static int learn(CommandContext<CommandSourceStack> context, boolean learn) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var blueprint = IdentifierArgument.getId(context, "blueprint");
        if (learn)
            BlessedContraptionItem.learn(player, blueprint);
        else
            forget(context, blueprint);
        context.getSource().sendSuccess(() -> Component.translatable("voidheart.research.command_done", player.getDisplayName()), true);
        return 1;
    }

    private static int learnAll(CommandContext<CommandSourceStack> context, boolean learn) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        for (var blueprint : Blueprints.all(Knowledge.tree(context.getSource().registryAccess())))
        {
            if (learn)
                BlessedContraptionItem.learn(player, blueprint);
            else
                forget(context, blueprint);
        }
        context.getSource().sendSuccess(() -> Component.translatable("voidheart.research.command_done", player.getDisplayName()), true);
        return 1;
    }

    private static void forget(CommandContext<CommandSourceStack> context, Identifier blueprint) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var server = context.getSource().getServer();
        if (Knowledge.of(server, player.getUUID()).forgetBlueprint(blueprint))
        {
            KnowledgeState.get(server).setDirty();
            Knowledge.sendSnapshot(player);
        }
    }
}
