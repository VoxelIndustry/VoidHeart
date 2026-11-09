package net.voxelindustry.voidheart.common.ley.taint;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.ley.anchor.LeyAnchorState;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells players in the overworld which nodes are tainted, whenever a node's taint moves by {@link LeyFlowBalance#SYNC_STEP},
 * changes stage, or a node becomes tainted or clean. Players arriving get the current set.
 */
public final class LeyTaintSync
{
    private static final Long2FloatOpenHashMap LAST_SENT = new Long2FloatOpenHashMap();
    private static final Map<UUID, Integer> VERSION_SENT = new HashMap<>();
    private static int version;
    private static LeyTaintPayload current = new LeyTaintPayload(List.of());

    private LeyTaintSync()
    {
    }

    static void send(ServerLevel overworld)
    {
        if (overworld.dimension() != Level.OVERWORLD)
            return;

        var state = LeyTaintState.get(overworld);
        if (changed(state))
        {
            current = snapshot(overworld, state);
            LAST_SENT.clear();
            current.nodes().forEach(node -> LAST_SENT.put(node.id(), node.taint()));
            version++;
        }

        for (var player : overworld.getServer().getPlayerList().getPlayers())
        {
            if (player.level() != overworld)
            {
                VERSION_SENT.remove(player.getUUID());
                continue;
            }
            var sent = VERSION_SENT.get(player.getUUID());
            if (sent == null || sent != version)
            {
                ServerPlayNetworking.send(player, current);
                VERSION_SENT.put(player.getUUID(), version);
            }
        }
    }

    private static boolean changed(LeyTaintState state)
    {
        var tainted = state.tainted();
        if (tainted.size() != LAST_SENT.size())
            return true;
        for (long node : tainted)
        {
            if (!LAST_SENT.containsKey(node))
                return true;
            float now = state.taint(node);
            float sent = LAST_SENT.get(node);
            if (Math.abs(now - sent) >= LeyFlowBalance.SYNC_STEP || LeyTaintMath.stage(now) != LeyTaintMath.stage(sent))
                return true;
        }
        return false;
    }

    private static LeyTaintPayload snapshot(ServerLevel overworld, LeyTaintState state)
    {
        var nodes = new ArrayList<LeyTaintPayload.Node>();
        var topology = LeyTopology.get(overworld);
        if (topology.isEmpty())
            return new LeyTaintPayload(nodes);

        var anchors = LeyAnchorState.get(overworld);
        for (long id : state.tainted())
        {
            var node = topology.get().node(id);
            if (node.isEmpty())
                continue;
            var spokes = new ArrayList<LeyTaintPayload.Spoke>();
            for (var edge : topology.get().edges(id))
            {
                boolean downstream = edge.from() == id;
                spokes.add(new LeyTaintPayload.Spoke(downstream ? edge.toX() : edge.fromX(), downstream ? edge.toZ() : edge.fromZ(), downstream));
            }
            int anchorY = anchors.placedY(id).orElse(LeyTaintPayload.NO_ANCHOR);
            nodes.add(new LeyTaintPayload.Node(id, node.get().x(), node.get().z(), state.taint(id), anchorY, spokes));
        }
        return new LeyTaintPayload(nodes);
    }

    public static void forget(UUID player)
    {
        VERSION_SENT.remove(player);
    }

    public static void clear()
    {
        LAST_SENT.clear();
        VERSION_SENT.clear();
        version = 0;
        current = new LeyTaintPayload(List.of());
    }
}
