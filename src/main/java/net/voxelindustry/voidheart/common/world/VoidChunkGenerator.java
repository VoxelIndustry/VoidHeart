package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryLookupCodec;
import net.minecraft.world.BlockView;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

public class VoidChunkGenerator extends ChunkGenerator
{
    public static Codec<VoidChunkGenerator> codec;

    private final VerticalBlockSample verticalBlockSample = new VerticalBlockSample(
            Stream.generate(Blocks.AIR::getDefaultState)
                    .limit(256)
                    .toArray(BlockState[]::new)
    );

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
    public void buildSurface(
            ChunkRegion region, Chunk chunk
    )
    {
    }

    @Override
    public void populateNoise(
            WorldAccess world, StructureAccessor accessor, Chunk chunk
    )
    {
    }

    @Override
    public void carve(
            long seed, BiomeAccess access, Chunk chunk, GenerationStep.Carver carver
    )
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
    public void generateFeatures(ChunkRegion region, StructureAccessor accessor)
    {
    }

    @Override
    public void populateEntities(ChunkRegion region)
    {
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmapType)
    {
        return 0;
    }

    @Override
    public BlockView getColumnSample(int x, int z)
    {
        return verticalBlockSample;
    }

    @Override
    public List<SpawnSettings.SpawnEntry> getEntitySpawnList(Biome biome, StructureAccessor accessor, SpawnGroup group, BlockPos pos)
    {
        return emptyList();
    }

    static
    {
        codec = RegistryLookupCodec.of(Registry.BIOME_KEY)
                .xmap(VoidChunkGenerator::new, VoidChunkGenerator::getBiomeRegistry)
                .stable()
                .codec();
    }
}