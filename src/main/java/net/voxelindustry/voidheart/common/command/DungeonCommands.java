package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonBlueprint;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Tools to review dungeons, until players have their own way in.
 */
final class DungeonCommands
{
    private static final int DEFAULT_DIFFICULTY = 3;

    private DungeonCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("dungeon")
                .then(literal("open")
                        .then(sizeAndTheme()
                                .executes(context -> open(context, DEFAULT_DIFFICULTY))
                                .then(difficulty().executes(context -> open(context, IntegerArgumentType.getInteger(context, "difficulty")))))
                )
                .then(literal("visit")
                        .then(sizeAndTheme()
                                .then(difficulty()
                                        .then(argument("index", IntegerArgumentType.integer(0, DungeonAddress.MAX_INDEX))
                                                .executes(DungeonCommands::visit)))))
                .then(literal("where").executes(DungeonCommands::where)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> sizeAndTheme()
    {
        return argument("kind", StringArgumentType.word())
                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(kinds(), suggestions));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> difficulty()
    {
        return argument("difficulty", IntegerArgumentType.integer(DungeonAddress.MIN_DIFFICULTY, DungeonAddress.MAX_DIFFICULTY));
    }

    private static Iterable<String> kinds()
    {
        return Arrays.stream(DungeonSize.values())
                .flatMap(size -> Arrays.stream(DungeonTheme.values()).filter(theme -> theme.comesIn(size)).map(theme -> size.getSerializedName() + "_" + theme.getSerializedName()))
                .toList();
    }

    private static <T extends StringRepresentable> T parse(T[] values, String name) throws CommandSyntaxException
    {
        for (var value : values)
        {
            if (value.getSerializedName().equals(name))
                return value;
        }
        throw new SimpleCommandExceptionType(Component.literal("Unknown value " + name)).create();
    }

    /**
     * Size and theme share one word so that a single completion covers both, as in small_cathedral.
     */
    private static DungeonAddress address(CommandContext<CommandSourceStack> context, int difficulty, int index) throws CommandSyntaxException
    {
        var parts = StringArgumentType.getString(context, "kind").split("_", 2);
        if (parts.length != 2)
            throw new SimpleCommandExceptionType(Component.literal("Expected size_theme, like small_cathedral")).create();
        var size = parse(DungeonSize.values(), parts[0]);
        var theme = parse(DungeonTheme.values(), parts[1]);
        if (!theme.comesIn(size))
            throw new SimpleCommandExceptionType(Component.literal("No " + theme.getSerializedName() + " dungeon comes in " + size.getSerializedName())).create();
        return new DungeonAddress(size, theme, difficulty, index);
    }

    private static int open(CommandContext<CommandSourceStack> context, int difficulty) throws CommandSyntaxException
    {
        var wanted = address(context, difficulty, 0);
        var server = context.getSource().getServer();
        var address = VoidDungeons.open(server, wanted.size(), wanted.theme(), difficulty);
        return enter(context, address);
    }

    private static int visit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return enter(context, address(context, IntegerArgumentType.getInteger(context, "difficulty"), IntegerArgumentType.getInteger(context, "index")));
    }

    private static int enter(CommandContext<CommandSourceStack> context, DungeonAddress address) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        VoidDungeons.enter(player, address);
        var blueprint = DungeonBlueprint.of(VoidDungeons.level(context.getSource().getServer()).getSeed(), address);
        context.getSource().sendSuccess(() -> Component.literal(describe(address, blueprint)), false);
        return 1;
    }

    private static int where(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        if (address == null || player.level().dimension() != VoidDungeons.WORLD_KEY)
        {
            context.getSource().sendFailure(Component.literal("Not inside a dungeon"));
            return 0;
        }

        var blueprint = DungeonBlueprint.of(VoidDungeons.level(context.getSource().getServer()).getSeed(), address);
        context.getSource().sendSuccess(() -> Component.literal(describe(address, blueprint)), false);
        return 1;
    }

    private static String describe(DungeonAddress address, DungeonBlueprint blueprint)
    {
        return address.size().getSerializedName() + " " + address.theme().getSerializedName() + " dungeon " + address.index()
               + ", difficulty " + address.difficulty() + ", " + blueprint.layout().name().toLowerCase();
    }
}
