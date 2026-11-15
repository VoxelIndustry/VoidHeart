package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumPillar;
import net.voxelindustry.voidheart.common.content.sanctum.Sanctums;
import net.voxelindustry.voidheart.common.content.sanctum.VeilGate;
import net.voxelindustry.voidheart.common.content.sanctum.VileFleshMass;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumPlan;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Ways to review sanctums without finding a temple: opening one, the door of the nearest temple, and the guardian's end, done again.
 */
final class SanctumCommands
{
    private static final int DOOR_REACH = 24;
    private static final SimpleCommandExceptionType NOT_IN_SANCTUM = new SimpleCommandExceptionType(Component.literal("Not in a sanctum"));
    private static final SimpleCommandExceptionType NO_SANCTUM = new SimpleCommandExceptionType(Component.literal("No sanctum has that number"));
    private static final SimpleCommandExceptionType NO_DOOR = new SimpleCommandExceptionType(Component.literal("No temple mosaic is near"));

    private SanctumCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("sanctum")
                .then(literal("enter")
                        .executes(SanctumCommands::enterNew)
                        .then(argument("index", IntegerArgumentType.integer(0))
                                .executes(SanctumCommands::enter)))
                .then(literal("leave").executes(SanctumCommands::leave))
                .then(literal("door").executes(SanctumCommands::door))
                .then(literal("slay").executes(SanctumCommands::slay))
                .then(literal("reset").executes(SanctumCommands::reset)));
    }

    /** Opens a sanctum of no temple, whose gate leads back here. */
    private static int enterNew(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = Sanctums.state(player.level().getServer()).open(GlobalPos.of(player.level().dimension(), player.blockPosition()), player.getYRot());
        Sanctums.arrive(player, index);
        context.getSource().sendSuccess(() -> Component.literal("Entered sanctum " + index), false);
        return index;
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = IntegerArgumentType.getInteger(context, "index");
        var state = Sanctums.state(player.level().getServer());
        if (state.get(index) == null)
            throw NO_SANCTUM.create();
        Sanctums.arrive(player, index);
        return index;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = Sanctums.indexAt(player.blockPosition());
        if (index < 0)
            throw NOT_IN_SANCTUM.create();
        Sanctums.leave(player, index);
        return 1;
    }

    /** Opens the door of the nearest temple mosaic as a Wraith Veil would. */
    private static int door(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        var level = player.level();
        var around = player.blockPosition();
        MosaicDoor nearest = null;
        for (var pos : BlockPos.betweenClosed(around.offset(-DOOR_REACH, -8, -DOOR_REACH), around.offset(DOOR_REACH, 8, DOOR_REACH)))
        {
            if (!level.getBlockState(pos).is(VoidHeartBlocks.UNDERWAY_MOSAIC))
                continue;
            for (var side : Direction.Plane.HORIZONTAL)
            {
                if (level.getBlockState(pos.relative(side)).isCollisionShapeFullBlock(level, pos.relative(side)))
                    continue;
                var origin = MosaicDoor.mosaicOrigin(level, pos, side);
                if (origin != null)
                {
                    nearest = new MosaicDoor(origin, side);
                    break;
                }
            }
            if (nearest != null)
                break;
        }
        if (nearest == null)
            throw NO_DOOR.create();
        VeilGate.open(level, nearest);
        return 1;
    }

    private static int slay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = Sanctums.indexAt(player.blockPosition());
        if (index < 0)
            throw NOT_IN_SANCTUM.create();
        var level = player.level();
        for (var mass : level.getEntitiesOfClass(VileFleshMass.class, player.getBoundingBox().inflate(SanctumPlan.LENGTH)))
            mass.kill(level);
        return 1;
    }

    /** Takes the pillar down and sets a new guardian where it sat. Shoggoths already slain stay slain. */
    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        int index = Sanctums.indexAt(player.blockPosition());
        if (index < 0)
            throw NOT_IN_SANCTUM.create();
        ServerLevel level = player.level();
        var altar = Sanctums.altar(index);
        for (int stone = 0; stone < SanctumPillar.STONES; stone++)
            level.setBlock(altar.above(stone), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (var mass : level.getEntitiesOfClass(VileFleshMass.class, player.getBoundingBox().inflate(SanctumPlan.LENGTH)))
            mass.discard();
        Sanctums.state(level.getServer()).reset(index);
        var mass = VoidHeartEntities.VILE_FLESH_MASS.create(level, EntitySpawnReason.COMMAND);
        if (mass != null)
        {
            mass.snapTo(altar.getX() + 0.5, altar.getY(), altar.getZ() + 0.5, 180, 0);
            mass.setPersistenceRequired();
            level.addFreshEntity(mass);
        }
        return 1;
    }
}
