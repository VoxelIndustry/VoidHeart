package net.voxelindustry.voidheart.common.world.underway;

/**
 * A square shaft of steel stairs wound around a dead lift's cage, or around a concrete newel. Four flights of four steps make a turn, so every 16 blocks the stairs are back at the corner they left, where each level has its landing.
 */
final class StairShaft
{
    static final int SIZE = 8;

    private static final int LAST = SIZE - 1;
    private static final int LANES = 2;
    private static final int RISE = 4;
    private static final int SIDES = 4;
    private static final int HEADROOM = 4;
    private static final int[] CLIMB_U = { 1, 0, -1, 0 };
    private static final int[] CLIMB_V = { 0, 1, 0, -1 };

    private StairShaft()
    {
    }

    static void paint(UnderwayBrush brush, UnderwayShaft shaft)
    {
        int a0 = shaft.a0();
        int c0 = shaft.c0();
        int bottomFloor = shaft.bottomFloor();
        int topFloor = shaft.topFloor();
        boolean highAlong = shaft.highAlong();
        boolean highAcross = shaft.highAcross();
        int top = topFloor + HEADROOM;
        brush.room(a0, c0, a0 + LAST, c0 + LAST, bottomFloor, top - bottomFloor, UnderwayMaterial.FLOOR);
        brush.fill(a0 + LANES, bottomFloor + 1, c0 + LANES, a0 + LAST - LANES, top, c0 + LAST - LANES, shaft.lift() ? UnderwayMaterial.GRATING : UnderwayMaterial.SHELL);
        if (shaft.lift())
        {
            brush.fill(a0 + LANES + 1, bottomFloor + 1, c0 + LANES + 1, a0 + LAST - LANES - 1, top, c0 + LAST - LANES - 1, UnderwayMaterial.AIR);
            brush.fill(a0 + LANES + 1, shaft.carFloor(), c0 + LANES + 1, a0 + LAST - LANES - 1, shaft.carFloor(), c0 + LAST - LANES - 1, UnderwayMaterial.STEEL);
        }

        int flights = (topFloor - bottomFloor) / RISE;
        for (int flight = 0; flight <= flights; flight++)
        {
            int side = flight % SIDES;
            int base = bottomFloor + flight * RISE;
            int climbAlong = highAlong ? -CLIMB_U[side] : CLIMB_U[side];
            int climbAcross = highAcross ? -CLIMB_V[side] : CLIMB_V[side];
            for (int lane = 0; lane < LANES; lane++)
            {
                for (int run = 0; run < LANES + RISE; run++)
                {
                    int u = u(side, run, lane);
                    int v = v(side, run, lane);
                    int along = highAlong ? a0 + LAST - u : a0 + u;
                    int across = highAcross ? c0 + LAST - v : c0 + v;
                    if (run < LANES)
                    {
                        if (flight > 0)
                            brush.set(along, base, across, UnderwayMaterial.GRATING);
                    }
                    else if (flight < flights)
                        brush.set(along, base + 1 + run - LANES, across, brush.stairs(climbAlong, climbAcross));
                }
            }
        }
    }

    private static int u(int side, int run, int lane)
    {
        return switch (side)
        {
            case 0 -> run;
            case 1 -> LAST - lane;
            case 2 -> LAST - run;
            default -> lane;
        };
    }

    private static int v(int side, int run, int lane)
    {
        return switch (side)
        {
            case 0 -> lane;
            case 1 -> run;
            case 2 -> LAST - lane;
            default -> LAST - run;
        };
    }
}
