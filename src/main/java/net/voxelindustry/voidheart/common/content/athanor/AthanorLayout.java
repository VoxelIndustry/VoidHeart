package net.voxelindustry.voidheart.common.content.athanor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The parts gathered around an athanor core: its alembic above, a crucible on any of its four sides, and the aspect intake below.
 */
public record AthanorLayout(boolean alembic, List<Direction> crucibles, boolean intake)
{
    public static final int MAX_CRUCIBLES = 4;

    public enum Part
    {
        ALEMBIC,
        CRUCIBLE,
        INTAKE,
        NONE
    }

    public static AthanorLayout scan(BlockPos core, Function<BlockPos, Part> partAt)
    {
        var crucibles = new ArrayList<Direction>();
        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (partAt.apply(core.relative(side)) == Part.CRUCIBLE)
                crucibles.add(side);
        }
        return new AthanorLayout(partAt.apply(core.above()) == Part.ALEMBIC, List.copyOf(crucibles), partAt.apply(core.below()) == Part.INTAKE);
    }

    /**
     * Brewing needs the alembic. Crucibles and the intake are only needed by the recipes asking for reagents and aspects.
     */
    public boolean canBrew()
    {
        return alembic;
    }
}
