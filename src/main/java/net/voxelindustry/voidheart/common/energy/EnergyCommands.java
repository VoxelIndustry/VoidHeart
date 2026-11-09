package net.voxelindustry.voidheart.common.energy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonContent;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;

import static net.minecraft.commands.Commands.literal;

/**
 * /voidheart energy, with probe to read what the contracts answer where the caller stands, and one hook per package for its own subcommands.
 */
public final class EnergyCommands
{
    private EnergyCommands()
    {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder)
    {
        var energy = literal("energy");
        energy.then(literal("probe").executes(EnergyCommands::probe));

        VoidGridContent.registerCommands(energy);
        RubrumContent.registerCommands(energy);
        LeyStoneContent.registerCommands(energy);
        RavenousEngineContent.registerCommands(energy);
        LeySpikeContent.registerCommands(energy);
        VoidFissureContent.registerCommands(energy);
        ContravoidReactorContent.registerCommands(energy);
        DeepSiphonContent.registerCommands(energy);

        builder.then(energy);
    }

    private static int probe(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var level = source.getLevel();
        var pos = BlockPos.containing(source.getPosition());

        long available = VoidEnergy.available(level, pos);
        float supply = LeyPower.supply(level, pos, 1);
        double flow = Ley.lineFlowAt(level, pos);
        float seep = VoidSeep.strengthAt(level, pos);
        source.sendSuccess(() -> Component.literal(String.format("Void energy %s, ley supply %.0f%%, line flow %.2f, seep %.2f",
                available == Long.MAX_VALUE ? "endless" : Long.toString(available), supply * 100, flow, seep)), false);

        LeyLattice.get(level).flatMap(lattice -> lattice.nearestNode(pos.getX(), pos.getZ())).ifPresent(node ->
                source.sendSuccess(() -> Component.literal(String.format("Nearest node %d, %.0f blocks away: taint %.1f, %s",
                        node.id(), Math.sqrt(node.distanceSq(pos.getX(), pos.getZ())), Ley.taint(level, node.id()), Ley.stage(level, node.id()).getSerializedName())), false));
        return 1;
    }
}
