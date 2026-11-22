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
    private int sentRoster;

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

        var roster = roster();
        if (first || roster.hashCode() != sentRoster)
        {
            sentRoster = roster.hashCode();
            ServerPlayNetworking.send(player, roster);
        }
    }

    /**
     * Each of the owner's loaded Officiants, nearest first: its station, what it is doing, and for a forge what that build makes.
     */
    private WorksPayloads.Roster roster()
    {
        var server = player.level().getServer();
        var catalogue = WorksCatalogues.of(server, owner, hoard());
        var stations = new ArrayList<WorksPayloads.Station>();
        var officiants = new ArrayList<>(OfficiantTile.loadedOf(owner));
        officiants.sort(java.util.Comparator.comparing((OfficiantTile officiant) -> officiant.getLevel() != player.level())
                .thenComparingDouble(officiant -> officiant.getBlockPos().distSqr(player.blockPosition())));
        for (var officiant : officiants)
        {
            var kind = officiant.stationKind();
            var claim = officiant.getClaim();
            var making = "";
            if (claim != null)
            {
                var recipes = catalogue.byId(claim.recipe());
                making = recipes.isEmpty() ? claim.recipe() : recipes.getFirst().outputs().getFirst().item().toStack().getHoverName().getString();
            }
            int status = claim != null ? officiant.isLaying() ? WorksPayloads.Station.LAYING : WorksPayloads.Station.WORKING
                    : switch (officiant.energy())
                    {
                        case READY -> WorksPayloads.Station.IDLE;
                        case GATHERING -> WorksPayloads.Station.GATHERING;
                        case NO_FIELD -> WorksPayloads.Station.NO_FIELD;
                    };
            var makes = kind == StationKind.FORGE ? officiant.forgeRecipes().stream()
                    .map(recipe -> ItemVariant.of(recipe.getRecipeOutputs(net.minecraft.world.item.ItemStack.class).getFirst().getRaw())).distinct().toList()
                    : List.<ItemVariant>of();
            stations.add(new WorksPayloads.Station(officiant.getBlockPos(), kind == null ? -1 : kind.ordinal(), status, making, makes));
        }
        return new WorksPayloads.Roster(containerId, stations);
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
        var plan = WorksPlan.plan(WorksCatalogues.of(server, owner, hoard).lookup(), hoard.view(), item, Math.clamp(count, 1, WorksBalance.MAX_ORDER));
        var graph = WorksGraph.of(plan);
        var reserve = new java.util.LinkedHashMap<String, Long>();
        for (var aspect : graph.essence().keySet())
        {
            var key = net.minecraft.resources.Identifier.tryParse(aspect);
            var type = key == null ? null : net.voxelindustry.voidheart.common.content.essence.EssenceRegistry.ESSENCE_REGISTRY.getValue(key);
            reserve.put(aspect, type == null ? 0 : hoard.reserve().amountOf(type));
        }
        ServerPlayNetworking.send(player, new WorksPayloads.Plan(containerId, item, count, graph, reserve));
    }

    void begin(ItemVariant item, long count)
    {
        WorksContent.order(player.level().getServer(), owner, item, Math.clamp(count, 1, WorksBalance.MAX_ORDER));
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
            // The order the level placed goes with it, so keeping the item again never orders the same shortfall twice
            works.keeps().remove(item);
            var job = works.keepJobs().remove(item);
            if (job != null)
                works.queue().cancel(job);
        }
        else
            works.keeps().put(item, Math.min(level, WorksBalance.MAX_ORDER));
        state.setDirty();
        broadcast(true);
    }
}
