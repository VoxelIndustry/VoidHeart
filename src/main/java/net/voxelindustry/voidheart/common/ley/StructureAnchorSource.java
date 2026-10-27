package net.voxelindustry.voidheart.common.ley;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predicts where tagged structures will generate from the seed alone, the same way /locate does,
 * so anchors exist before (and regardless of) the chunks being generated.
 */
public class StructureAnchorSource implements LeyAnchorSource
{
    private static final int CACHE_SIZE = 4096;

    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final ChunkGeneratorStructureState structureState;
    private final RandomState randomState;
    private final BiomeResolver biomes;
    private final List<AnchorSet> sets = new ArrayList<>();

    private final Map<Long, List<Anchor>> cache = new LinkedHashMap<>(256, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Anchor>> eldest)
        {
            return size() > CACHE_SIZE;
        }
    };

    public StructureAnchorSource(ServerLevel level)
    {
        this.level = level;
        generator = level.getChunkSource().getGenerator();
        structureState = level.getChunkSource().getGeneratorState();
        randomState = level.getChunkSource().randomState();
        biomes = generator.getBiomeSource().createUncachedResolver(randomState);

        for (var set : structureState.possibleStructureSets())
        {
            var tagged = new ArrayList<Holder<Structure>>();
            LeyNodeKind kind = null;
            for (var entry : set.value().structures())
            {
                var entryKind = kindOf(entry.structure());
                if (entryKind == null)
                    continue;
                tagged.add(entry.structure());
                if (kind == null || entryKind.ordinal() > kind.ordinal())
                    kind = entryKind;
            }

            if (kind != null)
                sets.add(new AnchorSet(set.value().placement(), kind, tagged));
        }
    }

    private static LeyNodeKind kindOf(Holder<Structure> structure)
    {
        if (structure.is(VoidHeartTags.LEY_MAJOR_ANCHOR))
            return LeyNodeKind.MAJOR_ANCHOR;
        if (structure.is(VoidHeartTags.LEY_ANCHOR))
            return LeyNodeKind.ANCHOR;
        if (structure.is(VoidHeartTags.LEY_MINOR_ANCHOR))
            return LeyNodeKind.MINOR_ANCHOR;
        return null;
    }

    @Override
    public List<Anchor> anchorsIn(int minX, int minZ, int maxX, int maxZ)
    {
        long key = (long) minX << 32 | minZ & 0xFFFFFFFFL;
        var cached = cache.get(key);
        if (cached != null)
            return cached;

        var anchors = new ArrayList<Anchor>();
        int minChunkX = minX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkX = maxX >> 4;
        int maxChunkZ = maxZ >> 4;

        for (var set : sets)
        {
            if (set.placement instanceof RandomSpreadStructurePlacement spread)
            {
                int spacing = spread.spacing();
                for (int gridX = Math.floorDiv(minChunkX, spacing); gridX <= Math.floorDiv(maxChunkX, spacing); gridX++)
                {
                    for (int gridZ = Math.floorDiv(minChunkZ, spacing); gridZ <= Math.floorDiv(maxChunkZ, spacing); gridZ++)
                    {
                        ChunkPos chunk = spread.getPotentialStructureChunk(structureState.getLevelSeed(), gridX * spacing, gridZ * spacing);
                        if (chunk.x() < minChunkX || chunk.x() > maxChunkX || chunk.z() < minChunkZ || chunk.z() > maxChunkZ)
                            continue;
                        if (spread.isStructureChunk(structureState, chunk.x(), chunk.z()) && hasValidBiome(set, chunk))
                            anchors.add(new Anchor(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), set.kind));
                    }
                }
            }
            else if (set.placement instanceof ConcentricRingsStructurePlacement rings)
            {
                var positions = structureState.getRingPositionsFor(rings);
                if (positions == null)
                    continue;
                for (var chunk : positions)
                {
                    if (chunk.x() >= minChunkX && chunk.x() <= maxChunkX && chunk.z() >= minChunkZ && chunk.z() <= maxChunkZ)
                        anchors.add(new Anchor(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), set.kind));
                }
            }
        }

        cache.put(key, anchors);
        return anchors;
    }

    /**
     * Approximates the structure's own biome check by sampling the surface and two cave depths,
     * so underground structures like ancient cities are found too.
     */
    private boolean hasValidBiome(AnchorSet set, ChunkPos chunk)
    {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);

        for (int y : new int[] { surface, 0, level.getMinY() + 13 })
        {
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            for (var structure : set.structures)
            {
                if (structure.value().biomes().contains(biome))
                    return true;
            }
        }
        return false;
    }

    private record AnchorSet(StructurePlacement placement, LeyNodeKind kind, List<Holder<Structure>> structures)
    {
    }
}
