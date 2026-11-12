package net.voxelindustry.voidheart.common.world.daedalus;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.voxelindustry.voidheart.common.content.underway.security.UnderwayUnitSpawner;
import net.voxelindustry.voidheart.common.world.EmptyChunkGenerator;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The infinite maze. Everything happens at the terrain step, and every later step is empty so that an explored chunk costs as little as possible.
 */
public final class DaedalusChunkGenerator extends EmptyChunkGenerator
{
    public static final MapCodec<DaedalusChunkGenerator> CODEC = DaedalusBiomeSource.CODEC
            .xmap(DaedalusChunkGenerator::new, generator -> (DaedalusBiomeSource) generator.getBiomeSource())
            .stable();

    private DaedalusChunkGenerator(DaedalusBiomeSource biomeSource)
    {
        super(biomeSource, DistrictPos.HEIGHT + UnderwayLevels.HEIGHT);
    }

    /** The Underway lies under the districts' floor at 0. */
    @Override
    public int getMinY()
    {
        return UnderwayLevels.MIN_Y;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk,
                                                       Blender blender,
                                                       RandomState randomState,
                                                       StructureManager structureManager,
                                                       BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion carverBiomeRegion,
                                                       Set<Holder<Biome>> possibleBiomes)
    {
        DaedalusChunkWriter.write(chunk, randomState.seed());
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void createStructures(RegistryAccess registryAccess,
                                 ChunkGeneratorStructureState structureState,
                                 StructureManager structureManager,
                                 ChunkAccess chunk,
                                 StructureTemplateManager templateManager,
                                 ResourceKey<Level> dimension)
    {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk)
    {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager)
    {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region)
    {
        DaedalusMobSpawner.spawn(region);
        UnderwayUnitSpawner.spawn(region);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig)
    {
        return DistrictPos.HEIGHT;
    }
}
