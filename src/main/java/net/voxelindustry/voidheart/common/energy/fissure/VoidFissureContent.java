package net.voxelindustry.voidheart.common.energy.fissure;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.energy.VoidWounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for W9 Void Fissure, called once by EnergySetup. The fissure and the wound are world features with no item of their own: a
 * Fissure Seed places the fissure, and only a failed reactor or a command opens a wound.
 */
public final class VoidFissureContent
{
    public static Block                              VOID_FISSURE;
    public static Block                              VOID_WOUND;
    public static Item                               FISSURE_SEED;
    public static BlockEntityType<VoidFissureTile> VOID_FISSURE_TILE;

    private VoidFissureContent()
    {
    }

    public static void register()
    {
        VOID_FISSURE = registerBlock("void_fissure", false);
        VOID_WOUND = registerBlock("void_wound", true);
        FISSURE_SEED = VoidHeartItems.registerItem("fissure_seed", properties -> new BlockItem(VOID_FISSURE, properties));

        VoidHeartTiles.registerTile(VOID_FISSURE_TILE = FabricBlockEntityTypeBuilder.create(VoidFissureTile::new, VOID_FISSURE, VOID_WOUND).build(), "void_fissure");
        VoidFissures.install();
    }

    /**
     * Only Contravoid closes them: they can't be mined, blown up, pushed or replaced, and drop nothing. Their light is drawn, so they give off none,
     * which would have lit the ground around them like a lamp.
     */
    private static Block registerBlock(String name, boolean wound)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, name);
        var properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1, 3600000)
                .noCollision()
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.SCULK)
                .setId(ResourceKey.create(Registries.BLOCK, id));
        return Registry.register(BuiltInRegistries.BLOCK, id, new VoidFissureBlock(wound, properties));
    }

    /**
     * /voidheart energy fissure &lt;pos&gt; info|width|seep|pay|spawn, and /voidheart energy fissure wound &lt;pos&gt; [size], for review: they skip the
     * seep's slow spread, the bundles, the Contravoid and the spawn timer.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("fissure")
                .then(literal("wound").then(argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> wound(context, VoidFissureBalance.WOUND_SIZE))
                        .then(argument("size", FloatArgumentType.floatArg(1, VoidFissureBalance.MAX_WIDTH))
                                .executes(context -> wound(context, FloatArgumentType.getFloat(context, "size"))))))
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(literal("info").executes(VoidFissureContent::info))
                        .then(literal("width").then(argument("width", IntegerArgumentType.integer(1, VoidFissureBalance.MAX_WIDTH)).executes(VoidFissureContent::width)))
                        .then(literal("seep").then(argument("radius", FloatArgumentType.floatArg(0)).executes(VoidFissureContent::seep)))
                        .then(literal("pay").then(argument("amount", IntegerArgumentType.integer(1)).executes(VoidFissureContent::pay)))
                        .then(literal("spawn").executes(VoidFissureContent::spawn))));
    }

    private static int wound(CommandContext<CommandSourceStack> context, float size) throws CommandSyntaxException
    {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        VoidWounds.open(context.getSource().getLevel(), pos, size);
        context.getSource().sendSuccess(() -> Component.literal("Opened a wound of size " + size + " at " + pos.toShortString()), true);
        return 1;
    }

    private static VoidFissureTile fissure(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (context.getSource().getLevel().getBlockEntity(pos) instanceof VoidFissureTile fissure)
            return fissure;
        context.getSource().sendFailure(Component.literal("No Void Fissure or Wound at " + pos.toShortString()));
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var fissure = fissure(context);
        if (fissure == null)
            return 0;
        long now = context.getSource().getLevel().getGameTime();
        context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "%s of width %.1f: offering %d VE/t, seep %.1f of %.1f blocks, %d / %d Contravoid%s",
                fissure.isWound() ? "Wound" : "Fissure", fissure.size(), fissure.lastOutput(), fissure.seepRadius(now), SeepMath.targetRadius(fissure.size()),
                fissure.paid(), fissure.cost(), fissure.isClosing() ? ", closing" : "")), false);
        return 1;
    }

    private static int width(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var fissure = fissure(context);
        if (fissure == null)
            return 0;
        fissure.setWidth(context.getSource().getLevel(), IntegerArgumentType.getInteger(context, "width"));
        return info(context);
    }

    private static int seep(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var fissure = fissure(context);
        if (fissure == null)
            return 0;
        fissure.setSeepRadius(context.getSource().getLevel(), FloatArgumentType.getFloat(context, "radius"));
        return info(context);
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var fissure = fissure(context);
        if (fissure == null)
            return 0;
        var paid = fissure.pay(IntegerArgumentType.getInteger(context, "amount"));
        context.getSource().sendSuccess(() -> Component.literal("Paid " + paid + " Contravoid"), false);
        return paid;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var fissure = fissure(context);
        if (fissure == null)
            return 0;
        var level = context.getSource().getLevel();
        var mob = SeepCreatures.tryBringThrough(level, fissure, level.getGameTime());
        if (mob == null)
            context.getSource().sendFailure(Component.literal("Nothing came through"));
        else
            context.getSource().sendSuccess(() -> Component.literal(mob.getType().getDescription().getString() + " came through"), false);
        return mob == null ? 0 : 1;
    }
}
