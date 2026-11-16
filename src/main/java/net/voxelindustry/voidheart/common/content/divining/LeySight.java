package net.voxelindustry.voidheart.common.content.divining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Gathers what a Ley Divining Rod reveals: every line passing within {@link #RADIUS} of where it was raised, with its flow at that moment, and the nodes
 * within the radius.
 */
public final class LeySight
{
    /**
     * How far from the rod lines and nodes show, in blocks.
     */
    public static final int RADIUS = 64;

    private LeySight()
    {
    }

    private record EdgeKey(long from, long to)
    {
    }

    public static LeySightPayload collect(ServerLevel level, Vec3 origin)
    {
        var lines = new ArrayList<LeySightPayload.Line>();
        var nodes = new ArrayList<LeySightPayload.Node>();

        var lattice = LeyLattice.get(level);
        var topology = LeyTopology.get(level);
        if (lattice.isEmpty() || topology.isEmpty())
            return new LeySightPayload(origin.x, origin.y, origin.z, List.of(), List.of());

        var heights = lattice.get().heights();
        var seenEdges = new HashSet<EdgeKey>();
        // A region keeps only the edges with an end inside it, so a line passing near the rod may belong to regions as far as its longest length away
        int reach = RADIUS + (int) Math.ceil(LeyLatticeGenerator.MAX_EDGE_LENGTH);
        for (var region : lattice.get().regionsAround((int) origin.x, (int) origin.z, reach, true))
        {
            for (var edge : region.edges())
            {
                // Edges crossing a region border are stored by both regions
                if (edge.distanceSq(origin.x, origin.z) > RADIUS * RADIUS || !seenEdges.add(new EdgeKey(edge.from(), edge.to())))
                    continue;

                var profile = heights.profile(edge);
                var sampled = new ArrayList<Float>(profile.length);
                for (float height : profile)
                    sampled.add(height);
                float flow = (float) LatticeLeyFlow.lineFlow(level, topology.get(), edge);
                lines.add(new LeySightPayload.Line(edge.fromX(), edge.fromZ(), edge.toX(), edge.toZ(), sampled, flow,
                        taintShare(level, edge.from()), taintShare(level, edge.to())));
            }

            for (var node : region.nodes())
            {
                if (node.distanceSq(origin.x, origin.z) > RADIUS * RADIUS)
                    continue;
                nodes.add(new LeySightPayload.Node(node.x(), heights.nodeHeight(node.x(), node.z()), node.z(), node.kind(),
                        (float) LatticeLeyFlow.nodeFlow(level, node.id()), taintShare(level, node.id())));
            }
        }
        return new LeySightPayload(origin.x, origin.y, origin.z, lines, nodes);
    }

    /**
     * A node's taint from 0, clean, to 1, fully breached.
     */
    private static float taintShare(ServerLevel level, long nodeId)
    {
        return Math.clamp(LeyTaint.taint(level, nodeId) / LeyFlowBalance.MAX_TAINT, 0, 1);
    }
}
