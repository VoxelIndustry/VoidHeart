package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.content.sin.DiggingProficiency;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinBalance;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /voidheart sin infuse <sin|none>} sets the held weapon's sin and {@code /voidheart sin stage <sin> <1-5>} sets the player's proficiency, to review
 * temperaments and lore without tens of hours of fighting. {@code /voidheart sin digging <1-5>} sets the bite of Ravenous tools. {@code /voidheart sin where} reports both, for review only.
 */
public final class SinCommands
{
    private static final String NONE = "none";

    private SinCommands()
    {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("sin")
                .then(literal("infuse")
                        .then(argument("sin", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(Arrays.stream(Sin.values()).map(Sin::getSerializedName), java.util.stream.Stream.of(NONE)), suggestions))
                                .executes(SinCommands::infuse)))
                .then(literal("stage")
                        .then(argument("sin", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(Sin.values()).map(Sin::getSerializedName), suggestions))
                                .then(argument("stage", IntegerArgumentType.integer(1, SinBalance.STAGES))
                                        .executes(SinCommands::stage))))
                .then(literal("digging")
                        .then(argument("stage", IntegerArgumentType.integer(1, SinBalance.STAGES))
                                .executes(SinCommands::digging)))
                .then(literal("where").executes(SinCommands::where)));
    }

    private static Sin parse(CommandContext<CommandSourceStack> context)
    {
        var name = StringArgumentType.getString(context, "sin");
        return Arrays.stream(Sin.values()).filter(sin -> sin.getSerializedName().equals(name)).findFirst().orElse(null);
    }

    private static int infuse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var stack = player.getMainHandItem();
        if (!stack.is(VoidHeartTags.SIN_CLEANSABLE))
        {
            context.getSource().sendFailure(Component.literal("The held item can't carry a sin"));
            return 0;
        }

        if (NONE.equals(StringArgumentType.getString(context, "sin")))
        {
            stack.remove(VoidHeartDataComponents.SIN);
            context.getSource().sendSuccess(() -> Component.literal("Cleansed the held weapon"), false);
            return 1;
        }

        var sin = parse(context);
        if (sin == null)
        {
            context.getSource().sendFailure(Component.literal("Unknown sin " + StringArgumentType.getString(context, "sin")));
            return 0;
        }
        if (stack.is(VoidHeartTags.SIN_DIGGABLE) && sin != Sin.GLUTTONY)
        {
            context.getSource().sendFailure(Component.literal("Only gluttony goes on tools"));
            return 0;
        }
        stack.set(VoidHeartDataComponents.SIN, sin);
        context.getSource().sendSuccess(() -> Component.literal("Infused the held weapon with " + sin.getSerializedName()), false);
        return 1;
    }

    private static int stage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var sin = parse(context);
        if (sin == null)
        {
            context.getSource().sendFailure(Component.literal("Unknown sin " + StringArgumentType.getString(context, "sin")));
            return 0;
        }

        var stage = IntegerArgumentType.getInteger(context, "stage");
        SinProficiencies.set(player, sin, SinBalance.STAGE_STARTS[stage - 1] * SinBalance.DEVOTED_POINTS);
        context.getSource().sendSuccess(() -> Component.literal("Set " + sin.getSerializedName() + " to stage " + stage), false);
        return 1;
    }

    private static int digging(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var stage = IntegerArgumentType.getInteger(context, "stage");
        DiggingProficiency.set(player, SinBalance.STAGE_STARTS[stage - 1] * SinBalance.DIGGING_DEVOTED_POINTS);
        context.getSource().sendSuccess(() -> Component.literal("Set digging to stage " + stage), false);
        return 1;
    }

    private static int where(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var proficiency = SinProficiencies.of(player);
        var held = SinProficiencies.sinOf(player.getMainHandItem());
        var report = new StringBuilder("Held: ").append(held == null ? NONE : held.getSerializedName());
        for (var sin : Sin.values())
            report.append(", ").append(sin.getSerializedName()).append(' ').append(Math.round(proficiency.points(sin))).append(" (stage ").append(proficiency.stage(sin)).append(')');
        report.append(", digging ").append(Math.round(DiggingProficiency.points(player))).append(" (stage ").append(DiggingProficiency.stage(player)).append(')');
        context.getSource().sendSuccess(() -> Component.literal(report.toString()), false);
        return 1;
    }
}
