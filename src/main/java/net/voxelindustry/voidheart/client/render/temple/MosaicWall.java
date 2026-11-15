package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.UnderwayMosaicBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;
import org.jspecify.annotations.Nullable;

/**
 * Where one temple's mosaic is in the world: the block of its top left tile as someone facing it sees it, the way its face looks, and how each tile fared.
 * Its picture runs rightward from there, which is counter-clockwise from the way it faces, and down.
 */
public final class MosaicWall
{
    public static final int WHOLE = 0;
    public static final int CHIPPED = 1;
    public static final int FALLEN = 2;

    private final BlockPos origin;
    private final Direction facing;
    private final int[] wear;

    private MosaicWall(BlockPos origin, Direction facing, int[] wear)
    {
        this.origin = origin;
        this.facing = facing;
        this.wear = wear;
    }

    /**
     * Finds the mosaic around a block of it seen from the side it shows, or a block of bare bed where one fell.
     *
     * @return the mosaic, or null when the blocks there do not make one as a temple lays it
     */
    public static @Nullable MosaicWall find(BlockGetter level, BlockPos seen, Direction face)
    {
        // The nearest tile still on the wall tells where the whole picture is
        var origin = MosaicDoor.mosaicOrigin(level, seen, face);
        if (origin == null)
            return null;
        var wall = new MosaicWall(origin, face, new int[UnderwayMosaicBlock.TILES]);
        if (!wall.contains(seen, face) || !wall.refresh(level))
            return null;
        return wall;
    }

    /**
     * Reads again how each tile fared. A place without a tile counts as fallen, whether bare bed or anything the temple was built around.
     *
     * @return false when a tile is not where the picture needs it, as when the picture would be mirrored, or none is left
     */
    public boolean refresh(BlockGetter level)
    {
        int tiles = 0;
        for (int row = 0; row < UnderwayTemple.ROWS; row++)
        {
            for (int column = 0; column < UnderwayTemple.COLUMNS; column++)
            {
                var state = level.getBlockState(tile(column, row));
                int index = row * UnderwayTemple.COLUMNS + column;
                if (state.is(VoidHeartBlocks.UNDERWAY_MOSAIC))
                {
                    if (state.getValue(UnderwayMosaicBlock.TILE) != index)
                        return false;
                    wear[index] = state.getValue(UnderwayMosaicBlock.CHIPPED) ? CHIPPED : WHOLE;
                }
                else
                    wear[index] = FALLEN;
                tiles += wear[index] == FALLEN ? 0 : 1;
            }
        }
        return tiles > 0;
    }

    public BlockPos tile(int column, int row)
    {
        return origin.relative(facing.getCounterClockWise(), column).below(row);
    }

    public boolean contains(BlockPos pos, Direction face)
    {
        if (face != facing)
            return false;
        var right = facing.getCounterClockWise();
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        int along = dx * right.getStepX() + dz * right.getStepZ();
        int out = dx * facing.getStepX() + dz * facing.getStepZ();
        int down = origin.getY() - pos.getY();
        return out == 0 && along >= 0 && along < UnderwayTemple.COLUMNS && down >= 0 && down < UnderwayTemple.ROWS;
    }

    public int[] wear()
    {
        return wear;
    }

    public Direction facing()
    {
        return facing;
    }

    public Direction right()
    {
        return facing.getCounterClockWise();
    }

    /** The picture's top left corner on the face of the wall. */
    public Vec3 corner()
    {
        var right = right();
        return Vec3.atCenterOf(origin)
                .add(facing.getStepX() * 0.5, 0, facing.getStepZ() * 0.5)
                .subtract(right.getStepX() * 0.5, -0.5, right.getStepZ() * 0.5);
    }

    /** The middle of the picture on the face of the wall. */
    public Vec3 middle()
    {
        return toWorld(MosaicArt.WIDTH / 2D, MosaicArt.HEIGHT / 2D, 0);
    }

    /** @return a place given in the picture's pixels, rightward, downward and out of the wall, in the world */
    public Vec3 toWorld(double u, double v, double h)
    {
        var corner = corner();
        var right = right();
        double x = corner.x + (right.getStepX() * u + facing.getStepX() * h) / 16;
        double z = corner.z + (right.getStepZ() * u + facing.getStepZ() * h) / 16;
        return new Vec3(x, corner.y - v / 16, z);
    }

    /** @return a place in the world in the picture's pixels: rightward, downward and out of the wall */
    public double[] toPicture(Vec3 world)
    {
        var corner = corner();
        var right = right();
        double dx = world.x - corner.x;
        double dz = world.z - corner.z;
        return new double[]{(dx * right.getStepX() + dz * right.getStepZ()) * 16, (corner.y - world.y) * 16, (dx * facing.getStepX() + dz * facing.getStepZ()) * 16};
    }

    /** What keeps this temple's mosaic waking its own way. */
    public long seed()
    {
        return origin.asLong() * 0x9E3779B97F4A7C15L ^ facing.ordinal();
    }

    public boolean sameAs(MosaicWall other)
    {
        return origin.equals(other.origin) && facing == other.facing;
    }
}
