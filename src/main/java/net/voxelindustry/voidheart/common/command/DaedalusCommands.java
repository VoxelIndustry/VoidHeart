package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The way in and out of the Daedalus, until players have their own.
 */
final class DaedalusCommands
{
    private DaedalusCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("daedalus")
                .then(literal("enter").executes(DaedalusCommands::enter))
                .then(literal("leave").executes(DaedalusCommands::leave))
                .then(literal("where").executes(DaedalusCommands::where))
                .then(literal("visit")
                        .then(argument("theme", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(DaedalusTheme.values()).map(DaedalusTheme::getSerializedName), suggestions))
                                .executes(DaedalusCommands::visit))));
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        VoidDaedalus.enter(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        if (player.level().dimension() != VoidDaedalus.WORLD_KEY)
        {
            context.getSource().sendFailure(Component.literal("Not inside the Daedalus"));
            return 0;
        }
        VoidDaedalus.leave(player);
        return 1;
    }

    private static int visit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var wanted = StringArgumentType.getString(context, "theme");
        var theme = Arrays.stream(DaedalusTheme.values())
                .filter(candidate -> candidate.getSerializedName().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown theme " + wanted)).create());

        var player = context.getSource().getPlayerOrException();
        var pos = VoidDaedalus.visit(player, theme);
        var seed = VoidDaedalus.level(context.getSource().getServer()).getSeed();
        context.getSource().sendSuccess(() -> Component.literal("On a bridge of district " + pos.x() + " " + pos.z() + ", ").append(VoidDaedalus.nameOf(seed, pos)), false);
        return 1;
    }

    private static int where(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        if (player.level().dimension() != VoidDaedalus.WORLD_KEY)
        {
            context.getSource().sendFailure(Component.literal("Not inside the Daedalus"));
            return 0;
        }

        var seed = VoidDaedalus.level(context.getSource().getServer()).getSeed();
        var pos = DistrictPos.at(player.getBlockX(), player.getBlockZ());
        var place = DistrictPos.isInsideDistrict(player.getBlockX(), player.getBlockZ()) ? "In district " : "In the chasm beside district ";
        var theme = DaedalusTheme.at(pos).getSerializedName();
        context.getSource().sendSuccess(() -> Component.literal(place + pos.x() + " " + pos.z() + ", " + theme + ", ").append(VoidDaedalus.nameOf(seed, pos)), false);
        return 1;
    }
}
