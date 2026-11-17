package net.voxelindustry.voidheart.common.energy.pearl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /voidheart energy pearl grow &lt;percent&gt; and pearl feed, which skip the hours of growth and the relays on the pearls hung near the player, for
 * review.
 */
final class PearlCommands
{
    private static final int REACH = 8;

    private PearlCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("pearl")
                .then(literal("grow").then(argument("percent", IntegerArgumentType.integer(0, 100)).executes(PearlCommands::grow)))
                .then(literal("feed").executes(PearlCommands::feed)));
    }

    private static List<HangingPearlTile> near(CommandSourceStack source)
    {
        var level = source.getLevel();
        var center = BlockPos.containing(source.getPosition());
        var found = new ArrayList<HangingPearlTile>();
        for (var pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH)))
        {
            if (level.getBlockEntity(pos) instanceof HangingPearlTile pearl)
                found.add(pearl);
        }
        if (found.isEmpty())
            source.sendFailure(Component.translatable("voidheart.hanging_rubrum_pearl.command.none"));
        return found;
    }

    private static int grow(CommandContext<CommandSourceStack> context)
    {
        var pearls = near(context.getSource());
        var percent = IntegerArgumentType.getInteger(context, "percent");
        for (var pearl : pearls)
            pearl.setShare(percent / 100D);
        if (!pearls.isEmpty())
            context.getSource().sendSuccess(() -> Component.translatable("voidheart.hanging_rubrum_pearl.command.grow", pearls.size(), percent), false);
        return pearls.size();
    }

    private static int feed(CommandContext<CommandSourceStack> context)
    {
        var pearls = near(context.getSource());
        for (var pearl : pearls)
            pearl.fillFiery();
        if (!pearls.isEmpty())
            context.getSource().sendSuccess(() -> Component.translatable("voidheart.hanging_rubrum_pearl.command.feed", pearls.size()), false);
        return pearls.size();
    }
}
