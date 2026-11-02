package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;

import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Lets an operator move a player along, mainly to bring existing worlds and tests up to speed.
 */
public class ResearchCommands
{
    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("research")
                .then(literal("knowledge").then(argument("amount", DoubleArgumentType.doubleArg())
                        .executes(context -> apply(context, data -> data.addKnowledge(DoubleArgumentType.getDouble(context, "amount"))))))
                .then(literal("insights").then(argument("amount", IntegerArgumentType.integer())
                        .executes(context -> apply(context, data -> data.addInsights(IntegerArgumentType.getInteger(context, "amount"))))))
                .then(literal("grant")
                        .then(literal("all").executes(context ->
                        {
                            var tree = Knowledge.tree(context.getSource().registryAccess());
                            return apply(context, data -> tree.keySet().forEach(data::grant));
                        }))
                        .then(argument("research", IdentifierArgument.id())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggestResource(Knowledge.tree(context.getSource().registryAccess()).keySet(), suggestions))
                                .executes(context -> apply(context, data -> data.grant(IdentifierArgument.getId(context, "research")))))));
    }

    private static int apply(CommandContext<CommandSourceStack> context, Consumer<PlayerKnowledge> change) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var server = context.getSource().getServer();

        change.accept(Knowledge.of(server, player.getUUID()));
        KnowledgeState.get(server).setDirty();
        Knowledge.sendSnapshot(player);

        context.getSource().sendSuccess(() -> Component.translatable("voidheart.research.command_done", player.getDisplayName()), true);
        return 1;
    }
}
