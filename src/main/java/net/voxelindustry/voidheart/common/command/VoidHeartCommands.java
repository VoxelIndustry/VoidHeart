package net.voxelindustry.voidheart.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.command.arguments.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartCommands
{
    public static void registerPocketCommand(LiteralArgumentBuilder<ServerCommandSource> builder)
    {
        builder.then(literal("pocket")
                .then(argument("name", GameProfileArgumentType.gameProfile())
                        .executes(VoidHeartCommands::processPocketCommand)));
    }

    public static int processPocketCommand(CommandContext<ServerCommandSource> context)
    {
        try
        {
            Optional<GameProfile> targetProfile = GameProfileArgumentType.getProfileArgument(context, "name").stream().findFirst();

            if (targetProfile.isPresent())
            {
                ServerWorld voidWorld = context.getSource().getMinecraftServer().getWorld(VoidHeart.VOID_WORLD_KEY);
                BlockPos pocketPos = VoidPocketState.getVoidPocketState(voidWorld).getPosForPlayer(targetProfile.get().getId());

                FabricDimensions.teleport(context.getSource().getPlayer(), voidWorld, (entity, newWorld, direction, offsetX, offsetY) ->
                        new BlockPattern.TeleportTarget(Vec3d.of(pocketPos.up()).add(0.5, 0.5, 0.5), Vec3d.ZERO, 0));
            }
        } catch (CommandSyntaxException e)
        {
            e.printStackTrace();
        }
        return 0;
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated)
    {
        LiteralArgumentBuilder<ServerCommandSource> builder = literal(MODID)
                .requires(commandSource -> commandSource.hasPermissionLevel(2));

        registerPocketCommand(builder);

        dispatcher.register(builder);
    }
}
