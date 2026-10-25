package net.voxelindustry.voidheart.common.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidMonolithFeature extends Feature<VoidMonolithFeatureConfig>
{
    public VoidMonolithFeature(Codec<VoidMonolithFeatureConfig> configCodec)
    {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VoidMonolithFeatureConfig> context)
    {
        if (context.random().nextFloat() > 0.01F)
            return false;

        MutableBlockPos chosenPos = context.origin().mutable().set(context.origin().getX() + context.random().nextInt(16), context.level().getMaxBuildHeight(), context.origin().getZ() + context.random().nextInt(16));
        MutableBlockPos posToPlace = findPosToPlace(context.level(), chosenPos);

        if (posToPlace == null)
            return false;

        int height = context.random().nextInt(context.config().maxHeight() - context.config().minHeight()) + context.config().minHeight();

        for (int y = 0; y < height; y++)
        {
            boolean hasBelow = y != 0;
            boolean hasAbove = y != height - 1;

            setBlock(context.level(), posToPlace,
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
