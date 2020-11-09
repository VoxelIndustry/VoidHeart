package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.Random;

public class VoidMonolithFeature extends Feature<VoidMonolithFeatureConfig>
{
    public VoidMonolithFeature(Codec<VoidMonolithFeatureConfig> configCodec)
    {
        super(configCodec);
    }

    @Override
    public boolean generate(
            StructureWorldAccess world,
            ChunkGenerator generator,
            Random random,
            BlockPos pos,
            VoidMonolithFeatureConfig config)
    {
        if (random.nextFloat() > 0.025F)
            return false;

        Mutable chosenPos = pos.mutableCopy().set(pos.getX() + random.nextInt(16), 256, pos.getZ() + random.nextInt(16));
        Mutable posToPlace = findPosToPlace(world, chosenPos);

        if (posToPlace == null)
            return false;

        int height = random.nextInt(config.maxHeight - config.minHeight) + config.minHeight;

        for (int y = 0; y < height; y++)
        {
            boolean hasBelow = y != 0;
            boolean hasAbove = y != height - 1;

            setBlockState(world, posToPlace,
                    VoidHeartBlocks.VOID_MONOLITH.getDefaultState()
                            .with(StateProperties.UP, hasAbove)
                            .with(StateProperties.DOWN, hasBelow));
            posToPlace.move(Direction.UP);
        }
        return true;
    }

    private static Mutable findPosToPlace(WorldAccess worldAccess, BlockPos.Mutable mutable)
    {
        while (mutable.getY() > 1)
        {
            mutable.move(Direction.DOWN);
            if (worldAccess.getBlockState(mutable).isAir())
            {
                BlockState blockState = worldAccess.getBlockState(mutable.move(Direction.DOWN));
                mutable.move(Direction.UP);
                if (!blockState.isOf(Blocks.WATER) && !blockState.isOf(Blocks.LAVA) && !blockState.isOf(Blocks.BEDROCK) && !blockState.isAir())
                    return mutable;
            }
        }
        return null;
    }
}
