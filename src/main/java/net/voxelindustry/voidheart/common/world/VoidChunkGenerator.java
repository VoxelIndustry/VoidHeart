package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.dynamic.RegistryLookupCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep.Carver;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VoidChunkGenerator extends ChunkGenerator
{
    public static Codec<VoidChunkGenerator> codec;

    @Getter
    private final Registry<Biome> biomeRegistry;

    public VoidChunkGenerator(Registry<Biome> biomeRegistry)
    {
        super(new FixedBiomeSource(biomeRegistry.getOrThrow(BiomeKeys.PLAINS)), new StructuresConfig(false));

        this.biomeRegistry = biomeRegistry;
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec()
    {
        return codec;
    }

    @Override
    public ChunkGenerator withSeed(long seed)
    {
        return this;
    }

    @Override
    public MultiNoiseSampler getMultiNoiseSampler()
    {
        return null;
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, Carver generationStep)
    {

    }

    @Override
    public BlockPos locateStructure(
            ServerWorld world,
            StructureFeature<?> feature,
            BlockPos center,
            int radius,
            boolean skipExistingChunks
    )
    {
        return null;
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, Chunk chunk)
    {

    }

    @Override
    public void populateEntities(ChunkRegion region)
    {
    }

    @Override
    public int getWorldHeight()
    {
        return 384;
    }

    @Override
    public Pool<SpawnSettings.SpawnEntry> getEntitySpawnList(Biome biome, StructureAccessor accessor, SpawnGroup group, BlockPos pos)
    {
        return SpawnSettings.EMPTY_ENTRY_POOL;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, StructureAccessor structureAccessor, Chunk chunk)
    {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel()
    {
        return -63;
    }

    @Override
    public int getMinimumY()
    {
        return 0;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world)
    {
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world)
    {
        return new VerticalBlockSample(0, new BlockState[0]);
    }

    static
    {
        codec = RegistryLookupCodec.of(Registry.BIOME_KEY)
                .xmap(VoidChunkGenerator::new, VoidChunkGenerator::getBiomeRegistry)
                .stable()
                .codec();
    }
}