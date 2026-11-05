package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * @param block  what the player places
     * @param formed the piece of the sculpted base that takes its place while the armillary is formed
     */
    public record BasePart(BlockPos pos, Block block, BlockState formed)
    {
        public boolean isPresentIn(BlockGetter level)
        {
            var state = level.getBlockState(pos);
            return state.is(block) || state == formed;
        }
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
        for (var facing : Direction.Plane.HORIZONTAL)
        {
            var side = core.relative(facing);
            parts.add(new BasePart(side, VoidHeartBlocks.CUT_RAVENOUS_GOLD, VoidHeartBlocks.ARMILLARY_BASE_SIDE.defaultBlockState().setValue(ArmillaryBaseBlock.FACING, facing)));
            parts.add(new BasePart(side.relative(facing.getClockWise()), VoidHeartBlocks.VOIDSTONE_CHISELED, VoidHeartBlocks.ARMILLARY_BASE_CORNER.defaultBlockState().setValue(ArmillaryBaseBlock.FACING, facing)));
        }
        return parts;
    }

    public static boolean isBaseComplete(BlockGetter level, BlockPos core)
    {
        for (var part : base(core))
        {
            if (!part.isPresentIn(level))
                return false;
        }
        return true;
    }

    /**
     * Turns the blocks around a core into the sculpted base, or back into what the player placed. Anything else standing there is left alone.
     *
     * @return whether anything changed
     */
    public static boolean shapeBase(Level level, BlockPos core, boolean formed)
    {
        boolean changed = false;
        for (var part : base(core))
        {
            var wanted = formed ? part.formed() : part.block().defaultBlockState();
            if (part.isPresentIn(level) && level.getBlockState(part.pos()) != wanted)
            {
                level.setBlockAndUpdate(part.pos(), wanted);
                changed = true;
            }
        }
        return changed;
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
