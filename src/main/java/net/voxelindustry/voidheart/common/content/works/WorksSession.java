package net.voxelindustry.voidheart.common.content.works;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server side of one open Reckoning: it sends what the owner's stations can make and their orders whenever these change, and answers plans asked
 * for, orders begun and cancelled, and levels to keep.
 */
public final class WorksSession
{
    private static final int REFRESH_TICKS = 20;

    private final ServerPlayer player;
    private final UUID owner;
    private final int containerId;
    private int sentMakeable;
    private int sentJobs;

    public WorksSession(ServerPlayer player, UUID owner, int containerId)
    {
        this.player = player;
        this.owner = owner;
        this.containerId = containerId;
        sentMakeable = 1;
        sentJobs = 1;
    }

    private PocketHoard.Hoard hoard()
    {
        return PocketHoard.get(player.level().getServer()).of(owner);
    }

    public void broadcast(boolean first)
    {
        var server = player.level().getServer();
        if (!first && server.getTickCount() % REFRESH_TICKS != 0)
            return;

        var makeable = new ArrayList<>(WorksCatalogues.of(server, owner, hoard()).outputs());
        makeable.sort(java.util.Comparator.comparing(item -> item.toStack().getHoverName().getString()));
        if (first || makeable.hashCode() != sentMakeable)
        {
            sentMakeable = makeable.hashCode();
            var pages = Math.max(1, (makeable.size() + WorksPayloads.PAGE_SIZE - 1) / WorksPayloads.PAGE_SIZE);
            for (int page = 0; page < pages; page++)
            {
                var from = page * WorksPayloads.PAGE_SIZE;
                ServerPlayNetworking.send(player, new WorksPayloads.Makeable(containerId, page, pages,
                        List.copyOf(makeable.subList(from, Math.min(makeable.size(), from + WorksPayloads.PAGE_SIZE)))));
            }
        }

        var jobs = jobs();
        if (first || jobs.hashCode() != sentJobs)
        {
            sentJobs = jobs.hashCode();
            ServerPlayNetworking.send(player, jobs);
        }
    }

    private WorksPayloads.Jobs jobs()
    {
        var works = WorksState.get(player.level().getServer()).owner(owner);
        var jobs = new ArrayList<WorksPayloads.Job>();
        for (var job : works.queue().jobs())
            jobs.add(WorksPayloads.Job.of(job, works));
        var keeps = works.keeps().entrySet().stream().map(entry -> new WorksPayloads.Kept(entry.getKey(), entry.getValue())).toList();
        return new WorksPayloads.Jobs(containerId, jobs, keeps);
    }

    void plan(ItemVariant item, long count)
    {
        var server = player.level().getServer();
        var hoard = hoard();
        var plan = WorksPlan.plan(WorksCatalogues.of(server, owner, hoard).lookup(), hoard.view(), item, Math.max(1, count));
        ServerPlayNetworking.send(player, new WorksPayloads.Plan(containerId, item, count, WorksGraph.of(plan)));
    }

    void begin(ItemVariant item, long count)
    {
        WorksContent.order(player.level().getServer(), owner, item, Math.max(1, count));
        broadcast(true);
    }

    void cancel(int job)
    {
        WorksState.get(player.level().getServer()).edit(owner).cancel(job);
        broadcast(true);
    }

    void keep(ItemVariant item, long level)
    {
        var state = WorksState.get(player.level().getServer());
        var works = state.owner(owner);
        if (level <= 0)
        {
            works.keeps().remove(item);
            works.keepJobs().remove(item);
        }
        else
            works.keeps().put(item, level);
        state.setDirty();
        broadcast(true);
    }
}
