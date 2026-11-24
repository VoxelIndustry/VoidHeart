package net.voxelindustry.voidheart.common.content.divining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.energy.ley.SpurTap;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyHeights;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator;
import net.voxelindustry.voidheart.common.ley.flow.LatticeLeyFlow;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Gathers what a Ley Divining Rod reveals: every line passing within {@link #RADIUS} of where it was raised, with its flow at that moment, the nodes
 * within the radius, and every reach of a standing stone's spur passing within it.
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
            return new LeySightPayload(origin.x, origin.y, origin.z, List.of(), List.of(), List.of());

        var heights = lattice.get().heights();
        var seenEdges = new HashSet<EdgeKey>();
        var edgesNear = new HashMap<EdgeKey, LeyEdge>();
        // A region keeps only the edges with an end inside it, so a line passing near the rod may belong to regions as far as its longest length away
        int reach = RADIUS + (int) Math.ceil(LeyLatticeGenerator.MAX_EDGE_LENGTH);
        for (var region : lattice.get().regionsAround((int) origin.x, (int) origin.z, reach, true))
        {
            for (var edge : region.edges())
            {
                edgesNear.putIfAbsent(new EdgeKey(edge.from(), edge.to()), edge);
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
        return new LeySightPayload(origin.x, origin.y, origin.z, lines, nodes, spurs(level, origin, heights, edgesNear));
    }

    /**
     * Each reach of a spur passing within the radius: from its tap on the line, at the line's height, or from the stone before, to the middle of a stone.
     */
    private static List<LeySightPayload.Spur> spurs(ServerLevel level, Vec3 origin, LeyHeights heights, Map<EdgeKey, LeyEdge> edgesNear)
    {
        var spurs = LeySpurs.get(level);
        var plan = spurs.plan();
        var shown = new ArrayList<LeySightPayload.Spur>();
        for (var link : plan.links())
        {
            var tap = plan.tap(link.root());
            if (tap == null)
                continue;
            var stone = link.stone();
            float toX = stone.getX() + 0.5F, toY = stone.getY() + STONE_MIDDLE, toZ = stone.getZ() + 0.5F;
            float fromX, fromY, fromZ;
            if (link.parent() == null)
            {
                var start = onLine(tap, edgesNear, heights, toY);
                fromX = start[0];
                fromY = start[1];
                fromZ = start[2];
            }
            else
            {
                BlockPos parent = link.parent();
                fromX = parent.getX() + 0.5F;
                fromY = parent.getY() + STONE_MIDDLE;
                fromZ = parent.getZ() + 0.5F;
            }
            if (segmentDistanceSq(origin.x, origin.z, fromX, fromZ, toX, toZ) > (double) RADIUS * RADIUS)
                continue;
            float taint = Math.clamp(LeyTaint.taint(level, tap.fromNode()) / LeyFlowBalance.MAX_TAINT, 0, 1);
            shown.add(new LeySightPayload.Spur(fromX, fromY, fromZ, toX, toY, toZ, spurs.flow(link.root()), taint, link.parent() == null));
        }
        return shown;
    }

    /**
     * Where a spur meets a standing stone, up from the stone's lower block, in blocks.
     */
    private static final float STONE_MIDDLE = 1;

    /**
     * Where the spur leaves the line as the rod draws it, half a block off the lattice's own coordinates, at the line's height. The stone's height stands in
     * if that line was not found.
     */
    private static float[] onLine(SpurTap tap, Map<EdgeKey, LeyEdge> edgesNear, LeyHeights heights, float fallbackY)
    {
        var edge = edgesNear.get(new EdgeKey(tap.fromNode(), tap.toNode()));
        if (edge == null)
            return new float[] { (float) (tap.x() + 0.5), fallbackY, (float) (tap.z() + 0.5) };
        double dx = edge.toX() - edge.fromX(), dz = edge.toZ() - edge.fromZ();
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq == 0 ? 0 : Math.clamp(((tap.x() - edge.fromX()) * dx + (tap.z() - edge.fromZ()) * dz) / lengthSq, 0, 1);
        float y = LeyHeights.at(heights.profile(edge), (float) t);
        return new float[] { (float) (edge.fromX() + 0.5 + dx * t), y + 0.5F, (float) (edge.fromZ() + 0.5 + dz * t) };
    }

    private static double segmentDistanceSq(double px, double pz, double ax, double az, double bx, double bz)
    {
        double dx = bx - ax, dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq == 0 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / lengthSq, 0, 1);
        double ox = ax + dx * t - px, oz = az + dz * t - pz;
        return ox * ox + oz * oz;
    }

    /**
     * A node's taint from 0, clean, to 1, fully breached.
     */
    private static float taintShare(ServerLevel level, long nodeId)
    {
        return Math.clamp(LeyTaint.taint(level, nodeId) / LeyFlowBalance.MAX_TAINT, 0, 1);
    }
}
