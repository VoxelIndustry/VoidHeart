package net.voxelindustry.voidheart.common.world.underway;

import java.util.ArrayList;
import java.util.List;

/**
 * What changes in the world when a district's hatch opens: its two leaves draw back from the line where they meet, a column of plates at a time, then the shaft's stairs are carried up through the gap to the district's floor.
 * The coaming stays, so only the stairs' inner lane comes up. The dead lift's well is covered, so nobody steps into it from above.
 */
public final class HatchOpening
{
    /** How many columns of plates each leaf has, and so how many steps the leaves take to draw back. */
    public static final int STAGES = StairShaft.SIZE / 2 - 1;

    private static final int FIRST = 1;
    private static final int LAST = StairShaft.LAST - 1;
    private static final int BOTTOM = UnderwayLevels.TOP_Y + 1;

    public enum Part
    {
        AIR,
        STEP,
        LANDING,
        CAP
    }

    /** @param climb the compass direction a step rises towards, 0 for the other parts */
    public record Change(int x, int y, int z, Part part, int climb)
    {
    }

    private HatchOpening()
    {
    }

    /**
     * @param stage 0 for the plates along the line where the leaves meet, up to {@link #STAGES} - 1 for those against the coaming
     */
    public static List<Change> retreat(UnderwayFort fort, int stage)
    {
        var changes = new ArrayList<Change>();
        int west = StairShaft.SIZE / 2 - 1 - stage;
        int east = StairShaft.SIZE / 2 + stage;
        for (int y = UnderwayLevels.HATCH_FLOOR; y >= BOTTOM; y--)
        {
            for (int v = FIRST; v <= LAST; v++)
            {
                changes.add(new Change(fort.hatchX() + west, y, fort.hatchZ() + v, Part.AIR, 0));
                changes.add(new Change(fort.hatchX() + east, y, fort.hatchZ() + v, Part.AIR, 0));
            }
        }
        return changes;
    }

    /** The steps, landing and cover laid once the leaves are gone. */
    public static List<Change> stairs(UnderwayFort fort)
    {
        var shaft = shaft(fort);
        var frame = fort.frame();
        var changes = new ArrayList<Change>();
        int flights = (shaft.topFloor() - shaft.bottomFloor()) / StairShaft.RISE;
        for (int flight = flights - 1; flight <= flights; flight++)
        {
            int side = flight % StairShaft.SIDES;
            int base = shaft.bottomFloor() + flight * StairShaft.RISE;
            int climb = frame.compass(shaft.highAlong() ? -StairShaft.CLIMB_U[side] : StairShaft.CLIMB_U[side], shaft.highAcross() ? -StairShaft.CLIMB_V[side] : StairShaft.CLIMB_V[side]);
            for (int lane = 0; lane < StairShaft.LANES; lane++)
            {
                for (int run = 0; run < StairShaft.LANES + StairShaft.RISE; run++)
                {
                    boolean landing = run < StairShaft.LANES;
                    int y = landing ? base : base + 1 + run - StairShaft.LANES;
                    int u = StairShaft.u(side, run, lane);
                    int v = StairShaft.v(side, run, lane);
                    if (landing == (flight < flights) || y < BOTTOM || isUnderCoaming(u) || isUnderCoaming(v))
                        continue;
                    changes.add(change(frame, shaft, u, v, y, landing ? Part.LANDING : Part.STEP, landing ? 0 : climb));
                }
            }
        }
        if (shaft.lift())
        {
            for (int u = StairShaft.LANES + 1; u < StairShaft.LAST - StairShaft.LANES; u++)
            {
                for (int v = StairShaft.LANES + 1; v < StairShaft.LAST - StairShaft.LANES; v++)
                    changes.add(change(frame, shaft, u, v, UnderwayLevels.TOP_Y, Part.CAP, 0));
            }
        }
        return changes;
    }

    private static boolean isUnderCoaming(int local)
    {
        return local < FIRST || local > LAST;
    }

    private static Change change(UnderwayFrame frame, UnderwayShaft shaft, int u, int v, int y, Part part, int climb)
    {
        int along = shaft.highAlong() ? shaft.a0() + StairShaft.LAST - u : shaft.a0() + u;
        int across = shaft.highAcross() ? shaft.c0() + StairShaft.LAST - v : shaft.c0() + v;
        return new Change(frame.x(along, across), y, frame.z(along, across), part, climb);
    }

    private static UnderwayShaft shaft(UnderwayFort fort)
    {
        for (var shaft : fort.shafts())
        {
            if (shaft.topFloor() == UnderwayLevels.HATCH_FLOOR && shaft.a0() == fort.hatchAlong() && shaft.c0() == fort.hatchAcross())
                return shaft;
        }
        throw new IllegalStateException("No hatch shaft under " + fort.pos());
    }
}
