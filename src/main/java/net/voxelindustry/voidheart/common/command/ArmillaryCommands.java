package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryConnections;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.DaedalusConnections;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Tools to review an armillary without waiting on its timers.
 */
final class ArmillaryCommands
{
    private static final int SEARCH_RADIUS = 16;

    private ArmillaryCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("armillary")
                .then(literal("reveal").executes(ArmillaryCommands::reveal))
                .then(literal("search")
                        .then(argument("percent", IntegerArgumentType.integer(0, 100)).executes(ArmillaryCommands::search)))
                .then(literal("decay")
                        .then(argument("seconds", IntegerArgumentType.integer(1)).executes(ArmillaryCommands::decay))));
    }

    private static int reveal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var armillary = nearest(context);
        if (armillary == null)
            return noArmillary(context);

        armillary.revealBubble();
        context.getSource().sendSuccess(() -> Component.literal("Revealed a bubble"), false);
        return 1;
    }

    private static int search(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var armillary = nearest(context);
        if (armillary == null)
            return noArmillary(context);
        if (!armillary.advanceSearch(IntegerArgumentType.getInteger(context, "percent") / 100F))
        {
            context.getSource().sendFailure(Component.literal("No search is running"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Search moved along"), false);
        return 1;
    }

    /**
     * Works on the armillary nearby, or from inside the dungeon or the Daedalus a connection leads to.
     */
    private static int decay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        var server = context.getSource().getServer();

        boolean done;
        if (player.level().dimension().equals(VoidDungeons.WORLD_KEY))
        {
            var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
            done = address != null && ArmillaryConnections.get(server).decayIn(address, ArmillaryConnections.now(server) + seconds * 20L);
        }
        else if (player.level().dimension().equals(VoidDaedalus.WORLD_KEY))
        {
            var connections = DaedalusConnections.get(server);
            var entry = connections.entryOf(player.getUUID());
            done = entry != null && connections.decayAt(entry, ArmillaryConnections.now(server) + seconds * 20L);
        }
        else
        {
            var armillary = nearest(context);
            done = armillary != null && armillary.decayIn(seconds);
        }

        if (!done)
        {
            context.getSource().sendFailure(Component.literal("No connection to shorten"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Connection decays in " + seconds + " seconds"), false);
        return 1;
    }

    private static int noArmillary(CommandContext<CommandSourceStack> context)
    {
        context.getSource().sendFailure(Component.literal("No armillary within " + SEARCH_RADIUS + " blocks"));
        return 0;
    }

    private static @Nullable ArmillaryTile nearest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var origin = player.blockPosition();
        ArmillaryTile nearest = null;
        double best = Double.MAX_VALUE;
        for (var pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS), origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)))
        {
            if (player.level().getBlockEntity(pos) instanceof ArmillaryTile armillary && pos.distSqr(origin) < best)
            {
                nearest = armillary;
                best = pos.distSqr(origin);
            }
        }
        return nearest;
    }
}
