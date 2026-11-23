package net.voxelindustry.voidheart.common.energy.superfluid;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * /voidheart energy superfluid rings &lt;shards&gt;, pour &lt;shards&gt; and fill, which skip the pour's minutes for review: wind the mature pearls near
 * the player with that many shards' worth, have the cradles near pour that much at once, or fill the tesseract in hand.
 */
final class SuperfluidCommands
{
    private static final int REACH = 8;

    private SuperfluidCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        var shards = SuperfluidBalance.PEARL_CAPACITY / SuperfluidBalance.SHARD_UNITS;
        energy.then(literal("superfluid")
                .then(literal("rings").then(argument("shards", IntegerArgumentType.integer(0, shards)).executes(SuperfluidCommands::rings)))
                .then(literal("pour").then(argument("shards", IntegerArgumentType.integer(1, shards)).executes(SuperfluidCommands::pour)))
                .then(literal("fill").executes(SuperfluidCommands::fill)));
    }

    private static <T extends BlockEntity> List<T> near(CommandSourceStack source, Class<T> type)
    {
        var level = source.getLevel();
        var center = BlockPos.containing(source.getPosition());
        var found = new ArrayList<T>();
        for (var pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH)))
        {
            if (type.isInstance(level.getBlockEntity(pos)))
                found.add(type.cast(level.getBlockEntity(pos)));
        }
        return found;
    }

    private static int rings(CommandContext<CommandSourceStack> context)
    {
        var shards = IntegerArgumentType.getInteger(context, "shards");
        var pearls = near(context.getSource(), HangingPearlTile.class).stream().filter(pearl -> pearl.growth().isMature()).toList();
        if (pearls.isEmpty())
        {
            context.getSource().sendFailure(Component.translatable(MODID + ".superfluid.command.no_pearl"));
            return 0;
        }
        for (var pearl : pearls)
            pearl.setSuperfluid(shards * SuperfluidBalance.SHARD_UNITS);
        context.getSource().sendSuccess(() -> Component.translatable(MODID + ".superfluid.command.rings", pearls.size(), shards), false);
        return pearls.size();
    }

    private static int pour(CommandContext<CommandSourceStack> context)
    {
        var shards = IntegerArgumentType.getInteger(context, "shards");
        var poured = 0;
        var cradles = 0;
        for (var cradle : near(context.getSource(), CrystalCradleTile.class))
        {
            var units = cradle.pourNow(shards);
            if (units > 0)
            {
                poured += units;
                cradles++;
            }
        }
        if (cradles == 0)
        {
            context.getSource().sendFailure(Component.translatable(MODID + ".superfluid.command.no_cradle"));
            return 0;
        }
        var total = poured / (double) SuperfluidBalance.SHARD_UNITS;
        var count = cradles;
        context.getSource().sendSuccess(() -> Component.translatable(MODID + ".superfluid.command.pour", count, String.format("%.1f", total)), false);
        return cradles;
    }

    private static int fill(CommandContext<CommandSourceStack> context)
    {
        var player = context.getSource().getPlayer();
        var stack = player == null ? null : player.getMainHandItem();
        if (stack == null || !stack.is(SuperfluidContent.TESSERACT))
        {
            context.getSource().sendFailure(Component.translatable(MODID + ".superfluid.command.no_tesseract"));
            return 0;
        }
        TesseractItem.setHeld(stack, SuperfluidBalance.TESSERACT_CAPACITY);
        context.getSource().sendSuccess(() -> Component.translatable(MODID + ".superfluid.command.fill"), false);
        return 1;
    }
}
