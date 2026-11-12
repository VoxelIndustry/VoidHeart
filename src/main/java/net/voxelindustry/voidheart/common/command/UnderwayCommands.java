package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.HatchBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.content.underway.UnderwaySecurity;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayNetwork;
import net.voxelindustry.voidheart.common.world.underway.UnderwayNode;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * A way into the Underway for review, until its hatches can be opened.
 */
final class UnderwayCommands
{
    private static final int SEARCH_RADIUS = 8;
    private static final int KEY_REACH = 48;
    private static final DynamicCommandExceptionType UNKNOWN_KIND = new DynamicCommandExceptionType(name -> Component.literal("No kind of fort is called " + name));

    private UnderwayCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("underway")
                .then(literal("enter")
                        .executes(context -> enter(context, null))
                        .then(argument("kind", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(UnderwayNode.values()).map(node -> node.name().toLowerCase(Locale.ROOT)), suggestions))
                                .executes(context -> enter(context, kind(context)))))
                .then(literal("hatch")
                        .executes(context -> hatch(context, null))
                        .then(argument("kind", StringArgumentType.word())
                                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(UnderwayNode.values()).map(node -> node.name().toLowerCase(Locale.ROOT)), suggestions))
                                .executes(context -> hatch(context, kind(context)))))
                .then(literal("charge")
                        .then(argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(UnderwayCommands::charge)))
                .then(literal("wave").executes(UnderwayCommands::wave))
                .then(literal("section")
                        .executes(context -> section(context, "status"))
                        .then(literal("live").executes(context -> section(context, "live")))
                        .then(literal("dark").executes(context -> section(context, "dark")))
                        .then(literal("alarm").executes(context -> section(context, "alarm")))
                        .then(literal("reset").executes(context -> section(context, "reset"))))
                .then(literal("where").executes(UnderwayCommands::where)));
    }

    private static UnderwayNode kind(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        String name = StringArgumentType.getString(context, "kind");
        for (var node : UnderwayNode.values())
        {
            if (node.name().equalsIgnoreCase(name))
                return node;
        }
        throw UNKNOWN_KIND.create(name);
    }

    /** Sends the player to the foot of the hatch stairs of the nearest fort of that kind, or of the one under their feet. */
    private static int enter(CommandContext<CommandSourceStack> context, UnderwayNode kind) throws CommandSyntaxException
    {
        return send(context, kind, false);
    }

    /** Sends the player onto the hatch of the nearest fort of that kind, in the district over it. */
    private static int hatch(CommandContext<CommandSourceStack> context, UnderwayNode kind) throws CommandSyntaxException
    {
        return send(context, kind, true);
    }

    private static int send(CommandContext<CommandSourceStack> context, UnderwayNode kind, boolean above) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var level = VoidDaedalus.level(context.getSource().getServer());
        long seed = level.getSeed();
        var from = player.level().dimension() == VoidDaedalus.WORLD_KEY ? DistrictPos.at(player.getBlockX(), player.getBlockZ()) : new DistrictPos(0, 0);
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                for (int x = -radius; x <= radius; x++)
                {
                    var pos = new DistrictPos(from.x() + x, from.z() + z);
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius || kind != null && UnderwayNetwork.nodeAt(seed, pos) != kind)
                        continue;
                    var fort = UnderwayPlans.fort(seed, pos);
                    var foot = new Vec3(fort.frame().x(fort.footAlong(), fort.footAcross()) + 0.5, UnderwayLevels.ENTRY_FLOOR + 1, fort.frame().z(fort.footAlong(), fort.footAcross()) + 0.5);
                    var hatch = new Vec3(fort.hatchX() + DistrictPos.CELL / 2.0, DistrictPos.floorY(0) + 1, fort.hatchZ() + DistrictPos.CELL / 2.0);
                    if (!above && level.getBlockState(BlockPos.containing(foot).below()).isAir())
                    {
                        context.getSource().sendFailure(Component.literal("District " + pos.x() + ", " + pos.z() + " was generated before the Underway and has nothing under it"));
                        return 0;
                    }
                    TeleportUtil.teleport(player, level, above ? hatch : foot, player.getYRot(), 0);
                    context.getSource().sendSuccess(() -> describe(seed, pos), false);
                    return 1;
                }
            }
        }
        context.getSource().sendFailure(Component.literal("No such fort within " + SEARCH_RADIUS + " districts"));
        return 0;
    }

    /** Sets how far the hatch has charged through the key nearest the player, to skip the wait. */
    private static int charge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var keys = keysByDistance(player);
        if (keys.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("No key at work within " + KEY_REACH + " blocks"));
            return 0;
        }
        int percent = IntegerArgumentType.getInteger(context, "percent");
        keys.getFirst().skipTo(HatchBalance.CHARGE_TICKS * percent / 100);
        context.getSource().sendSuccess(() -> Component.literal("Hatch charged to " + percent + "%"), false);
        return 1;
    }

    /** Brings the next wave on the key nearest the player now. */
    private static int wave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var keys = keysByDistance(player);
        if (keys.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("No key at work within " + KEY_REACH + " blocks"));
            return 0;
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL)
        {
            context.getSource().sendFailure(Component.literal("No wave comes in peaceful"));
            return 0;
        }
        keys.getFirst().callWave();
        context.getSource().sendSuccess(() -> Component.literal("Wave " + keys.getFirst().wavesSent() + " called"), false);
        return 1;
    }

    /** Reads the section the player stands in, or skips the cell and the eyes: sets it live on a full cell, takes the cell out, sounds its alarm, or forgets what happened to it. */
    private static int section(CommandContext<CommandSourceStack> context, String action) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var level = context.getSource().getLevel();
        var security = UnderwaySecurity.of(level);
        var key = UnderwaySecurity.Key.at(player.blockPosition());
        if (security == null || key == null)
        {
            context.getSource().sendFailure(Component.literal("Not inside a fort of the Underway"));
            return 0;
        }
        switch (action)
        {
            case "live" ->
            {
                if (security.hasCell(key))
                    security.removeCell(level, key);
                security.insertCell(level, key, RubrumBalance.CELL_CAPACITY);
            }
            case "dark" -> security.removeCell(level, key);
            case "alarm" -> security.raiseAlarm(level, key);
            case "reset" -> security.reset(level, key);
            default ->
            {
            }
        }
        int units = UnderwayPlans.fort(level.getSeed(), key.district()).unitCount(key.section());
        var text = key.section().name().toLowerCase(Locale.ROOT) + " section under district " + key.x() + ", " + key.z() + ": "
                + (security.isLive(level, key) ? "live" : "dormant") + (security.hasCell(key) ? ", cell at " + security.charge(key) * 100 / RubrumBalance.CELL_CAPACITY + "%" : ", no cell")
                + (security.isAlarmed(key) ? ", alarm sounding" : "") + ", " + security.destroyedCount(key) + " of " + units + " units destroyed"
                + (security.isSecured(level, key) ? ", secured" : "");
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** @return the keys at work around the player, the nearest first */
    private static List<UnderwayKeyEntity> keysByDistance(ServerPlayer player)
    {
        var keys = player.level().getEntitiesOfClass(UnderwayKeyEntity.class, player.getBoundingBox().inflate(KEY_REACH));
        keys.sort(Comparator.comparingDouble(player::distanceToSqr));
        return keys;
    }

    private static int where(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        if (player.level().dimension() != VoidDaedalus.WORLD_KEY)
        {
            context.getSource().sendFailure(Component.literal("Not inside the Daedalus"));
            return 0;
        }
        long seed = VoidDaedalus.level(context.getSource().getServer()).getSeed();
        var pos = DistrictPos.at(player.getBlockX(), player.getBlockZ());
        context.getSource().sendSuccess(() -> describe(seed, pos), false);
        return 1;
    }

    private static Component describe(long seed, DistrictPos pos)
    {
        var text = new StringBuilder(UnderwayNetwork.ruinAt(seed, pos).name().toLowerCase(Locale.ROOT)).append(' ')
                .append(UnderwayNetwork.nodeAt(seed, pos).name().toLowerCase(Locale.ROOT)).append(" under district ").append(pos.x()).append(", ").append(pos.z());
        var station = UnderwayNetwork.stationOf(seed, pos);
        if (!station.equals(pos))
            text.append(", its gallery leads to ").append(station.x()).append(", ").append(station.z());
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            var link = UnderwayNetwork.linkFrom(seed, pos, direction);
            if (link.canBeWalked() || link == net.voxelindustry.voidheart.common.world.underway.UnderwayLink.GATED)
                text.append(", ").append(new String[] { "north", "east", "south", "west" }[direction]).append(' ').append(link.name().toLowerCase(Locale.ROOT));
        }
        return Component.literal(text.toString());
    }
}
