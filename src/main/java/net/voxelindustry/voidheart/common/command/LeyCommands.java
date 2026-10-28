package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.ley.LeyDebugView;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeDump;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchor;
import net.voxelindustry.voidheart.common.ley.anchor.OldAnchors;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.logging.Level;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

public class LeyCommands
{
    private static final int DEFAULT_DUMP_RADIUS = 4096;
    private static final int DUMP_PIXELS = 2048;
    private static final int ANCHOR_SEARCH_RADIUS = 2048;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        builder.then(literal("ley")
                .then(literal("info").executes(LeyCommands::info))
                .then(literal("show").executes(LeyCommands::show))
                .then(literal("anchor").executes(LeyCommands::anchor))
                .then(literal("dump")
                        .executes(context -> dump(context, DEFAULT_DUMP_RADIUS))
                        .then(argument("radius", IntegerArgumentType.integer(256, 16384))
                                .executes(context -> dump(context, IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int info(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var lattice = LeyLattice.get(source.getLevel());
        if (lattice.isEmpty())
        {
            source.sendFailure(Component.literal("Ley lines only exist in the overworld"));
            return 0;
        }

        int x = (int) Math.floor(source.getPosition().x);
        int z = (int) Math.floor(source.getPosition().z);

        lattice.get().nearestNode(x, z).ifPresent(node ->
        {
            var region = lattice.get().region(Math.floorDiv(node.x(), REGION_SIZE), Math.floorDiv(node.z(), REGION_SIZE));
            long inflow = region.edges().stream().filter(edge -> edge.to() == node.id()).count();
            source.sendSuccess(() -> Component.literal(String.format("Node %d (%s) at %d, %d, %.0f blocks away: %d lines (%d in, %d out), power %.1f",
                    node.id(), node.kind().getSerializedName(), node.x(), node.z(), Math.sqrt(node.distanceSq(x, z)),
                    node.lines(), inflow, node.lines() - inflow, node.power())), false);
        });

        lattice.get().nearestEdge(x, z).ifPresent(edge ->
                source.sendSuccess(() -> Component.literal(String.format("Nearest line %.0f blocks away, %.0f long, flowing from %d, %d to %d, %d",
                        Math.sqrt(edge.distanceSq(x, z)), edge.length(), edge.fromX(), edge.fromZ(), edge.toX(), edge.toZ())), false));
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        var source = context.getSource();
        boolean shown = LeyDebugView.toggle(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal(shown ? "Ley lines shown" : "Ley lines hidden"), false);
        return 1;
    }

    private static int anchor(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var anchors = OldAnchors.get(source.getLevel());
        if (anchors.isEmpty())
        {
            source.sendFailure(Component.literal("Ley lines only exist in the overworld"));
            return 0;
        }

        int x = (int) Math.floor(source.getPosition().x);
        int z = (int) Math.floor(source.getPosition().z);
        var nearest = anchors.get().network().anchorsWithin(x - ANCHOR_SEARCH_RADIUS, z - ANCHOR_SEARCH_RADIUS, x + ANCHOR_SEARCH_RADIUS, z + ANCHOR_SEARCH_RADIUS).stream()
                .min(Comparator.comparingDouble(anchor -> Math.hypot(anchor.x() - x, anchor.z() - z)));
        if (nearest.isEmpty())
        {
            source.sendFailure(Component.literal("No Old Anchor within " + ANCHOR_SEARCH_RADIUS + " blocks"));
            return 0;
        }

        var anchor = nearest.get();
        var linked = anchors.get().network().linked(anchor);
        source.sendSuccess(() -> Component.literal(String.format("%s at %d, %d, %d (%s), %.0f blocks away, linked to %d: %s",
                anchor.name(), anchor.x(), anchors.get().lowerY(anchor), anchor.z(), anchors.get().isActive(anchor) ? "active" : "dormant",
                Math.hypot(anchor.x() - x, anchor.z() - z), linked.size(),
                linked.stream().map(OldAnchor::name).collect(Collectors.joining(", ")))), false);
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> context, int radius)
    {
        var source = context.getSource();
        var lattice = LeyLattice.get(source.getLevel());
        if (lattice.isEmpty())
        {
            source.sendFailure(Component.literal("Ley lines only exist in the overworld"));
            return 0;
        }

        int x = (int) Math.floor(source.getPosition().x);
        int z = (int) Math.floor(source.getPosition().z);

        var regions = lattice.get().regionsAround(x, z, radius, false);
        var image = LeyLatticeDump.render(regions, lattice.get()::pressure, x - radius, z - radius, radius * 2, DUMP_PIXELS);

        var output = source.getServer().getWorldPath(LevelResource.ROOT).resolve(VoidHeart.MODID).resolve("ley_" + x + "_" + z + ".png");
        try
        {
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        } catch (IOException e)
        {
            VoidHeart.LOGGER.log(Level.WARNING, "Could not write ley lattice dump", e);
            source.sendFailure(Component.literal("Could not write " + output + ": " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Ley lattice dumped to " + output.toAbsolutePath().normalize()), false);
        return 1;
    }
}
