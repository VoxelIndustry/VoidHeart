package net.voxelindustry.voidheart.common.energy.engine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlockItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Arrays;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Startup registrar for W5 Ravenous engine, called once by EnergySetup.
 */
public final class RavenousEngineContent
{
    public static Block                                 RAVENOUS_ENGINE;
    public static BlockEntityType<RavenousEngineTile> RAVENOUS_ENGINE_TILE;

    private RavenousEngineContent()
    {
    }

    public static void register()
    {
        // The storage's item already keeps several aspects on the stack and lists them in its tooltip
        RAVENOUS_ENGINE = VoidHeartBlocks.registerBlock("ravenous_engine", RavenousEngineBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(1.5F, 6.0F)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .sound(SoundType.STONE), RavenousStorageBlockItem::new);

        VoidHeartTiles.registerTile(RAVENOUS_ENGINE_TILE = FabricBlockEntityTypeBuilder.create(RavenousEngineTile::new, RAVENOUS_ENGINE).build(), "ravenous_engine");
    }

    /**
     * /voidheart energy engine fill &lt;pos&gt; &lt;aspect&gt; &lt;amount&gt; puts essence straight into an engine's tank, for review.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("engine").then(literal("fill")
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("aspect", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(EngineFuel.values()).map(fuel -> fuel.name().toLowerCase(Locale.ROOT)), builder))
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(RavenousEngineContent::fill))))));
    }

    private static int fill(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        var source = context.getSource();
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        var aspect = StringArgumentType.getString(context, "aspect");
        var fuel = Arrays.stream(EngineFuel.values()).filter(candidate -> candidate.name().equalsIgnoreCase(aspect)).findFirst().orElse(null);
        if (fuel == null)
        {
            source.sendFailure(Component.literal("Engines burn stillness, fiery or suffering"));
            return 0;
        }

        var tile = source.getLevel().getBlockEntity(pos);
        if (!(tile instanceof RavenousEngineTile) && source.getLevel().getBlockEntity(pos.below()) instanceof RavenousEngineTile below)
            tile = below;
        if (!(tile instanceof RavenousEngineTile engine))
        {
            source.sendFailure(Component.literal("No Ravenous Engine at " + pos.toShortString()));
            return 0;
        }

        var received = engine.receive(RavenousEngineTile.essenceOf(fuel), IntegerArgumentType.getInteger(context, "amount"));
        source.sendSuccess(() -> Component.literal("Filled " + received + " " + fuel.name().toLowerCase(Locale.ROOT)), false);
        return received;
    }
}
