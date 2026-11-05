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
import net.voxelindustry.voidheart.common.world.pocket.PocketBlueprint;
import net.voxelindustry.voidheart.common.world.pocket.PocketBuilder;
import net.voxelindustry.voidheart.common.world.pocket.PocketVariant;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartCommands
{
    private static final int PREVIEW_ORIGIN = 4_000_000;
    private static final int PREVIEW_SPACING = 256;
    private static final int PREVIEW_Y = 64;
    private static final int PREVIEW_SEEDS = 16;

    public static void registerPocketCommand(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("pocket")
                .then(argument("name", GameProfileArgument.gameProfile())
                        .executes(VoidHeartCommands::processPocketCommand)));

        builder.then(literal("pocket_preview")
                .then(argument("variant", StringArgumentType.word())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(PocketVariant.values()).map(PocketVariant::getSerializedName), suggestions))
                        .executes(context -> previewPocket(context, 0))
                        .then(argument("seed", IntegerArgumentType.integer(0, PREVIEW_SEEDS - 1))
                                .executes(context -> previewPocket(context, IntegerArgumentType.getInteger(context, "seed"))))));
    }

    /**
     * Builds a throwaway interior far from every real pocket and sends the caller there, to review a variant without creating a world per pocket.
     */
    private static int previewPocket(CommandContext<CommandSourceStack> context, int seed) throws CommandSyntaxException
    {
        var name = StringArgumentType.getString(context, "variant");
        var variant = Arrays.stream(PocketVariant.values()).filter(candidate -> candidate.getSerializedName().equals(name)).findFirst().orElse(null);
        if (variant == null)
        {
            context.getSource().sendFailure(Component.literal("Unknown pocket variant " + name));
            return 0;
        }

        var player = context.getSource().getPlayerOrException();
        var voidWorld = context.getSource().getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
        var origin = new BlockPos(PREVIEW_ORIGIN + variant.ordinal() * PREVIEW_SPACING, PREVIEW_Y, PREVIEW_ORIGIN + seed * PREVIEW_SPACING);
        var blueprint = new PocketBlueprint(variant, voidWorld.getSeed() + seed);

        PocketBuilder.build(voidWorld, origin, blueprint);
        TeleportUtil.teleport(player, voidWorld, Vec3.atBottomCenterOf(origin), player.getYHeadRot(), player.getViewXRot(0));
        context.getSource().sendSuccess(() -> Component.literal("Built a " + name + " pocket"), false);
        return 1;
    }

    public static int processPocketCommand(CommandContext<CommandSourceStack> context)
    {
        try
        {
            Optional<NameAndId> targetProfile = GameProfileArgument.getGameProfiles(context, "name").stream().findFirst();

            if (targetProfile.isPresent())
            {
                ServerLevel voidWorld = context.getSource().getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
                // Looking up the pocket position would reserve a pocket, and the altar then refuses to create the real one
                var heartData = VoidPocketState.getVoidPocketState(voidWorld).getHeartData(targetProfile.get().id());
                if (heartData == null)
                {
                    context.getSource().sendFailure(Component.translatable(MODID + ".no_pocket_for_a_player", targetProfile.get().name()));
                    return 0;
                }

                Vec3 destinationPos = Vec3.atLowerCornerOf(heartData.pocketPos().above()).add(0.5, 0.5, 0.5);
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
        DungeonCommands.register(builder);
        ArmillaryCommands.register(builder);
        LeyCommands.register(builder);
        CurseCommands.register(builder);
        ResearchCommands.register(builder);

        dispatcher.register(builder);
    }
}
