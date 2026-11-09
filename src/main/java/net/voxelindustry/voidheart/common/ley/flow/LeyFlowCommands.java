package net.voxelindustry.voidheart.common.ley.flow;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyNode;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /voidheart ley taint} and {@code /voidheart ley flow}, for review and tests. Both act on the node nearest the source.
 */
public final class LeyFlowCommands
{
    private LeyFlowCommands()
    {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> taint()
    {
        return literal("taint")
                .executes(LeyFlowCommands::showTaint)
                .then(literal("set").then(argument("taint", FloatArgumentType.floatArg(0, LeyFlowBalance.MAX_TAINT))
                        .executes(context -> setTaint(context, FloatArgumentType.getFloat(context, "taint")))))
                .then(literal("add").then(argument("amount", FloatArgumentType.floatArg(-LeyFlowBalance.MAX_TAINT, LeyFlowBalance.MAX_TAINT))
                        .executes(context -> addTaint(context, FloatArgumentType.getFloat(context, "amount")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> flow()
    {
        return literal("flow").executes(LeyFlowCommands::showFlow);
    }

    private static int showTaint(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var level = source.getLevel();
        var node = nearestNode(source);
        if (node.isEmpty())
            return 0;

        long id = node.get().id();
        var topology = LeyTopology.get(level).orElseThrow();
        String downstream = Arrays.stream(topology.downstream(id))
                .mapToObj(next -> String.format("%d (%.1f)", next, Ley.taint(level, next)))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(String.format("Node %d at %d, %d: taint %.2f, %s%s, downstream: %s",
                id, node.get().x(), node.get().z(), Ley.taint(level, id), Ley.stage(level, id).getSerializedName(),
                LeyTaint.isSalted(level, id) ? ", salted" : "", downstream.isEmpty() ? "none" : downstream)), false);
        return 1;
    }

    private static int setTaint(CommandContext<CommandSourceStack> context, float taint)
    {
        var source = context.getSource();
        var node = nearestNode(source);
        if (node.isEmpty())
            return 0;

        LeyTaint.set(source.getLevel(), node.get().id(), taint);
        source.sendSuccess(() -> Component.literal(String.format("Node %d taint set to %.1f (%s)",
                node.get().id(), Ley.taint(source.getLevel(), node.get().id()), Ley.stage(source.getLevel(), node.get().id()).getSerializedName())), true);
        return 1;
    }

    private static int addTaint(CommandContext<CommandSourceStack> context, float amount)
    {
        var source = context.getSource();
        var node = nearestNode(source);
        if (node.isEmpty())
            return 0;

        LeyTaint.forgetSalt();
        Ley.addTaint(source.getLevel(), node.get().id(), amount);
        source.sendSuccess(() -> Component.literal(String.format("Node %d taint now %.1f (%s), passing downstream at the next window",
                node.get().id(), Ley.taint(source.getLevel(), node.get().id()), Ley.stage(source.getLevel(), node.get().id()).getSerializedName())), true);
        return 1;
    }

    private static int showFlow(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var level = source.getLevel();
        var topology = LeyTopology.get(level);
        if (topology.isEmpty())
        {
            source.sendFailure(Component.literal("Ley lines only exist in the overworld"));
            return 0;
        }

        int x = (int) Math.floor(source.getPosition().x);
        int z = (int) Math.floor(source.getPosition().z);
        var attachment = topology.get().attachment(x, z);
        if (attachment.isNone())
        {
            source.sendFailure(Component.literal("Not on a ley node or line"));
            return 0;
        }

        long nodeId = attachment.sourceNode();
        var node = topology.get().node(nodeId).orElseThrow();
        int phase = LatticeLeyFlow.moonPhase(level);
        boolean awakened = LatticeLeyFlow.isAwakened(level, nodeId);
        float taint = Ley.taint(level, nodeId);
        double nodeFlow = LatticeLeyFlow.nodeFlow(level, nodeId);
        source.sendSuccess(() -> Component.literal(String.format("Node %d: power %.2f × tide %.2f (moon phase %d) × awakened %.1f × taint %.2f = %.2f ley",
                nodeId, node.power(), LeyFlowMath.tide(phase), phase, awakened ? LeyFlowBalance.AWAKENED : 1D, LeyFlowMath.taintFactor(taint), nodeFlow)), false);
        source.sendSuccess(() -> Component.literal(String.format("Claimed on the node %.2f, %d downstream lines",
                LatticeLeyFlow.claimedOnNode(level, nodeId), topology.get().downstreamLines(nodeId))), false);

        if (attachment.onLine())
        {
            var edge = attachment.edge();
            source.sendSuccess(() -> Component.literal(String.format("On the line to %d, %d at %.0f%% along: carries %.2f ley, claimed %.2f",
                    edge.toX(), edge.toZ(), attachment.position() * 100, LatticeLeyFlow.lineFlow(level, topology.get(), edge),
                    LatticeLeyFlow.claimedOnLine(level, edge))), false);
        }
        for (var claim : LatticeLeyFlow.claims(level, attachment))
        {
            var pos = BlockPos.of(claim.claimant());
            source.sendSuccess(() -> Component.literal(String.format("  claim at %d, %d, %d (%.0f%% along): asked %.2f, granted %.2f",
                    pos.getX(), pos.getY(), pos.getZ(), claim.position() * 100, claim.requested(), claim.granted())), false);
        }
        return 1;
    }

    private static Optional<LeyNode> nearestNode(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        var topology = LeyTopology.get(level);
        if (topology.isEmpty())
        {
            source.sendFailure(Component.literal("Ley lines only exist in the overworld"));
            return Optional.empty();
        }
        int x = (int) Math.floor(source.getPosition().x);
        int z = (int) Math.floor(source.getPosition().z);
        var node = topology.get().lattice().nearestNode(x, z);
        if (node.isEmpty())
            source.sendFailure(Component.literal("No ley node nearby"));
        return node;
    }
}
