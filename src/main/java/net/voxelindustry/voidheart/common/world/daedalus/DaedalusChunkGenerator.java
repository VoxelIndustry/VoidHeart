package net.voxelindustry.voidheart.common.world.daedalus;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The infinite maze. Everything happens at the terrain step, and every later step is empty so that an explored chunk costs as little as possible.
 */
public final class DaedalusChunkGenerator extends ChunkGenerator
{
    private static final int SEA_LEVEL = -63;

    public static final MapCodec<DaedalusChunkGenerator> CODEC = DaedalusBiomeSource.CODEC
            .xmap(DaedalusChunkGenerator::new, generator -> (DaedalusBiomeSource) generator.getBiomeSource())
            .stable();

    private DaedalusChunkGenerator(DaedalusBiomeSource biomeSource)
    {
        super(biomeSource);
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
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structureManager, MobCategory mobCategory, BlockPos pos)
    {
        return WeightedList.of();
    }

    @Override
    public int getGenDepth()
    {
        return DistrictPos.HEIGHT;
    }

    @Override
    public int getSeaLevel()
    {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY()
    {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig)
    {
        return DistrictPos.HEIGHT;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig)
    {
        return new NoiseColumn(world.getMinY(), new BlockState[] { Blocks.AIR.defaultBlockState() });
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos, SamplerContext samplerContext)
    {
    }
}
