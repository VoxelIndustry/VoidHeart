package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A core in the middle of a 3x3 base, with the three blocks above it left free for the rings.
 */
public final class ArmillaryLayout
{
    public static final int RING_LAYERS = 3;
    public static final int HUB_HEIGHT  = 2;

    public record BasePart(BlockPos pos, Block block)
    {
    }

    private ArmillaryLayout()
    {
    }

    public static BlockPos hub(BlockPos core)
    {
        return core.above(HUB_HEIGHT);
    }

    public static List<BasePart> base(BlockPos core)
    {
        var parts = new ArrayList<BasePart>(8);
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                if (x == 0 && z == 0)
                    continue;
                parts.add(new BasePart(core.offset(x, 0, z), x != 0 && z != 0 ? VoidHeartBlocks.VOIDSTONE_CHISELED : VoidHeartBlocks.CUT_RAVENOUS_GOLD));
            }
        }
        return parts;
    }

    public static boolean isBaseComplete(BlockGetter level, BlockPos core)
    {
        for (var part : base(core))
        {
            if (!level.getBlockState(part.pos()).is(part.block()))
                return false;
        }
        return true;
    }

    /**
     * What stops the rings from turning, which grass or snow never does.
     */
    public static List<BlockPos> blockers(BlockGetter level, BlockPos core)
    {
        var blockers = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(core.offset(-1, 1, -1), core.offset(1, RING_LAYERS, 1)))
        {
            var state = level.getBlockState(pos);
            if (!state.is(VoidHeartBlocks.ARMILLARY_HUB) && !state.canBeReplaced())
                blockers.add(pos.immutable());
        }
        return blockers;
    }

    public static boolean isComplete(BlockGetter level, BlockPos core)
    {
        return isBaseComplete(level, core) && blockers(level, core).isEmpty();
    }
}
