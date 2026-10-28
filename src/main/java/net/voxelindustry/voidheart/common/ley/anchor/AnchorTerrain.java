package net.voxelindustry.voidheart.common.ley.anchor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Samples the generated terrain (heights and biome colors) under an anchor's travel map. Sampling runs on a
 * background thread since it evaluates thousands of noise columns; results are cached per anchor.
 */
public class AnchorTerrain
{
    /**
     * Smallest span of blocks a travel map shows, shared with the screen so both frame the same area.
     */
    public static final int MIN_SPAN = 512;
    public static final double MAP_PADDING = 1.25;

    // Screens are wider than tall: cover the extra width the screen may show around the anchors
    private static final double WIDTH_FACTOR = 2.2;
    private static final int CELLS_X = 128;

    private final ServerLevel level;
    private final Map<Long, AnchorPayloads.Terrain> cache = new HashMap<>();
    private final Map<Long, Set<UUID>> waiting = new HashMap<>();

    public AnchorTerrain(ServerLevel level)
    {
        this.level = level;
    }

    public void send(ServerPlayer player, AnchorPayloads.TravelMap map)
    {
        long source = map.source().id();
        var cached = cache.get(source);
        if (cached != null)
        {
            ServerPlayNetworking.send(player, cached);
            return;
        }

        boolean alreadySampling = waiting.containsKey(source);
        waiting.computeIfAbsent(source, key -> new HashSet<>()).add(player.getUUID());
        if (alreadySampling)
            return;

        int minX = map.source().x(), maxX = minX, minZ = map.source().z(), maxZ = minZ;
        for (var anchor : map.linked())
        {
            minX = Math.min(minX, anchor.x());
            maxX = Math.max(maxX, anchor.x());
            minZ = Math.min(minZ, anchor.z());
            maxZ = Math.max(maxZ, anchor.z());
        }
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        double span = Math.max(MIN_SPAN, Math.max(maxX - minX, maxZ - minZ) * MAP_PADDING);

        int step = (int) Math.ceil(span * WIDTH_FACTOR / CELLS_X);
        int sizeZ = (int) Math.ceil(span * MAP_PADDING / step);

        var server = level.getServer();
        CompletableFuture.supplyAsync(() -> sample(source, centerX - CELLS_X * step / 2, centerZ - sizeZ * step / 2, step, CELLS_X, sizeZ), Util.backgroundExecutor())
                .whenComplete((terrain, error) -> server.execute(() ->
                {
                    var players = waiting.remove(source);
                    if (error != null)
                    {
                        VoidHeart.LOGGER.log(Level.WARNING, "Could not sample the terrain of a travel map", error);
                        return;
                    }

                    cache.put(source, terrain);
                    for (var uuid : players)
                    {
                        var waitingPlayer = server.getPlayerList().getPlayer(uuid);
                        if (waitingPlayer != null)
                            ServerPlayNetworking.send(waitingPlayer, terrain);
                    }
                }));
    }

    private AnchorPayloads.Terrain sample(long source, int minX, int minZ, int step, int sizeX, int sizeZ)
    {
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        var biomes = generator.getBiomeSource().createUncachedResolver(randomState);
        int seaLevel = generator.getSeaLevel();

        var heights = new int[sizeX * sizeZ];
        var colors = new int[sizeX * sizeZ];
        for (int j = 0; j < sizeZ; j++)
        {
            for (int i = 0; i < sizeX; i++)
            {
                int x = minX + i * step + step / 2;
                int z = minZ + j * step + step / 2;
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
                var biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));

                // Water only sits at or below sea level outside of rare lakes, skip the second column elsewhere
                int floor = surface <= seaLevel + 1 ? generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) : surface;
                boolean water = floor < surface;

                int index = j * sizeX + i;
                heights[index] = water ? floor : surface;
                if (water)
                    colors[index] = biome.value().getWaterColor() & 0xFFFFFF | AnchorPayloads.Terrain.WATER_FLAG;
                else
                    colors[index] = biome.value().getGrassColor(x, z) & 0xFFFFFF;
            }
        }
        return new AnchorPayloads.Terrain(source, minX, minZ, step, sizeX, sizeZ, seaLevel, heights, colors);
    }
}
