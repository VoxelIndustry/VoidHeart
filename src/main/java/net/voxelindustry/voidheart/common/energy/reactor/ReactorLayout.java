package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shape of a Contravoid reactor: a closed 3x3x3 shell of containment around the core, with the two intakes at the centers of opposite sides, along
 * the reactor's axis (X or Z). The other four face centers carry the slits the core shows through, running along the axis. Pure, and unit tested.
 * Offsets are from the core.
 */
public final class ReactorLayout
{
    public enum Part
    {
        CONTAINMENT,
        INTAKE
    }

    /**
     * A slit cut through a containment block: the axis it passes through the block along, and the axis it runs along across the face.
     */
    public enum Slit implements StringRepresentable
    {
        NONE("none", null, null),
        Y_ALONG_X("y_along_x", Direction.Axis.Y, Direction.Axis.X),
        Y_ALONG_Z("y_along_z", Direction.Axis.Y, Direction.Axis.Z),
        Z_ALONG_X("z_along_x", Direction.Axis.Z, Direction.Axis.X),
        X_ALONG_Z("x_along_z", Direction.Axis.X, Direction.Axis.Z);

        private final String         name;
        private final Direction.Axis through;
        private final Direction.Axis along;

        Slit(String name, Direction.Axis through, Direction.Axis along)
        {
            this.name = name;
            this.through = through;
            this.along = along;
        }

        public Direction.Axis through()
        {
            return through;
        }

        public Direction.Axis along()
        {
            return along;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }

    public record Expected(BlockPos offset, Part part, Slit slit)
    {
    }

    private ReactorLayout()
    {
    }

    /**
     * Every block of the shell for a reactor along this axis, intakes included.
     */
    public static List<Expected> shell(Direction.Axis axis)
    {
        var out = new ArrayList<Expected>(26);
        for (var dx = -1; dx <= 1; dx++)
        {
            for (var dy = -1; dy <= 1; dy++)
            {
                for (var dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;
                    var offset = new BlockPos(dx, dy, dz);
                    out.add(new Expected(offset, isIntake(axis, offset) ? Part.INTAKE : Part.CONTAINMENT, slitAt(axis, offset)));
                }
            }
        }
        return out;
    }

    public static List<BlockPos> intakes(Direction.Axis axis)
    {
        return axis == Direction.Axis.Z ? List.of(new BlockPos(0, 0, -1), new BlockPos(0, 0, 1)) : List.of(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
    }

    public static boolean isIntake(Direction.Axis axis, BlockPos offset)
    {
        return intakes(axis).contains(offset);
    }

    /**
     * Whether an offset from the core is part of its shell.
     */
    public static boolean inShell(BlockPos offset)
    {
        return Math.abs(offset.getX()) <= 1 && Math.abs(offset.getY()) <= 1 && Math.abs(offset.getZ()) <= 1 && !offset.equals(BlockPos.ZERO);
    }

    /**
     * The slit a containment block shows once the reactor along this axis stands whole: only the four face centers away from the intakes have one.
     */
    public static Slit slitAt(Direction.Axis axis, BlockPos offset)
    {
        var x = offset.getX();
        var y = offset.getY();
        var z = offset.getZ();
        if (Math.abs(x) + Math.abs(y) + Math.abs(z) != 1)
            return Slit.NONE;
        if (y != 0)
            return axis == Direction.Axis.Z ? Slit.Y_ALONG_Z : Slit.Y_ALONG_X;
        if (axis == Direction.Axis.X && z != 0)
            return Slit.Z_ALONG_X;
        if (axis == Direction.Axis.Z && x != 0)
            return Slit.X_ALONG_Z;
        return Slit.NONE;
    }

    /**
     * The axis already chosen by an intake standing at a center of a side, X when none does yet or both axes have one.
     */
    public static Direction.Axis inferAxis(Predicate<BlockPos> isIntakeAt)
    {
        var onX = intakes(Direction.Axis.X).stream().filter(isIntakeAt).count();
        var onZ = intakes(Direction.Axis.Z).stream().filter(isIntakeAt).count();
        return onZ > onX ? Direction.Axis.Z : Direction.Axis.X;
    }

    /**
     * The blocks of the shell not standing as they should.
     *
     * @param partAt what stands at an offset, or null for anything else
     */
    public static List<Expected> missing(Direction.Axis axis, java.util.function.Function<BlockPos, Part> partAt)
    {
        var out = new ArrayList<Expected>();
        for (var expected : shell(axis))
        {
            if (partAt.apply(expected.offset()) != expected.part())
                out.add(expected);
        }
        return out;
    }
}
