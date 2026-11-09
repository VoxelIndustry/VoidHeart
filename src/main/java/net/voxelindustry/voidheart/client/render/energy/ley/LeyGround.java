package net.voxelindustry.voidheart.client.render.energy.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.energy.ley.LeyGrowerBlock;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBlock;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardBlock;

/**
 * Finds the top of the ground or floor near a height, so ley effects lie on terrain without reading the heightmap, which would put them on a roof.
 */
final class LeyGround
{
    private static final int ABOVE = 3;
    private static final int BELOW = 6;

    private LeyGround()
    {
    }

    /**
     * The height of the first walkable top at x, z, searching from a few blocks above reference down to a few below, or NaN if there is none.
     */
    static double surface(Level level, double x, double z, int reference)
    {
        var cursor = new BlockPos.MutableBlockPos(Mth.floor(x), reference + ABOVE, Mth.floor(z));
        boolean openAbove = shape(level, cursor).isEmpty();
        for (int y = reference + ABOVE - 1; y >= reference - BELOW; y--)
        {
            cursor.setY(y);
            var shape = shape(level, cursor);
            if (!shape.isEmpty() && openAbove)
                return y + shape.max(Direction.Axis.Y);
            openAbove = shape.isEmpty();
        }
        return Double.NaN;
    }

    /**
     * Ley's own blocks stand on the ground rather than being it, so a seam runs to a stone's foot instead of climbing it.
     */
    private static VoxelShape shape(Level level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof LeyStoneBlock || state.getBlock() instanceof LeyWardBlock || state.getBlock() instanceof LeyGrowerBlock)
            return Shapes.empty();
        return state.getCollisionShape(level, pos);
    }
}
