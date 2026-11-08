package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock.RavenousStoragePart;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixBlock.RavenousStorageMatrixPart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * Finds the block holding the essence of a storage or matrix from any of its blocks.
 */
public final class StorageParts
{
    private StorageParts()
    {
    }

    /**
     * The storage base or matrix core this block belongs to, itself when it is one, or null when it is neither.
     */
    public static @Nullable BlockPos holderOf(BlockState state, BlockPos pos)
    {
        if (state.is(VoidHeartBlocks.RAVENOUS_STORAGE))
            return state.getValue(RavenousStorageBlock.PART) == RavenousStoragePart.PILLAR ? pos.below() : pos;
        if (state.is(VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX))
        {
            var part = state.getValue(RavenousStorageMatrixBlock.PART);
            return part == RavenousStorageMatrixPart.BASE ? pos : pos.relative(part.face().getOpposite());
        }
        return null;
    }
}
