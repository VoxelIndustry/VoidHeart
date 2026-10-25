package net.voxelindustry.voidheart.common.command;

import com.mojang.authlib.GameProfile;
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
            Optional<GameProfile> targetProfile = GameProfileArgument.getGameProfiles(context, "name").stream().findFirst();

            if (targetProfile.isPresent())
            {
                ServerLevel voidWorld = context.getSource().getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
                BlockPos pocketPos = VoidPocketState.getVoidPocketState(voidWorld).getPosForPlayer(targetProfile.get().getId());

                Vec3 destinationPos = Vec3.atLowerCornerOf(pocketPos.above()).add(0.5, 0.5, 0.5);
                context.getSource().getPlayer().teleportTo(voidWorld,
                        destinationPos.x(),
                        destinationPos.y(),
                        destinationPos.z(),
                        context.getSource().getPlayer().getYHeadRot(),
                        context.getSource().getPlayer().getViewXRot(0));
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
                .requires(commandSource -> commandSource.hasPermission(2));

        registerPocketCommand(builder);

        dispatcher.register(builder);
    }
}
