package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public record VoidMonolithFeature(int minHeight, int maxHeight) implements Feature
{
    public static final MapCodec<VoidMonolithFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("minimum_height").forGetter(VoidMonolithFeature::minHeight),
            Codec.INT.fieldOf("maximum_height").forGetter(VoidMonolithFeature::maxHeight)
    ).apply(instance, VoidMonolithFeature::new));

    @Override
    public MapCodec<VoidMonolithFeature> codec()
    {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin)
    {
        if (random.nextFloat() > 0.01F)
            return false;

        MutableBlockPos chosenPos = origin.mutable().set(origin.getX() + random.nextInt(16), level.getMaxY(), origin.getZ() + random.nextInt(16));
        MutableBlockPos posToPlace = findPosToPlace(level, chosenPos);

        if (posToPlace == null)
            return false;

        int height = random.nextInt(maxHeight - minHeight) + minHeight;

        for (int y = 0; y < height; y++)
        {
            boolean hasBelow = y != 0;
            boolean hasAbove = y != height - 1;

            setBlock(level, posToPlace,
                    VoidHeartBlocks.VOID_MONOLITH.defaultBlockState()
                            .setValue(StateProperties.UP, hasAbove)
                            .setValue(StateProperties.DOWN, hasBelow)
                            .setValue(StateProperties.BROKEN, true));
            posToPlace.move(Direction.UP);
        }
        return true;
    }

    private static MutableBlockPos findPosToPlace(LevelAccessor worldAccess, BlockPos.MutableBlockPos mutable)
    {
        while (mutable.getY() > 1)
        {
            mutable.move(Direction.DOWN);
            if (worldAccess.getBlockState(mutable).isAir())
            {
                BlockState blockState = worldAccess.getBlockState(mutable.move(Direction.DOWN));
                mutable.move(Direction.UP);
                if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.LAVA) && !blockState.is(Blocks.BEDROCK) && !blockState.isAir())
                    return mutable;
            }
        }
        return null;
    }
}
