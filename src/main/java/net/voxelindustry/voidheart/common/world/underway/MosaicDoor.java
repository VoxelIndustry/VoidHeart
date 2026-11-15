package net.voxelindustry.voidheart.common.world.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.UnderwayMosaicBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * Where a Wraith Veil opens a way through a temple's mosaic: three tiles wide under the cracked world at its middle, down to the dais.
 *
 * @param origin the block of the mosaic's top left tile as someone facing it sees it
 * @param facing the way the mosaic's face looks, into the temple
 */
public record MosaicDoor(BlockPos origin, Direction facing)
{
    public static final int FIRST_COLUMN = 6;
    public static final int COLUMNS = 3;
    /** The row of the picture at the door's top, the one holding the world. */
    public static final int TOP_ROW = 5;
    /** One below the picture's bottom row, the wall over the dais. */
    public static final int BOTTOM_ROW = UnderwayTemple.ROWS;
    public static final int ROWS = BOTTOM_ROW - TOP_ROW + 1;

    /**
     * Finds the mosaic around a block of it, or of the wall just below it, seen from the side it shows.
     *
     * @return where its top left tile is, or null when no tile of a mosaic is near
     */
    public static @Nullable BlockPos mosaicOrigin(BlockGetter level, BlockPos seen, Direction face)
    {
        if (!face.getAxis().isHorizontal())
            return null;
        var right = face.getCounterClockWise();
        for (int ring = 0; ring <= UnderwayTemple.COLUMNS; ring++)
        {
            for (int k = -ring; k <= ring; k++)
            {
                for (int j = -Math.min(ring, UnderwayTemple.ROWS); j <= Math.min(ring, UnderwayTemple.ROWS); j++)
                {
                    if (Math.max(Math.abs(k), Math.abs(j)) != ring)
                        continue;
                    var candidate = seen.relative(right, k).above(j);
                    var state = level.getBlockState(candidate);
                    if (state.is(VoidHeartBlocks.UNDERWAY_MOSAIC))
                    {
                        int tile = state.getValue(UnderwayMosaicBlock.TILE);
                        return candidate.relative(right, -(tile % UnderwayTemple.COLUMNS)).above(tile / UnderwayTemple.COLUMNS);
                    }
                }
            }
        }
        return null;
    }

    /** @return the door of the mosaic a block faces belongs to, or null when that block is not in one */
    public static @Nullable MosaicDoor at(BlockGetter level, BlockPos clicked, Direction face)
    {
        // The door's bottom row lies under the picture, where no tile is
        var origin = mosaicOrigin(level, clicked, face);
        if (origin == null)
            origin = mosaicOrigin(level, clicked.above(), face);
        if (origin == null)
            return null;
        var door = new MosaicDoor(origin, face);
        return door.contains(clicked) ? door : null;
    }

    public Direction right()
    {
        return facing.getCounterClockWise();
    }

    /** @return the block of the wall at a place of the picture, counted from its top left */
    public BlockPos tile(int column, int row)
    {
        return origin.relative(right(), column).below(row);
    }

    public boolean contains(BlockPos pos)
    {
        var right = right();
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        int along = dx * right.getStepX() + dz * right.getStepZ() - FIRST_COLUMN;
        int out = dx * facing.getStepX() + dz * facing.getStepZ();
        int down = origin.getY() - pos.getY() - TOP_ROW;
        return out == 0 && along >= 0 && along < COLUMNS && down >= 0 && down < ROWS;
    }

    /** The middle of the door on the face of the wall. */
    public Vec3 center()
    {
        var top = Vec3.atCenterOf(tile(FIRST_COLUMN + COLUMNS / 2, TOP_ROW));
        return top.add(facing.getStepX() * 0.5, -(ROWS - 1) / 2D, facing.getStepZ() * 0.5);
    }

    /** Where someone stands who came back out of it, on the dais facing the temple. */
    public Vec3 threshold()
    {
        var feet = tile(FIRST_COLUMN + COLUMNS / 2, BOTTOM_ROW).relative(facing);
        return Vec3.atBottomCenterOf(feet);
    }

    /** @return the yaw of someone facing the temple from the door */
    public float outwardYaw()
    {
        return facing.toYRot();
    }

    /** The space in front of the door that a body walking into it reaches, a hair deep. */
    public AABB mouth(double depth)
    {
        var center = center();
        double halfAlong = COLUMNS / 2D, halfUp = ROWS / 2D;
        var right = right();
        double rx = Math.abs(right.getStepX()) * halfAlong, rz = Math.abs(right.getStepZ()) * halfAlong;
        double fx = facing.getStepX() * depth, fz = facing.getStepZ() * depth;
        return new AABB(center.x - rx, center.y - halfUp, center.z - rz, center.x + rx, center.y + halfUp, center.z + rz)
                .expandTowards(fx, 0, fz);
    }

    /** What tells one temple's door from another's. */
    public long key()
    {
        return origin.asLong();
    }
}
