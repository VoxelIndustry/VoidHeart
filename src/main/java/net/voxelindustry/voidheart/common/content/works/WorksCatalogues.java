package net.voxelindustry.voidheart.common.content.works;

import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Each owner's catalogue, kept while their hoard and stations stay as they were, since building one reads every crafting recipe.
 */
public final class WorksCatalogues
{
    /** Ticks a catalogue stays good for, so research done and forges rebuilt show up. */
    private static final long MAX_AGE = 200;

    private record Cached(WorksCatalogue catalogue, long hoardVersion, int stations, long builtAt)
    {
    }

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private WorksCatalogues()
    {
    }

    public static WorksCatalogue of(MinecraftServer server, UUID owner, PocketHoard.Hoard hoard)
    {
        var officiants = OfficiantTile.loadedOf(owner);
        var kinds = EnumSet.noneOf(StationKind.class);
        var forgeRecipes = new HashSet<ShatterForgeRecipe>();
        for (var officiant : officiants)
        {
            var kind = officiant.stationKind();
            if (kind != null)
                kinds.add(kind);
            if (kind == StationKind.FORGE)
                forgeRecipes.addAll(officiant.forgeRecipes());
        }
        var stations = kinds.hashCode() * 31 + forgeRecipes.size();
        var now = server.overworld().getGameTime();

        var cached = CACHE.get(owner);
        if (cached != null && cached.hoardVersion() == hoard.version() && cached.stations() == stations && now - cached.builtAt() < MAX_AGE)
            return cached.catalogue();

        var catalogue = WorksCatalogue.of(server, owner, kinds, forgeRecipes, hoard.view());
        CACHE.put(owner, new Cached(catalogue, hoard.version(), stations, now));
        return catalogue;
    }
}
