package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalKey;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.PocketBlueprint;
import net.voxelindustry.voidheart.common.world.pocket.PocketBuilder;
import net.voxelindustry.voidheart.common.world.pocket.PocketGrid;
import net.voxelindustry.voidheart.common.world.pocket.PocketPalette;
import net.voxelindustry.voidheart.common.world.pocket.PocketVariant;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Reviewing and repairing players' pockets: finding them, making one without the altar, rebuilding or wiping one, and the flexion and portals its heart keeps.
 */
final class PocketCommands
{
    private static final int PREVIEW_ORIGIN = 4_000_000;
    private static final int PREVIEW_SPACING = 256;
    private static final int PREVIEW_Y = 64;
    private static final int PREVIEW_SEEDS = 16;
    /** The grid sits {@code 6 - heartRise} above the heart, so this reaches the floor of any variant's grid. */
    private static final int CENTER_ABOVE_HEART = 6;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SUPPRESS_DROPS;

    private static final SimpleCommandExceptionType NO_PLAYER = new SimpleCommandExceptionType(Component.literal("No such player"));
    private static final SimpleCommandExceptionType NOT_IN_POCKET = new SimpleCommandExceptionType(Component.literal("Not in a pocket, and no player given"));
    private static final DynamicCommandExceptionType NO_POCKET = new DynamicCommandExceptionType(name -> Component.literal(name + " has no pocket"));
    private static final DynamicCommandExceptionType HAS_POCKET = new DynamicCommandExceptionType(name -> Component.literal(name + " already has a pocket"));
    private static final DynamicCommandExceptionType UNKNOWN_VARIANT = new DynamicCommandExceptionType(name -> Component.literal("Unknown pocket variant " + name));

    private PocketCommands()
    {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("pocket")
                .then(literal("list").executes(PocketCommands::list))
                .then(literal("info")
                        .executes(context -> info(context, null))
                        .then(player().executes(context -> info(context, owner(context)))))
                .then(literal("tp").then(player().executes(PocketCommands::teleport)))
                .then(literal("leave").executes(PocketCommands::leave))
                .then(literal("create").then(player()
                        .executes(context -> create(context, null))
                        .then(variant().executes(context -> create(context, variantArgument(context))))))
                .then(literal("rebuild").then(player()
                        .executes(context -> rebuild(context, null))
                        .then(variant().executes(context -> rebuild(context, variantArgument(context))))))
                .then(literal("delete").then(player()
                        .executes(context -> delete(context, false))
                        .then(literal("confirm").executes(context -> delete(context, true)))))
                .then(literal("heart").then(player().executes(PocketCommands::heart)))
                .then(literal("flexion").then(player()
                        .then(argument("max", IntegerArgumentType.integer(0, 64)).executes(PocketCommands::flexion))))
                .then(literal("portals").then(player()
                        .executes(PocketCommands::portals)
                        .then(literal("prune").executes(PocketCommands::prune))))
                .then(literal("preview").then(variant()
                        .executes(context -> preview(context, 0))
                        .then(argument("seed", IntegerArgumentType.integer(0, PREVIEW_SEEDS - 1))
                                .executes(context -> preview(context, IntegerArgumentType.getInteger(context, "seed"))))))
                // The first form, kept for muscle memory: /voidheart pocket <player> teleports there
                .then(player().executes(PocketCommands::teleport)));

        builder.then(literal("pocket_preview").then(variant()
                .executes(context -> preview(context, 0))
                .then(argument("seed", IntegerArgumentType.integer(0, PREVIEW_SEEDS - 1))
                        .executes(context -> preview(context, IntegerArgumentType.getInteger(context, "seed"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, GameProfileArgument.Result> player()
    {
        return argument("player", GameProfileArgument.gameProfile());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> variant()
    {
        return argument("variant", StringArgumentType.word())
                .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(Arrays.stream(PocketVariant.values()).map(PocketVariant::getSerializedName), suggestions));
    }

    private static PocketVariant variantArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var name = StringArgumentType.getString(context, "variant");
        return Arrays.stream(PocketVariant.values())
                .filter(candidate -> candidate.getSerializedName().equals(name))
                .findFirst()
                .orElseThrow(() -> UNKNOWN_VARIANT.create(name));
    }

    private static NameAndId owner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return GameProfileArgument.getGameProfiles(context, "player").stream().findFirst().orElseThrow(NO_PLAYER::create);
    }

    private static ServerLevel voidWorld(CommandSourceStack source)
    {
        return source.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);
    }

    private static VoidPocketState state(CommandSourceStack source)
    {
        return VoidPocketState.getVoidPocketState(voidWorld(source));
    }

    /** Never looks up the pocket position, which would reserve a pocket and make the altar refuse to create the real one. */
    private static VoidHeartData heartData(CommandSourceStack source, NameAndId owner) throws CommandSyntaxException
    {
        var heartData = state(source).getHeartData(owner.id());
        if (heartData == null)
            throw NO_POCKET.create(owner.name());
        return heartData;
    }

    private static String nameOf(CommandSourceStack source, UUID owner)
    {
        return ProfileUtil.cachedName(source.getServer(), owner).orElse(owner.toString());
    }

    private static Vec3 arrival(VoidHeartData heartData)
    {
        return Vec3.atLowerCornerOf(heartData.pocketPos().above()).add(0.5, 0.5, 0.5);
    }

    private static MutableComponent clickable(String text, String command)
    {
        return Component.literal(text).withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static int list(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var pockets = new ArrayList<>(state(source).pockets().entrySet());
        pockets.sort(Map.Entry.comparingByKey());
        source.sendSuccess(() -> Component.literal(pockets.size() + " pocket(s)"), false);
        for (var entry : pockets)
        {
            var name = nameOf(source, entry.getKey());
            var heartData = entry.getValue();
            var line = Component.literal(name + " ")
                    .append(clickable("[" + heartData.pocketPos().toShortString() + "]", "/voidheart pocket tp " + name))
                    .append(Component.literal(" " + heartData.variant().map(PocketVariant::getSerializedName).orElse("unknown variant")
                            + ", flexion " + heartData.currentFlexionCount() + "/" + heartData.maxFlexion()
                            + ", " + heartData.portalsCount() + " portal(s)"));
            source.sendSuccess(() -> line, false);
        }
        return pockets.size();
    }

    /**
     * @param owner the pocket's owner, or null for the pocket the caller stands in, then the caller's own
     */
    private static int info(CommandContext<CommandSourceStack> context, @Nullable NameAndId owner) throws CommandSyntaxException
    {
        var source = context.getSource();
        var state = state(source);
        UUID ownerID;
        if (owner != null)
            ownerID = owner.id();
        else
        {
            var player = source.getPlayerOrException();
            ownerID = player.level().dimension() == VoidHeart.VOID_WORLD_KEY ? state.ownerAt(player.blockPosition()).orElse(null) : null;
            if (ownerID == null && state.hasPocket(player.getUUID()))
                ownerID = player.getUUID();
            if (ownerID == null)
                throw NOT_IN_POCKET.create();
        }

        var heartData = state.getHeartData(ownerID);
        if (heartData == null)
            throw NO_POCKET.create(owner != null ? owner.name() : nameOf(source, ownerID));

        var level = voidWorld(source);
        var heartPos = heartData.pocketPos();
        var text = Component.literal("§7====== §3Pocket of " + nameOf(source, ownerID) + " §7======\n");
        text.append(Component.literal("§6Owner: §7" + ownerID + "\n"));
        text.append(heartData.debugPrint());
        if (heartData.variant().isEmpty())
            text.append(Component.literal("§6Biome suggests: §3" + guessVariant(level, heartPos).map(PocketVariant::getSerializedName).orElse("none") + "\n"));
        text.append(Component.literal("§6Heart rise: §3" + heartData.heartRise() + "\n"));
        if (level.isLoaded(heartPos))
        {
            var heartOwner = level.getBlockEntity(heartPos) instanceof VoidHeartTile heart ? heart.getPlayerID() : null;
            if (heartOwner == null)
                text.append(Component.literal("§cNo heart at its position, /voidheart pocket heart puts one back\n"));
            else if (!heartOwner.equals(ownerID))
                text.append(Component.literal("§cThe heart there belongs to " + heartOwner + "\n"));
            else
                text.append(Component.literal("§2Heart in place\n"));
        }
        else
            text.append(Component.literal("§7Heart not loaded\n"));
        var inside = level.getPlayers(player -> heartData.contains(player.blockPosition())).stream().map(Player::getPlainTextName).toList();
        text.append(Component.literal("§6Inside: §3" + (inside.isEmpty() ? "nobody" : String.join(", ", inside))));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static Optional<PocketVariant> guessVariant(ServerLevel level, BlockPos heart)
    {
        var biome = level.getBiome(heart);
        return Arrays.stream(PocketVariant.values()).filter(variant -> biome.is(PocketPalette.biome(variant))).findFirst();
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var heartData = heartData(context.getSource(), owner(context));
        var player = context.getSource().getPlayerOrException();
        TeleportUtil.teleport(player, voidWorld(context.getSource()), arrival(heartData), player.getYHeadRot(), player.getViewXRot(0));
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayerOrException();
        player.teleport(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
        return 1;
    }

    /** Makes a pocket as the altar would, without the pearls it hands out. */
    private static int create(CommandContext<CommandSourceStack> context, @Nullable PocketVariant variant) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var state = state(source);
        if (state.hasPocket(owner.id()))
            throw HAS_POCKET.create(owner.name());
        state.createPocket(voidWorld(source), owner.id(), variant);
        var heartData = state.getHeartData(owner.id());
        source.sendSuccess(() -> Component.literal("Made a " + heartData.variant().map(PocketVariant::getSerializedName).orElse("") + " pocket for " + owner.name() + " at " + heartData.pocketPos().toShortString()), true);
        return 1;
    }

    /**
     * Wipes everything inside the pocket and builds it again, keeping its heart, flexion and portals. Without a variant it keeps its own, or the one its biome suggests.
     */
    private static int rebuild(CommandContext<CommandSourceStack> context, @Nullable PocketVariant variant) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        var level = voidWorld(source);
        var heart = heartData.pocketPos();

        if (variant == null)
            variant = heartData.variant().or(() -> guessVariant(level, heart)).orElseThrow(() -> UNKNOWN_VARIANT.create("(none saved, give one)"));

        var blueprint = new PocketBlueprint(variant, level.getSeed() * 31 + heart.asLong());
        int newRise = variant.profile().heartRise();
        clear(level, heart, Math.max(heartData.heartRise(), newRise));

        heartData.heartRise(newRise);
        heartData.variant(variant);
        VoidPocketState.placeHeart(level, heart, owner.id());
        PocketBuilder.build(level, heart, blueprint);

        for (var player : level.getPlayers(player -> heartData.contains(player.blockPosition())))
            TeleportUtil.teleport(player, level, arrival(heartData), player.getYHeadRot(), player.getViewXRot(0));

        var name = variant.getSerializedName();
        source.sendSuccess(() -> Component.literal("Rebuilt the pocket of " + owner.name() + " as " + name), true);
        return 1;
    }

    /**
     * Wipes the pocket's blocks and forgets it, so the altar can make a new one. Its hoard and reserve are kept by owner, and stay.
     */
    private static int delete(CommandContext<CommandSourceStack> context, boolean confirmed) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        if (!confirmed)
        {
            source.sendSuccess(() -> Component.literal("This wipes every block in the pocket of " + owner.name() + " at " + heartData.pocketPos().toShortString()
                            + ", contents included, and forgets it. Its hoard stays. ")
                    .append(clickable("[Confirm]", "/voidheart pocket delete " + owner.name() + " confirm")), false);
            return 0;
        }

        var level = voidWorld(source);
        for (ServerPlayer player : level.getPlayers(player -> heartData.contains(player.blockPosition())))
            player.teleport(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
        clear(level, heartData.pocketPos(), heartData.heartRise());
        level.setBlock(heartData.pocketPos(), Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
        state(source).removePocket(owner.id());
        source.sendSuccess(() -> Component.literal("Deleted the pocket of " + owner.name()), true);
        return 1;
    }

    /** Puts the heart back at the pocket's position, owned by its owner, when it went missing or belongs to someone else. */
    private static int heart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        VoidPocketState.placeHeart(voidWorld(source), heartData.pocketPos(), owner.id());
        source.sendSuccess(() -> Component.literal("Set the heart of " + owner.name() + " at " + heartData.pocketPos().toShortString()), true);
        return 1;
    }

    private static int flexion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        int max = IntegerArgumentType.getInteger(context, "max");
        heartData.maxFlexion(max);
        state(source).setDirty();
        source.sendSuccess(() -> Component.literal("Max flexion of " + owner.name() + " is now " + max + ", " + heartData.currentFlexionCount() + " used"), true);
        return max;
    }

    private static int portals(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        source.sendSuccess(() -> Component.literal(owner.name() + " holds " + heartData.portalsCount() + " portal(s)"), false);
        for (var key : heartData.portalKeySet())
        {
            var pos = key.corePos();
            var dimension = key.worldKey().identifier();
            var line = Component.literal(dimension + " ")
                    .append(clickable("[" + pos.toShortString() + "]", "/execute in " + dimension + " run tp @s " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ()))
                    .append(Component.literal(" " + (isLive(source, key) ? "§2linked" : "§cstale")));
            source.sendSuccess(() -> line, false);
        }
        return heartData.portalsCount();
    }

    /** Forgets portals whose core is gone or no longer linked, which free flexion they still hold. */
    private static int prune(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var owner = owner(context);
        var heartData = heartData(source, owner);
        var stale = heartData.portalKeySet().stream().filter(key -> !isLive(source, key)).toList();
        stale.forEach(heartData::removePortal);
        state(source).setDirty();
        source.sendSuccess(() -> Component.literal("Forgot " + stale.size() + " stale portal(s) of " + owner.name()), true);
        return stale.size();
    }

    /** Loads the core's chunk if it must. */
    private static boolean isLive(CommandSourceStack source, PortalKey key)
    {
        var level = source.getServer().getLevel(key.worldKey());
        return level != null && level.getBlockEntity(key.corePos()) instanceof PortalFrameCoreTile core && core.isLinked();
    }

    /**
     * Builds a throwaway interior far from every real pocket and sends the caller there, to review a variant without creating a world per pocket.
     */
    private static int preview(CommandContext<CommandSourceStack> context, int seed) throws CommandSyntaxException
    {
        var variant = variantArgument(context);
        var player = context.getSource().getPlayerOrException();
        var level = voidWorld(context.getSource());
        var origin = new BlockPos(PREVIEW_ORIGIN + variant.ordinal() * PREVIEW_SPACING, PREVIEW_Y, PREVIEW_ORIGIN + seed * PREVIEW_SPACING);

        PocketBuilder.build(level, origin, new PocketBlueprint(variant, level.getSeed() + seed));
        TeleportUtil.teleport(player, level, Vec3.atBottomCenterOf(origin), player.getYHeadRot(), player.getViewXRot(0));
        context.getSource().sendSuccess(() -> Component.literal("Built a " + variant.getSerializedName() + " pocket"), false);
        return 1;
    }

    /**
     * Empties the pocket's grid around its heart, which stays, and removes what lay or roamed in it except players.
     *
     * @param rise the highest heart rise to clear for, since the grid sits lower the higher the heart rises
     */
    private static void clear(ServerLevel level, BlockPos heart, int rise)
    {
        int bottom = CENTER_ABOVE_HEART - rise - PocketGrid.WALL_REACH;
        int top = CENTER_ABOVE_HEART + PocketGrid.WALL_REACH;
        var air = Blocks.AIR.defaultBlockState();
        var cursor = new BlockPos.MutableBlockPos();
        for (int y = bottom; y <= top; y++)
        {
            for (int z = -PocketGrid.WALL_REACH; z <= PocketGrid.WALL_REACH; z++)
            {
                for (int x = -PocketGrid.WALL_REACH; x <= PocketGrid.WALL_REACH; x++)
                {
                    if (x == 0 && y == 0 && z == 0)
                        continue;
                    cursor.set(heart.getX() + x, heart.getY() + y, heart.getZ() + z);
                    if (!level.getBlockState(cursor).isAir())
                        level.setBlock(cursor, air, CLEAR_FLAGS);
                }
            }
        }
        var box = new AABB(heart.getX() - PocketGrid.WALL_REACH, heart.getY() + bottom, heart.getZ() - PocketGrid.WALL_REACH,
                heart.getX() + PocketGrid.WALL_REACH + 1, heart.getY() + top + 1, heart.getZ() + PocketGrid.WALL_REACH + 1);
        level.getEntities((Entity) null, box, entity -> !(entity instanceof Player)).forEach(Entity::discard);
    }
}
