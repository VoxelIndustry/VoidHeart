package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.ley.LeyEdge;

/**
 * Where a stone bends its spur off a line: the point on the line nearest the stone, and the line's two nodes. Flow on the line runs from {@code fromNode}.
 */
public record SpurTap(double x, double z, long fromNode, long toNode)
{
    public static SpurTap of(LeyEdge edge, double px, double pz)
    {
        double dx = edge.toX() - edge.fromX();
        double dz = edge.toZ() - edge.fromZ();
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq == 0 ? 0 : Math.clamp(((px - edge.fromX()) * dx + (pz - edge.fromZ()) * dz) / lengthSq, 0, 1);
        return new SpurTap(edge.fromX() + dx * t, edge.fromZ() + dz * t, edge.from(), edge.to());
    }

    /**
     * The block on the line that claims for the spur, so the line's flow and its upstream order are read where the spur really leaves it.
     */
    public BlockPos claimant(int y)
    {
        return BlockPos.containing(x, y, z);
    }

    public double distanceTo(BlockPos pos)
    {
        return Math.hypot(pos.getX() + 0.5 - x, pos.getZ() + 0.5 - z);
    }
}
