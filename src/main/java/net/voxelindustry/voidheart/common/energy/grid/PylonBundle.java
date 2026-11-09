package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

/**
 * The azurium bundle standing upright in the block above a pylon, which sets its field and cap.
 */
public enum PylonBundle
{
    NONE(0, 0, 0),
    SMALL(VoidGridBalance.SMALL_RADIUS, VoidGridBalance.SMALL_CAP, 6),
    MEDIUM(VoidGridBalance.MEDIUM_RADIUS, VoidGridBalance.MEDIUM_CAP, 10),
    LARGE(VoidGridBalance.LARGE_RADIUS, VoidGridBalance.LARGE_CAP, 15);

    private final int  radius;
    private final long cap;
    private final int  height;

    PylonBundle(int radius, long cap, int height)
    {
        this.radius = radius;
        this.cap = cap;
        this.height = height;
    }

    public int radius()
    {
        return radius;
    }

    public long cap()
    {
        return cap;
    }

    /**
     * Height of the bundle's crystals in pixels, matching its block shape, so arcs leave from its tip.
     */
    public int height()
    {
        return height;
    }

    public static PylonBundle above(BlockGetter level, BlockPos pylon)
    {
        return of(level.getBlockState(pylon.above()));
    }

    public static PylonBundle of(BlockState state)
    {
        if (!state.hasProperty(AmethystClusterBlock.FACING) || state.getValue(AmethystClusterBlock.FACING) != Direction.UP)
            return NONE;
        return ofBlock(state.getBlock());
    }

    public static PylonBundle ofBlock(Block block)
    {
        if (block == VoidHeartBlocks.SMALL_AZURIUM_BUNDLE)
            return SMALL;
        if (block == VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE)
            return MEDIUM;
        if (block == VoidHeartBlocks.LARGE_AZURIUM_BUNDLE)
            return LARGE;
        return NONE;
    }
}
