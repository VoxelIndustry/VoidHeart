package net.voxelindustry.voidheart.common.ley.anchor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends a player the anchors linked to the one they stand by, and clears them once they walk away.
 */
public class AnchorWaypoints
{
    private static final int CHECK_INTERVAL = 10;

    private static final Map<UUID, Long> anchorByPlayer = new HashMap<>();

    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % CHECK_INTERVAL != 0)
            return;

        for (var player : server.getPlayerList().getPlayers())
        {
            var anchors = OldAnchors.get(player.level());
            var near = anchors.flatMap(service -> service.anchorNear(player.position()));
            Long nearId = near.map(OldAnchor::nodeId).orElse(null);

            if (Objects.equals(nearId, anchorByPlayer.get(player.getUUID())))
                continue;

            if (near.isPresent())
            {
                anchorByPlayer.put(player.getUUID(), nearId);
                var service = anchors.get();
                ServerPlayNetworking.send(player, new AnchorPayloads.Waypoints(Optional.of(service.view(near.get())), service.linkedViews(near.get())));
            }
            else
            {
                anchorByPlayer.remove(player.getUUID());
                ServerPlayNetworking.send(player, AnchorPayloads.Waypoints.CLEAR);
            }
        }
    }

    /**
     * Forces a resend on the next check, after an anchor changed state.
     */
    public static void invalidate()
    {
        anchorByPlayer.replaceAll((player, anchor) -> Long.MIN_VALUE);
    }

    public static void clear()
    {
        anchorByPlayer.clear();
    }
}
