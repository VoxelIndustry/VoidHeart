package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /voidheart energy construct fill|bud, which skip the waits on the nearest construct for review.
 */
final class RubrumCommands
{
    private static final int REACH = 8;

    private RubrumCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("construct")
                .then(literal("fill").then(argument("percent", IntegerArgumentType.integer(0, 100)).executes(RubrumCommands::fill)))
                .then(literal("bud").then(argument("percent", IntegerArgumentType.integer(0, 100)).executes(RubrumCommands::bud))));
    }

    private static @Nullable RubrumConstructTile nearest(CommandSourceStack source)
    {
        var level = source.getLevel();
        var center = BlockPos.containing(source.getPosition());
        RubrumConstructTile best = null;
        var bestDistance = Double.MAX_VALUE;
        for (var pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH)))
        {
            if (level.getBlockEntity(pos) instanceof RubrumConstructTile core && pos.distSqr(center) < bestDistance)
            {
                bestDistance = pos.distSqr(center);
                best = core;
            }
        }
        if (best == null)
            source.sendFailure(Component.translatable("voidheart.rubrum_construct.command.none"));
        return best;
    }

    private static int fill(CommandContext<CommandSourceStack> context)
    {
        var core = nearest(context.getSource());
        if (core == null)
            return 0;
        var percent = IntegerArgumentType.getInteger(context, "percent");
        core.setStored(Math.round(core.capacity() * (percent / 100D)));
        context.getSource().sendSuccess(() -> core.status().getFirst(), false);
        return 1;
    }

    private static int bud(CommandContext<CommandSourceStack> context)
    {
        var core = nearest(context.getSource());
        if (core == null)
            return 0;
        var percent = IntegerArgumentType.getInteger(context, "percent");
        core.setBudProgress(Math.round(core.budding().duration() * (percent / 100D)));
        context.getSource().sendSuccess(() -> Component.translatable("voidheart.rubrum_construct.command.bud", percent), false);
        return 1;
    }
}
