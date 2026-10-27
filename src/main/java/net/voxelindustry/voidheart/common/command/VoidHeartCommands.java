package net.voxelindustry.voidheart.common.command;

import net.minecraft.commands.Commands;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.minecraft.server.players.NameAndId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartCommands
{
    public static void registerPocketCommand(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("pocket")
                .then(argument("name", GameProfileArgument.gameProfile())
                        .executes(VoidHeartCommands::processPocketCommand)));
    }

    public static int processPocketCommand(CommandContext<CommandSourceStack> context)
    {
        try
        {
            Optional<NameAndId> targetProfile = GameProfileArgument.getGameProfiles(context, "name").stream().findFirst();

            if (targetProfile.isPresent())
            {
                ServerLevel voidWorld = context.getSource().getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
                BlockPos pocketPos = VoidPocketState.getVoidPocketState(voidWorld).getPosForPlayer(targetProfile.get().id());

                Vec3 destinationPos = Vec3.atLowerCornerOf(pocketPos.above()).add(0.5, 0.5, 0.5);
                var player = context.getSource().getPlayerOrException();
                TeleportUtil.teleport(player, voidWorld, destinationPos, player.getYHeadRot(), player.getViewXRot(0));
            }
        } catch (CommandSyntaxException e)
        {
            e.printStackTrace();
        }
        return 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess, CommandSelection environment)
    {
        LiteralArgumentBuilder<CommandSourceStack> builder = literal(MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        registerPocketCommand(builder);
        LeyCommands.register(builder);

        dispatcher.register(builder);
    }
}
