package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.voxelindustry.voidheart.common.content.mausoleum.Dullahan;
import net.voxelindustry.voidheart.common.content.mausoleum.DullahanCrown;
import net.voxelindustry.voidheart.common.content.mausoleum.MaleficentWisp;
import net.voxelindustry.voidheart.common.content.mausoleum.Mausoleums;
import net.voxelindustry.voidheart.common.content.mausoleum.Sarcophagi;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumPlan;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Ways to review mausoleums without finding a shroud gate: opening one, the rider's flames and its end, and the fight run again.
 */
final class MausoleumCommands
{
    private static final SimpleCommandExceptionType NOT_IN_MAUSOLEUM = new SimpleCommandExceptionType(Component.literal("Not in a mausoleum"));
    private static final SimpleCommandExceptionType NO_MAUSOLEUM = new SimpleCommandExceptionType(Component.literal("No mausoleum has that number"));
    private static final SimpleCommandExceptionType NO_RIDER = new SimpleCommandExceptionType(Component.literal("No Dullahan rides here"));

    private MausoleumCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("mausoleum")
                .then(literal("enter")
                        .executes(MausoleumCommands::enterNew)
                        .then(argument("index", IntegerArgumentType.integer(0))
                                .executes(MausoleumCommands::enter)))
                .then(literal("leave").executes(MausoleumCommands::leave))
                .then(literal("flame").executes(MausoleumCommands::flame))
                .then(literal("slay").executes(MausoleumCommands::slay))
                .then(literal("reset").executes(MausoleumCommands::reset)));
    }

    /** Opens a mausoleum of no gate, whose own gate leads back here. */
    private static int enterNew(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = Mausoleums.state(player.level().getServer()).open(GlobalPos.of(player.level().dimension(), player.blockPosition()), player.getYRot());
        Mausoleums.arrive(player, index);
        context.getSource().sendSuccess(() -> Component.literal("Entered mausoleum " + index), false);
        return index;
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (Mausoleums.state(player.level().getServer()).get(index) == null)
            throw NO_MAUSOLEUM.create();
        Mausoleums.arrive(player, index);
        return index;
    }

    /** @return the mausoleum the player stands in, which needs the dungeons' dimension as well as a mausoleum's cell */
    private static int mausoleumOf(ServerPlayer player) throws CommandSyntaxException
    {
        int index = player.level().dimension() == VoidDungeons.WORLD_KEY ? Mausoleums.indexAt(player.blockPosition()) : -1;
        if (index < 0)
            throw NOT_IN_MAUSOLEUM.create();
        return index;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        Mausoleums.leave(player, mausoleumOf(player));
        return 1;
    }

    private static List<Dullahan> riders(ServerPlayer player)
    {
        return player.level().getEntitiesOfClass(Dullahan.class, player.getBoundingBox().inflate(MausoleumPlan.LENGTH), Dullahan::isAlive);
    }

    /** Lets the next flame of the crown go, as if the rider had lost the share of health that holds it. */
    private static int flame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        mausoleumOf(player);
        var riders = riders(player);
        if (riders.isEmpty())
            throw NO_RIDER.create();
        for (var rider : riders)
        {
            float floor = DullahanCrown.threshold(rider.released()) * rider.getMaxHealth();
            rider.setHealth(Math.max(1, floor));
            rider.release(player.level());
        }
        return 1;
    }

    private static int slay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        mausoleumOf(player);
        ServerLevel level = player.level();
        for (var rider : riders(player))
        {
            rider.putOutWisps(level);
            rider.kill(level);
        }
        return 1;
    }

    /** Lays the lid back and sets a new rider at its post. Everything the fight brought out is put away. */
    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = mausoleumOf(player);
        ServerLevel level = player.level();
        var box = player.getBoundingBox().inflate(MausoleumPlan.LENGTH);
        for (var rider : level.getEntitiesOfClass(Dullahan.class, box))
        {
            rider.putOutWisps(level);
            rider.discard();
        }
        for (var wisp : level.getEntitiesOfClass(MaleficentWisp.class, box))
            wisp.discard();
        for (var wraith : level.getEntitiesOfClass(Wraith.class, box, wraith -> Mausoleums.indexAt(wraith.blockPosition()) == index))
            wraith.discard();
        Sarcophagi.close(level, index);
        Mausoleums.state(level.getServer()).reset(index);

        var rider = VoidHeartEntities.DULLAHAN.create(level, EntitySpawnReason.COMMAND);
        if (rider != null)
        {
            var post = Mausoleums.local(index, MausoleumPlan.RIDER_X, MausoleumPlan.RIDER_Y, MausoleumPlan.RIDER_Z);
            rider.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, MausoleumPlan.RIDER_YAW, 0);
            rider.setYBodyRot(MausoleumPlan.RIDER_YAW);
            rider.finalizeSpawn(level, level.getCurrentDifficultyAt(post), EntitySpawnReason.COMMAND, null);
            level.addFreshEntity(rider);
        }
        return 1;
    }
}
