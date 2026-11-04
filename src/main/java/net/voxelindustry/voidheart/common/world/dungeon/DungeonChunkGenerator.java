package net.voxelindustry.voidheart.common.world.dungeon;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * An empty world holding only dungeons. They are written at the feature step, which is off the main thread and has what block entities and structure templates need.
 */
public final class DungeonChunkGenerator extends ChunkGenerator
{
    public static final MapCodec<DungeonChunkGenerator> CODEC = DungeonBiomeSource.CODEC
            .xmap(DungeonChunkGenerator::new, generator -> (DungeonBiomeSource) generator.getBiomeSource())
            .stable();

    private DungeonChunkGenerator(DungeonBiomeSource biomeSource)
    {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager)
    {
        DungeonPlacer.place(level, chunk);
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structureManager, MobCategory mobCategory, BlockPos pos)
    {
        return WeightedList.of();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region)
    {
    }

    @Override
    public int getGenDepth()
    {
        return 256;
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
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel()
    {
        return -63;
    }

    @Override
    public int getMinY()
    {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig)
    {
        return 0;
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
