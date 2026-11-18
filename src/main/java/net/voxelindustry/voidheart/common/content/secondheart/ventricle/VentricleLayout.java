package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Twin Ventricle's shape, around its heart cradle: an open dais nine blocks long and three wide, all at the cradle's level. The cradle sits
 * in a 3x3 dais of polished voidstone. On each side along one axis a ventricle basin stands in a 3x3 ring of veined walls, rubrum-veined on the
 * essence side and azurium-veined on the Contravoid side, with a Ravenous Gold valve as the ring's piece facing the cradle.
 * <p>
 * Pure, so the shape is tested without a world.
 */
public final class VentricleLayout
{
    public enum Part
    {
        DAIS,
        VALVE,
        BASIN,
        RUBRUM_WALL,
        AZURIUM_WALL
    }

    public record Expected(BlockPos offset, Part part)
    {
    }

    /**
     * @param axis          the line the two ventricles lie on
     * @param rubrumPositive whether the essence side is toward positive x or z
     */
    public record Orientation(Direction.Axis axis, boolean rubrumPositive)
    {
        public static final List<Orientation> ALL = List.of(
                new Orientation(Direction.Axis.X, true), new Orientation(Direction.Axis.X, false),
                new Orientation(Direction.Axis.Z, true), new Orientation(Direction.Axis.Z, false));

        public BlockPos along(int step, int across)
        {
            return axis == Direction.Axis.X ? new BlockPos(step, 0, across) : new BlockPos(across, 0, step);
        }

        public BlockPos basin(boolean essence)
        {
            return along(essence == rubrumPositive ? 3 : -3, 0);
        }
    }

    public static final int BLOCKS = 26;

    private VentricleLayout()
    {
    }

    /**
     * Every block but the cradle, from the cradle.
     */
    public static List<Expected> expected(Orientation orientation)
    {
        var parts = new ArrayList<Expected>(BLOCKS);
        for (int step = -1; step <= 1; step++)
            for (int across = -1; across <= 1; across++)
                if (step != 0 || across != 0)
                    parts.add(new Expected(orientation.along(step, across), Part.DAIS));

        for (int side : new int[]{1, -1})
        {
            var wall = (side > 0) == orientation.rubrumPositive() ? Part.RUBRUM_WALL : Part.AZURIUM_WALL;
            for (int step = 2; step <= 4; step++)
                for (int across = -1; across <= 1; across++)
                {
                    Part part;
                    if (step == 3 && across == 0)
                        part = Part.BASIN;
                    else if (step == 2 && across == 0)
                        part = Part.VALVE;
                    else
                        part = wall;
                    parts.add(new Expected(orientation.along(step * side, across), part));
                }
        }
        return parts;
    }

    public static List<Expected> missing(Orientation orientation, Function<BlockPos, @Nullable Part> partAt)
    {
        return expected(orientation).stream().filter(expected -> partAt.apply(expected.offset()) != expected.part()).toList();
    }

    /**
     * The orientation the built blocks fit best, the one missing the fewest, so a half-built chamber still shows what it lacks.
     */
    public static Orientation bestFit(Function<BlockPos, @Nullable Part> partAt)
    {
        Orientation best = Orientation.ALL.getFirst();
        int fewest = Integer.MAX_VALUE;
        for (var orientation : Orientation.ALL)
        {
            int missing = missing(orientation, partAt).size();
            if (missing < fewest)
            {
                fewest = missing;
                best = orientation;
            }
        }
        return best;
    }
}
