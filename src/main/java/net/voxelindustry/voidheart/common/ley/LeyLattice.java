package net.voxelindustry.voidheart.common.ley;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

/**
 * Entry point to the overworld ley lattice. Regions are generated on first access and frozen in {@link LeyLatticeState}.
 */
public class LeyLattice
{
    private static final Map<ServerLevel, LeyLattice> BY_LEVEL = new WeakHashMap<>();

    public interface RegionStore
    {
        LeyRegion get(int regionX, int regionZ);

        void put(LeyRegion region);
    }

    private final LeyLatticeGenerator generator;
    private final RegionStore store;

    public LeyLattice(LeyLatticeGenerator generator, RegionStore store)
    {
        this.generator = generator;
        this.store = store;
    }

    public static Optional<LeyLattice> get(ServerLevel level)
    {
        if (level.dimension() != Level.OVERWORLD)
            return Optional.empty();

        return Optional.of(BY_LEVEL.computeIfAbsent(level, key ->
                new LeyLattice(new LeyLatticeGenerator(level.getSeed(), new StructureAnchorSource(level)), LeyLatticeState.get(level))));
    }

    public static void clear()
    {
        BY_LEVEL.clear();
    }

    public LeyRegion region(int regionX, int regionZ)
    {
        var region = store.get(regionX, regionZ);
        if (region != null)
            return region;

        region = reconcile(generator.generateRegion(regionX, regionZ), store);
        store.put(region);
        return region;
    }

    /**
     * Same as {@link #region} without freezing anything, for debug views over unexplored land.
     */
    public LeyRegion preview(int regionX, int regionZ)
    {
        var region = store.get(regionX, regionZ);
        return region != null ? region : reconcile(generator.generateRegion(regionX, regionZ), store);
    }

    public float pressure(double x, double z)
    {
        return generator.pressure(x, z);
    }

    public List<LeyRegion> regionsAround(int x, int z, int radius, boolean freeze)
    {
        var regions = new ArrayList<LeyRegion>();
        for (int regionX = Math.floorDiv(x - radius, REGION_SIZE); regionX <= Math.floorDiv(x + radius, REGION_SIZE); regionX++)
        {
            for (int regionZ = Math.floorDiv(z - radius, REGION_SIZE); regionZ <= Math.floorDiv(z + radius, REGION_SIZE); regionZ++)
                regions.add(freeze ? region(regionX, regionZ) : preview(regionX, regionZ));
        }
        return regions;
    }

    public Optional<LeyNode> nearestNode(int x, int z)
    {
        return regionsAround(x, z, REGION_SIZE / 2, true).stream()
                .flatMap(region -> region.nodes().stream())
                .min(Comparator.comparingDouble(node -> node.distanceSq(x, z)));
    }

    public Optional<LeyEdge> nearestEdge(int x, int z)
    {
        return regionsAround(x, z, REGION_SIZE / 2, true).stream()
                .flatMap(region -> region.edges().stream())
                .min(Comparator.comparingDouble(edge -> edge.distanceSq(x, z)));
    }

    /**
     * Frozen neighbors are authoritative on the edges they share with a freshly generated region.
     * With unchanged rules this is a no-op, since generation already agrees across borders.
     */
    static LeyRegion reconcile(LeyRegion fresh, RegionStore store)
    {
        var edges = new ArrayList<>(fresh.edges());
        var nodesById = new HashMap<Long, LeyNode>();
        fresh.nodes().forEach(node -> nodesById.put(node.id(), node));

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                var frozen = (dx | dz) == 0 ? null : store.get(fresh.regionX() + dx, fresh.regionZ() + dz);
                if (frozen == null)
                    continue;

                edges.removeIf(edge -> frozen.contains(edge.fromX(), edge.fromZ()) || frozen.contains(edge.toX(), edge.toZ()));
                for (var edge : frozen.edges())
                {
                    if (matchesNode(nodesById.get(edge.from()), edge.fromX(), edge.fromZ()) || matchesNode(nodesById.get(edge.to()), edge.toX(), edge.toZ()))
                        edges.add(edge);
                }
            }
        }

        var lines = new HashMap<Long, Integer>();
        for (var edge : edges)
        {
            lines.merge(edge.from(), 1, Integer::sum);
            lines.merge(edge.to(), 1, Integer::sum);
        }

        var nodes = new ArrayList<LeyNode>();
        for (var node : fresh.nodes())
        {
            int count = lines.getOrDefault(node.id(), 0);
            if (count > 0)
                nodes.add(count == node.lines() ? node : node.withLines(count));
        }
        return new LeyRegion(fresh.regionX(), fresh.regionZ(), nodes, edges);
    }

    private static boolean matchesNode(LeyNode node, int x, int z)
    {
        return node != null && node.x() == x && node.z() == z;
    }
}
