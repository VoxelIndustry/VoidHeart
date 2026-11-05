package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player comes out: beside the armillary, or where it used to stand if it was broken while they were away.
 */
final class ArmillaryLanding
{
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 5;
    private static final int[] HEIGHTS  = { 0, 1, -1, 2, -2, 3, -3 };

    private ArmillaryLanding()
    {
    }

    static Vec3 find(ServerLevel level, BlockPos armillary)
    {
        for (int radius = MIN_RADIUS; radius <= MAX_RADIUS; radius++)
        {
            for (int x = -radius; x <= radius; x++)
            {
                for (int z = -radius; z <= radius; z++)
                {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius)
                        continue;
                    for (int height : HEIGHTS)
                    {
                        var feet = armillary.offset(x, height, z);
                        if (canStand(level, feet))
                            return Vec3.atBottomCenterOf(feet);
                    }
                }
            }
        }

        var column = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, armillary);
        return Vec3.atBottomCenterOf(column.getY() > level.getMinY() ? column : armillary.above());
    }

    private static boolean canStand(ServerLevel level, BlockPos feet)
    {
        var below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && isOpen(level, feet) && isOpen(level, feet.above());
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
