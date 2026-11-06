package net.voxelindustry.voidheart.common.world.dungeon;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.voxelindustry.voidheart.common.world.EmptyChunkGenerator;

/**
 * An empty world holding only dungeons. They are written at the feature step, which is off the main thread and has what block entities and structure templates need.
 */
public final class DungeonChunkGenerator extends EmptyChunkGenerator
{
    private static final int GEN_DEPTH = 256;

    public static final MapCodec<DungeonChunkGenerator> CODEC = DungeonBiomeSource.CODEC
            .xmap(DungeonChunkGenerator::new, generator -> (DungeonBiomeSource) generator.getBiomeSource())
            .stable();

    private DungeonChunkGenerator(DungeonBiomeSource biomeSource)
    {
        super(biomeSource, GEN_DEPTH);
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
}
