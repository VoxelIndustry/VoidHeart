package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class VoidChunkGenerator extends EmptyChunkGenerator
{
    private static final int GEN_DEPTH = 384;

    public static final MapCodec<VoidChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(RegistryOps.retrieveElement(Biomes.PLAINS))
            .apply(instance, instance.stable(VoidChunkGenerator::new)));

    public VoidChunkGenerator(Reference<Biome> biomeReference)
    {
        super(new FixedBiomeSource(biomeReference), GEN_DEPTH);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }
}
