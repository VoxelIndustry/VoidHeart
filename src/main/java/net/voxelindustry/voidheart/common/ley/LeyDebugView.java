package net.voxelindustry.voidheart.common.ley;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Streams nearby ley lines to players who toggled the debug view, resending once they wander far enough.
 */
public class LeyDebugView
{
    private static final int VIEW_RADIUS = 512;
    private static final int RESEND_DISTANCE = 128;
    private static final int CHECK_INTERVAL = 10;

    private static final Map<UUID, Vec3> lastSentByViewer = new HashMap<>();

    public static boolean toggle(ServerPlayer player)
    {
        if (lastSentByViewer.containsKey(player.getUUID()))
        {
            lastSentByViewer.remove(player.getUUID());
            ServerPlayNetworking.send(player, LeyDebugPayload.CLEAR);
            return false;
        }

        lastSentByViewer.put(player.getUUID(), null);
        return true;
    }

    public static void tickServer(MinecraftServer server)
    {
        if (lastSentByViewer.isEmpty() || server.getTickCount() % CHECK_INTERVAL != 0)
            return;

        for (var entry : lastSentByViewer.entrySet())
        {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null)
                continue;

            var lattice = LeyLattice.get(player.level());
            if (lattice.isEmpty())
            {
                if (entry.getValue() != null)
                {
                    ServerPlayNetworking.send(player, LeyDebugPayload.CLEAR);
                    entry.setValue(null);
                }
                continue;
            }

            var lastSent = entry.getValue();
            if (lastSent != null && lastSent.subtract(player.position()).horizontalDistanceSqr() < RESEND_DISTANCE * RESEND_DISTANCE)
                continue;

            ServerPlayNetworking.send(player, collect(lattice.get(), player.getBlockX(), player.getBlockZ()));
            entry.setValue(player.position().multiply(1, 0, 1));
        }
    }

    public static void clear()
    {
        lastSentByViewer.clear();
    }

    private static LeyDebugPayload collect(LeyLattice lattice, int x, int z)
    {
        var lines = new ArrayList<LeyDebugPayload.Line>();
        var nodes = new ArrayList<LeyDebugPayload.Node>();
        var seenEdges = new HashSet<String>();
        var heights = lattice.heights();

        for (var region : lattice.regionsAround(x, z, VIEW_RADIUS, true))
        {
            for (var edge : region.edges())
            {
                if (edge.distanceSq(x, z) > VIEW_RADIUS * VIEW_RADIUS || !seenEdges.add(edge.from() + ":" + edge.to()))
                    continue;

                var profile = heights.profile(edge);
                var sampled = new ArrayList<Float>(profile.length);
                for (float height : profile)
                    sampled.add(height);
                lines.add(new LeyDebugPayload.Line(edge.fromX(), edge.fromZ(), edge.toX(), edge.toZ(), sampled));
            }

            for (var node : region.nodes())
            {
                if (node.distanceSq(x, z) <= VIEW_RADIUS * VIEW_RADIUS)
                    nodes.add(new LeyDebugPayload.Node(node.x(), heights.nodeHeight(node.x(), node.z()), node.z(), node.kind(), node.lines(), node.power()));
            }
        }
        return new LeyDebugPayload(lines, nodes);
    }
}
