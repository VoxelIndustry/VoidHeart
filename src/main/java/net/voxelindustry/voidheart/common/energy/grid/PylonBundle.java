package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * The azurium bundle standing upright in the block above a pylon, or hanging below one that hangs, which sets its field and cap.
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

    /**
     * The bundle in the claws of the pylon at that position.
     */
    public static PylonBundle held(BlockGetter level, BlockPos pylon)
    {
        var state = level.getBlockState(pylon);
        if (!state.is(VoidGridContent.AZURIUM_PYLON))
            return NONE;
        var claws = AzuriumPylonBlock.claws(state);
        return of(level.getBlockState(pylon.relative(claws)), claws);
    }

    /**
     * The pylon whose claws hold the bundle at that position, if any.
     */
    public static @Nullable BlockPos holder(BlockGetter level, BlockPos bundle)
    {
        var state = level.getBlockState(bundle);
        if (!state.hasProperty(AmethystClusterBlock.FACING))
            return null;
        var pylon = bundle.relative(state.getValue(AmethystClusterBlock.FACING).getOpposite());
        var pylonState = level.getBlockState(pylon);
        if (!pylonState.is(VoidGridContent.AZURIUM_PYLON) || !AzuriumPylonBlock.bundlePos(pylonState, pylon).equals(bundle))
            return null;
        return held(level, pylon) != NONE ? pylon : null;
    }

    /**
     * The bundle this state is, when it points the way a pylon's claws face.
     */
    public static PylonBundle of(BlockState state, Direction facing)
    {
        if (!state.hasProperty(AmethystClusterBlock.FACING) || state.getValue(AmethystClusterBlock.FACING) != facing)
            return NONE;
        return ofBlock(state.getBlock());
    }

    /**
     * Whether the bundle at that position grew Clear.
     */
    public static boolean isClear(BlockGetter level, BlockPos bundle)
    {
        return CrystalBundles.habitOf(level.getBlockState(bundle)) == CrystalHabit.CLEAR;
    }

    /**
     * Whether the bundle at that position was scoured, so its field holds back the seep.
     */
    public static boolean isScoured(BlockGetter level, BlockPos bundle)
    {
        return CrystalBundles.habitOf(level.getBlockState(bundle)) == CrystalHabit.SCOURED;
    }

    /**
     * A Clear bundle carries nothing inside any seep.
     */
    public static boolean isDarkInSeep(ServerLevel level, BlockPos bundle)
    {
        return isClear(level, bundle) && VoidSeep.strengthAt(level, bundle) > 0;
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
