package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps each player told of the order their Reckoning tables show: their own oldest running order, whichever table they look at, sent only when
 * it or its progress changes.
 */
public final class ReckoningSync
{
    private static final int PERIOD = 10;

    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private ReckoningSync()
    {
    }

    static void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(ReckoningSync::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SENT.remove(handler.getPlayer().getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
    }

    private static void tick(MinecraftServer server)
    {
        if (server.getTickCount() % PERIOD != 0)
            return;
        var state = WorksState.get(server);
        for (var player : server.getPlayerList().getPlayers())
        {
            var works = state.owners().get(player.getUUID());
            var jobs = works == null ? java.util.List.<WorksQueue.Job>of() : works.queue().jobs();
            var shown = new WorksPayloads.Shown(jobs.isEmpty() ? Optional.empty() : Optional.of(WorksPayloads.Job.of(jobs.getFirst(), works)),
                    Math.max(0, jobs.size() - 1));
            var hash = shown.hashCode();
            if (Integer.valueOf(hash).equals(SENT.get(player.getUUID())))
                continue;
            SENT.put(player.getUUID(), hash);
            ServerPlayNetworking.send(player, shown);
        }
    }
}
