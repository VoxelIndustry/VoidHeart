package net.voxelindustry.voidheart.common.energy.reactor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlockItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Startup registrar for W10 Contravoid reactor, called once by EnergySetup.
 */
public final class ContravoidReactorContent
{
    public static Block CONTRAVOID_REACTOR_CORE;
    public static Block REACTOR_CONTAINMENT;
    public static Block REACTOR_INTAKE;

    public static BlockEntityType<ContravoidReactorTile> CONTRAVOID_REACTOR_TILE;
    public static BlockEntityType<ReactorIntakeTile>     REACTOR_INTAKE_TILE;

    private static final List<String> FUELS = List.of("contravoid", "stillness", "fiery", "suffering");

    private ContravoidReactorContent()
    {
    }

    /**
     * As hard and blast proof as obsidian, and never moved by pistons, so only a player's own hand breaches it.
     */
    private static BlockBehaviour.Properties shell(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(5F, 1200F)
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE);
    }

    public static void register()
    {
        CONTRAVOID_REACTOR_CORE = VoidHeartBlocks.registerBlock("contravoid_reactor_core", ContravoidReactorCoreBlock::new,
                shell(MapColor.COLOR_PURPLE).noOcclusion(), true);
        REACTOR_CONTAINMENT = VoidHeartBlocks.registerBlock("reactor_containment", ReactorContainmentBlock::new, shell(MapColor.COLOR_BLACK), true);
        // The storage's item already carries one essence on the stack and lists it in its tooltip
        REACTOR_INTAKE = VoidHeartBlocks.registerBlock("reactor_intake", ReactorIntakeBlock::new, shell(MapColor.COLOR_BLACK), RavenousStorageBlockItem::new);

        VoidHeartTiles.registerTile(CONTRAVOID_REACTOR_TILE = FabricBlockEntityTypeBuilder.create(ContravoidReactorTile::new, CONTRAVOID_REACTOR_CORE).build(),
                "contravoid_reactor_core");
        VoidHeartTiles.registerTile(REACTOR_INTAKE_TILE = FabricBlockEntityTypeBuilder.create(ReactorIntakeTile::new, REACTOR_INTAKE).build(), "reactor_intake");
    }

    /**
     * /voidheart energy reactor fill &lt;pos&gt; &lt;contravoid|stillness|fiery|suffering&gt; &lt;amount&gt; puts fuel straight into the intakes of the
     * reactor at pos (its core, or any block of its shell), for review.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("reactor").then(literal("fill")
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("fuel", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(FUELS, builder))
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ContravoidReactorContent::fill))))));
    }

    private static int fill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        var name = StringArgumentType.getString(context, "fuel");
        var type = typeNamed(name);
        if (type == null)
        {
            source.sendFailure(Component.literal("Reactors take contravoid, stillness, fiery or suffering"));
            return 0;
        }

        var level = source.getLevel();
        var core = level.getBlockEntity(pos) instanceof ContravoidReactorTile tile ? tile : ContravoidReactorTile.coreAround(level, pos);
        if (core == null)
        {
            source.sendFailure(Component.literal("No Contravoid Reactor at " + pos.toShortString()));
            return 0;
        }

        var filled = core.fill(type, IntegerArgumentType.getInteger(context, "amount"));
        if (filled == 0)
        {
            source.sendFailure(Component.literal("Neither intake takes " + name + " now: one must hold Contravoid and the other one essence"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Filled " + filled + " " + name), false);
        return filled;
    }

    private static EssenceType typeNamed(String name)
    {
        return switch (name)
        {
            case "contravoid" -> EssenceRegistry.CONTRAVOID;
            case "stillness" -> EssenceRegistry.STILLNESS;
            case "fiery" -> EssenceRegistry.FIERY;
            case "suffering" -> EssenceRegistry.SUFFERING;
            default -> null;
        };
    }
}
