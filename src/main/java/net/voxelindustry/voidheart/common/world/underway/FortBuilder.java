package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.BALLAST;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.ENTRY_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.HATCH_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_FLOOR;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.LIVING_HEIGHT;
import static net.voxelindustry.voidheart.common.world.underway.UnderwayLevels.PLATFORM_FLOOR;

/**
 * Gathers a fort's rooms as its planner lays them out, and holds what every kind of fort shares: rows of bays, the defended way in from the hatch, rails and lamps.
 */
final class FortBuilder
{
    static final int DOOR_HEIGHT = 3;
    static final int BAY_DEPTH = 12;

    private static final int CELL = DistrictPos.CELL;
    private static final int GRID = DistrictPos.CELLS;
    private static final int CELL_END = CELL - 1;
    private static final int LAST_CELL = GRID - 2;
    private static final int PASSAGE_LOW = 3;
    private static final int PASSAGE_MIDDLE = 4;
    private static final int PASSAGE_HIGH = 5;
    private static final int HATCH_REACH = 6;
    private static final int HATCH_TRIES = 64;
    private static final int MIN_WALK = 3;
    private static final int PIT_DEPTH = 2;
    private static final int TRIPWIRE_PAST_DOOR = 2;
    private static final int MAX_SIDE_ROOMS = 4;
    private static final int SIDE_ROOM_PERCENT = 50;
    private static final int TALL_BAY = 7;
    private static final int PERCENT = 100;
    private static final int CAVED_IN_PERCENT = 30;
    private static final int BREACH_AMONG = 8;
    private static final int BREACH_SPILL = 4;
    private static final int LAMP_EVERY = 16;
    private static final int UNREACHED = Integer.MAX_VALUE;
    /** Rooms worth locking, whose doorway is a shutter that only rises on power. */
    private static final EnumSet<UnderwayRoomKind> SHUTTERED = EnumSet.of(UnderwayRoomKind.ARMOURY, UnderwayRoomKind.STORES, UnderwayRoomKind.COMMAND_POST, UnderwayRoomKind.ARCHIVE,
            UnderwayRoomKind.LABORATORY);
    private static final UnderwayRoomKind[] SIDE_ROOMS = { UnderwayRoomKind.ARMOURY, UnderwayRoomKind.QUARTERS, UnderwayRoomKind.DECONTAMINATION, UnderwayRoomKind.STORES };

    final long seed;
    final DistrictPos pos;
    final UnderwayFrame frame;
    final UnderwayRuin ruin;

    private final boolean rubble;
    private final UnderwayRoomKind[] program;
    private final int breachedBay;
    private final List<UnderwayRoom> rooms = new ArrayList<>();
    private final List<UnderwayOpening> openings = new ArrayList<>();
    private final List<UnderwayShaft> shafts = new ArrayList<>();
    private final List<UnderwayStroke> strokes = new ArrayList<>();
    private final List<UnderwayUnit> units = new ArrayList<>();
    private final List<UnderwayPropSpot> fixtures = new ArrayList<>();
    private long hash;
    private int nextBay;
    private int bayCount;
    private UnderwayShaft hatch;

    FortBuilder(long seed, DistrictPos pos, boolean alongX, long salt, UnderwayRoomKind[] program)
    {
        this.seed = seed;
        this.pos = pos;
        hash = DaedalusHash.mix(seed, pos.x(), pos.z(), salt);
        frame = new UnderwayFrame(pos.originX(), pos.originZ(), alongX, roll(2) == 0, roll(2) == 0);
        ruin = UnderwayNetwork.ruinAt(seed, pos);
        rubble = UnderwayNetwork.regionRuin(seed, pos) == UnderwayRuin.COLLAPSED;
        breachedBay = ruin == UnderwayRuin.BREACHED ? roll(BREACH_AMONG) : -1;
        this.program = program.clone();
        for (int last = program.length - 1; last > 0; last--)
        {
            int swap = roll(last + 1);
            var kind = this.program[last];
            this.program[last] = this.program[swap];
            this.program[swap] = kind;
        }
    }

    /** @return the next of this fort's own random numbers, below {@code count} */
    int roll(int count)
    {
        hash = DaedalusHash.mix(hash);
        return DaedalusHash.pick(hash, count);
    }

    UnderwayFort build(UnderwayNode node)
    {
        var furnisher = new FortFurnisher(this, node, rooms, openings, shafts, strokes);
        var props = furnisher.furnish();
        units.addAll(furnisher.units());
        // A bulkhead, a lamp or rubble drawn over a room afterwards takes the place of what the furnisher put there
        units.removeIf(unit ->
        {
            var stands = after(unit.along(), unit.y(), unit.across());
            return (unit.kind() == UnderwayUnitKind.RAM_CART ? !stands.isRail() : stands != UnderwayMaterial.AIR)
                    || unit.kind().isTall() && after(unit.along(), unit.y() + 1, unit.across()) != UnderwayMaterial.AIR;
        });
        // The void burnt out what guarded the fort it broke into, and water shorted what stood on a drowned rail level
        if (ruin == UnderwayRuin.BREACHED)
            units.clear();
        else if (ruin == UnderwayRuin.FLOODED)
            units.removeIf(unit -> unit.section() == UnderwaySection.RAIL);
        return new UnderwayFort(pos, node, frame, ruin, hatch.a0(), hatch.c0(), hatch.landingAlong(), hatch.landingAcross(),
                List.copyOf(rooms), List.copyOf(openings), List.copyOf(shafts), List.copyOf(strokes), List.copyOf(props), List.copyOf(units));
    }

    /** @return what a block of a room's air is once every stroke is drawn */
    private UnderwayMaterial after(int along, int y, int across)
    {
        var material = UnderwayMaterial.AIR;
        for (var stroke : strokes)
        {
            if (along >= stroke.a0() && along <= stroke.a1() && y >= stroke.y0() && y <= stroke.y1() && across >= stroke.c0() && across <= stroke.c1()
                    && (stroke.over() == null || stroke.over() == material))
                material = stroke.material();
        }
        return material;
    }

    /** @return what the fort itself hangs on its walls, before anything else is furnished */
    List<UnderwayPropSpot> fixtures()
    {
        return fixtures;
    }

    /** @return the shaft under the hatch, once the entry level is laid out */
    UnderwayShaft hatch()
    {
        return hatch;
    }

    UnderwayRoom room(UnderwayRoomKind kind, int a0, int c0, int a1, int c1, int floorY, int height)
    {
        var room = new UnderwayRoom(kind, Math.min(a0, a1), Math.min(c0, c1), Math.max(a0, a1), Math.max(c0, c1), floorY, height);
        rooms.add(room);
        return room;
    }

    void opening(int a0, int c0, int a1, int c1, int floorY, int height)
    {
        openings.add(new UnderwayOpening(Math.min(a0, a1), Math.min(c0, c1), Math.max(a0, a1), Math.max(c0, c1), floorY, height));
    }

    UnderwayShaft shaft(int a0, int c0, int bottomFloor, int topFloor, boolean highAlong, boolean highAcross, boolean lift, int carFloor)
    {
        var shaft = new UnderwayShaft(a0, c0, bottomFloor, topFloor, highAlong, highAcross, lift, carFloor);
        shafts.add(shaft);
        return shaft;
    }

    void fill(UnderwayMaterial material, int a0, int y0, int c0, int a1, int y1, int c1)
    {
        swap(null, material, a0, y0, c0, a1, y1, c1);
    }

    void inAir(UnderwayMaterial material, int a0, int y0, int c0, int a1, int y1, int c1)
    {
        swap(UnderwayMaterial.AIR, material, a0, y0, c0, a1, y1, c1);
    }

    void swap(UnderwayMaterial over, UnderwayMaterial material, int a0, int y0, int c0, int a1, int y1, int c1)
    {
        strokes.add(new UnderwayStroke(material, over, Math.min(a0, a1), Math.min(y0, y1), Math.min(c0, c1), Math.max(a0, a1), Math.max(y0, y1), Math.max(c0, c1)));
    }

    /**
     * @param runsAlong whether the track runs along the frame, from {@code from} to {@code to} at {@code at} across it
     */
    void rail(boolean trunk, boolean runsAlong, int from, int to, int at, int floorY)
    {
        boolean alongX = runsAlong == frame.alongX();
        var material = trunk
                ? (alongX ? UnderwayMaterial.TRUNK_RAIL_X : UnderwayMaterial.TRUNK_RAIL_Z)
                : (alongX ? UnderwayMaterial.FEEDER_RAIL_X : UnderwayMaterial.FEEDER_RAIL_Z);
        if (runsAlong)
            inAir(material, from, floorY + 1, at, to, floorY + 1, at);
        else
            inAir(material, at, floorY + 1, from, at, floorY + 1, to);
    }

    /** Hangs lamps along a ceiling: every one lit in a sealed fort, otherwise half as many on emergency power. */
    void lamps(boolean runsAlong, int from, int to, int at, int y)
    {
        var lamp = ruin == UnderwayRuin.SEALED ? UnderwayMaterial.LAMP : UnderwayMaterial.EMERGENCY_LAMP;
        int every = ruin == UnderwayRuin.SEALED ? LAMP_EVERY : LAMP_EVERY * 2;
        for (int run = from + LAMP_EVERY / 2; run <= to; run += every)
        {
            if (runsAlong)
                inAir(lamp, run, y, at, run, y, at);
            else
                inAir(lamp, at, y, run, at, y, run);
        }
    }

    /** Two steps from a platform's height down to the track bed, in the trunk's margin beside the platform. */
    void steps(int top, int across, int climb)
    {
        fill(UnderwayMaterial.STEEL, top, BALLAST + 1, across, top, BALLAST + 1, across);
        var stairs = UnderwayMaterial.stairs(frame.compass(climb, 0));
        fill(stairs, top, PLATFORM_FLOOR, across, top, PLATFORM_FLOOR, across);
        fill(stairs, top - climb, BALLAST + 1, across, top - climb, BALLAST + 1, across);
    }

    /** Cuts a door two blocks wide from a shaft's landing through the wall on one side of it. */
    void door(UnderwayShaft shaft, int stepAlong, int stepAcross, int floorY, boolean armoured)
    {
        int a0 = shaft.landingAlong();
        int a1 = a0 + 1;
        int c0 = shaft.landingAcross();
        int c1 = c0 + 1;
        if (stepAlong != 0)
            a0 = a1 = stepAlong > 0 ? shaft.a0() + StairShaft.SIZE : shaft.a0() - 1;
        else
            c0 = c1 = stepAcross > 0 ? shaft.c0() + StairShaft.SIZE : shaft.c0() - 1;
        opening(a0, c0, a1, c1, floorY, armoured ? 2 : DOOR_HEIGHT);
        if (armoured)
            fill(UnderwayMaterial.DOOR, a0, floorY + 1, c0, a1, floorY + 2, c1);
    }

    /**
     * Lines one side of a living gallery with bays, taken in turn from this fort's programme.
     *
     * @param transposed whether the gallery runs across the frame rather than along it
     * @param from       where the row may start along the gallery
     * @param to         where it must end
     * @param wall       the gallery's wall on that side
     * @param outward    1 or -1, the way the bays reach back from the wall
     */
    void bays(boolean transposed, int from, int to, int wall, int outward)
    {
        int run = from;
        while (true)
        {
            var kind = program[nextBay % program.length];
            if (run + kind.bayWidth() - 1 > to)
                kind = UnderwayRoomKind.STORES;
            else
                nextBay++;
            int end = run + kind.bayWidth() - 1;
            if (end > to)
                return;

            int height = kind == UnderwayRoomKind.POWER_PLANT ? TALL_BAY : LIVING_HEIGHT;
            if (bayCount == breachedBay)
                kind = UnderwayRoomKind.BREACH;
            else if (rubble && roll(PERCENT) < CAVED_IN_PERCENT)
                kind = UnderwayRoomKind.CAVED_IN;
            int near = wall + outward;
            int far = wall + outward * BAY_DEPTH;
            int door = (run + end) / 2;
            if (transposed)
                room(kind, near, run, far, end, LIVING_FLOOR, height);
            else
                room(kind, run, near, end, far, LIVING_FLOOR, height);

            if (kind == UnderwayRoomKind.CAVED_IN)
            {
                turned(transposed, null, UnderwayMaterial.RUBBLE, run, LIVING_FLOOR + 1, near, end, LIVING_FLOOR + height, far);
                turned(transposed, UnderwayMaterial.AIR, UnderwayMaterial.RUBBLE, door - 2, LIVING_FLOOR + 1, wall - outward, door + 2, LIVING_FLOOR + 1, wall - outward * 2);
            }
            else if (transposed)
                opening(wall, door, wall, door + 1, LIVING_FLOOR, DOOR_HEIGHT);
            else
                opening(door, wall, door + 1, wall, LIVING_FLOOR, DOOR_HEIGHT);
            if (SHUTTERED.contains(kind))
                turned(transposed, null, UnderwayMaterial.SHUTTER, door, LIVING_FLOOR + 1, wall, door + 1, LIVING_FLOOR + DOOR_HEIGHT, wall);
            if (kind == UnderwayRoomKind.BREACH)
            {
                turned(transposed, null, UnderwayMaterial.VOID, run + 1, LIVING_FLOOR + 1, far + outward, end - 1, LIVING_FLOOR + height - 1, far + outward * 2);
                turned(transposed, UnderwayMaterial.AIR, UnderwayMaterial.VOID, run, LIVING_FLOOR + 1, near, end, LIVING_FLOOR + 2, far);
                turned(transposed, UnderwayMaterial.AIR, UnderwayMaterial.VOID, door - BREACH_SPILL, LIVING_FLOOR + 1, wall - outward, door + BREACH_SPILL, LIVING_FLOOR + 1, wall - outward * BREACH_SPILL);
            }
            run = end + 2;
            bayCount++;
        }
    }

    private void turned(boolean transposed, UnderwayMaterial over, UnderwayMaterial material, int run0, int y0, int deep0, int run1, int y1, int deep1)
    {
        if (transposed)
            swap(over, material, deep0, y0, run0, deep1, y1, run1);
        else
            swap(over, material, run0, y0, deep0, run1, y1, deep1);
    }

    /**
     * Lines a side of a gallery with bays wherever nothing else stands.
     *
     * @param reserved the stretches along the gallery kept clear, each as its first and last block, in rising order
     */
    void bays(boolean transposed, int from, int to, int wall, int outward, int[]... reserved)
    {
        int start = from;
        for (int[] stretch : reserved)
        {
            if (stretch == null)
                continue;
            bays(transposed, start, stretch[0] - 1, wall, outward);
            start = stretch[1] + 1;
        }
        bays(transposed, start, to, wall, outward);
    }

    /**
     * Opens a hatch somewhere over the fort and builds the entry level between the foot of its stairs and the core. The gallery is routed over the districts' lattice of cells, so it bends: every bend is watched by a guard room's embrasure, straight stretches hide pits, and blast doors close both ends.
     *
     * @param core       the shaft the gallery leads to, standing on the lattice
     * @param exitAlong  the side of the core its vestibule lies on, as a step along the frame
     * @param exitAcross the same, as a step across the frame
     */
    void entry(UnderwayShaft core, int exitAlong, int exitAcross)
    {
        int coreI = core.a0() / CELL;
        int coreJ = core.c0() / CELL;
        int vestibule = cell(coreI + exitAlong, coreJ + exitAcross);
        boolean[] taken = new boolean[GRID * GRID];
        taken[cell(coreI, coreJ)] = true;
        taken[vestibule] = true;

        int hatchI;
        int hatchJ;
        int tries = 0;
        do
        {
            if (tries++ == HATCH_TRIES)
                throw new IllegalStateException("No place for a hatch over " + pos);
            hatchI = coreI - HATCH_REACH + roll(HATCH_REACH * 2 + 1);
            hatchJ = coreJ - HATCH_REACH + roll(HATCH_REACH * 2 + 1);
        } while (isOffGrid(hatchI, hatchJ) || isOffGrid(hatchI - 1, hatchJ - 1) || isOffGrid(hatchI + 1, hatchJ + 1) || Math.max(Math.abs(hatchI - coreI), Math.abs(hatchJ - coreJ)) < 2
                || Math.abs(hatchI - vestibule / GRID) + Math.abs(hatchJ - vestibule % GRID) < MIN_WALK);
        taken[cell(hatchI, hatchJ)] = true;

        int[] cost = new int[GRID * GRID];
        int[] towardsCore = new int[GRID * GRID];
        route(taken, vestibule, cost, towardsCore);
        int lobby = -1;
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            int i = hatchI + Compass.dx(direction);
            int j = hatchJ + Compass.dz(direction);
            if (!isOffGrid(i, j) && cost[cell(i, j)] != UNREACHED && (lobby < 0 || cost[cell(i, j)] < cost[lobby]))
                lobby = cell(i, j);
        }
        var path = new ArrayList<Integer>();
        for (int at = lobby; at != vestibule; at = towardsCore[at])
            path.add(at);
        path.add(vestibule);
        for (int at : path)
            taken[at] = true;

        int stepAlong = lobby / GRID - hatchI;
        int stepAcross = lobby % GRID - hatchJ;
        hatch = shaft(hatchI * CELL, hatchJ * CELL, ENTRY_FLOOR, HATCH_FLOOR, stepAlong != 0 ? stepAlong > 0 : roll(2) == 0, stepAcross != 0 ? stepAcross > 0 : roll(2) == 0, true, ENTRY_FLOOR);
        endRoom(UnderwayRoomKind.HATCH_LOBBY, lobby, stepAlong);
        door(hatch, stepAlong, stepAcross, ENTRY_FLOOR, true);
        endRoom(UnderwayRoomKind.VESTIBULE, vestibule, exitAlong);
        door(core, exitAlong, exitAcross, ENTRY_FLOOR, true);

        int last = path.size() - 1;
        for (int step = 0; step < last; step++)
            link(path.get(step), path.get(step + 1));
        lock(path.get(0), path.get(1), path.get(1));
        if (last > 1)
            lock(path.get(last - 1), path.get(last), path.get(last - 1));

        for (int step = 1; step < last; step++)
        {
            int in = path.get(step) - path.get(step - 1);
            if (in != path.get(step + 1) - path.get(step))
                guard(path.get(step), in, taken);
        }
        boolean[] hasDoor = new boolean[GRID * GRID];
        int sideRooms = 0;
        for (int step = 1; step < last; step++)
        {
            for (int direction = 0; direction < Compass.COUNT && sideRooms < MAX_SIDE_ROOMS; direction++)
            {
                int i = path.get(step) / GRID + Compass.dx(direction);
                int j = path.get(step) % GRID + Compass.dz(direction);
                if (isOffGrid(i, j) || taken[cell(i, j)] || roll(PERCENT) >= SIDE_ROOM_PERCENT)
                    continue;
                taken[cell(i, j)] = true;
                hasDoor[path.get(step)] = true;
                sideRoom(SIDE_ROOMS[sideRooms++ % SIDE_ROOMS.length], path.get(step), cell(i, j));
            }
        }
        for (int step = 1; step < last; step++)
        {
            int in = path.get(step) - path.get(step - 1);
            if (in == path.get(step + 1) - path.get(step) && !hasDoor[path.get(step)])
                pit(path.get(step), in);
        }
    }

    private static int cell(int i, int j)
    {
        return i * GRID + j;
    }

    private static boolean isOffGrid(int i, int j)
    {
        return i < 1 || i > LAST_CELL || j < 1 || j > LAST_CELL;
    }

    /** Finds the cheapest way to every free cell from one, over cells that each cost a little more or less so the way wanders. */
    private void route(boolean[] taken, int start, int[] cost, int[] back)
    {
        long jitter = hash;
        Arrays.fill(cost, UNREACHED);
        cost[start] = 0;
        boolean[] settled = new boolean[cost.length];
        while (true)
        {
            int at = -1;
            for (int candidate = 0; candidate < cost.length; candidate++)
            {
                if (!settled[candidate] && cost[candidate] != UNREACHED && (at < 0 || cost[candidate] < cost[at]))
                    at = candidate;
            }
            if (at < 0)
                return;
            settled[at] = true;
            for (int direction = 0; direction < Compass.COUNT; direction++)
            {
                int i = at / GRID + Compass.dx(direction);
                int j = at % GRID + Compass.dz(direction);
                if (isOffGrid(i, j) || taken[cell(i, j)])
                    continue;
                int reached = cost[at] + 1 + DaedalusHash.pick(DaedalusHash.mix(jitter + cell(i, j)), 3);
                if (reached < cost[cell(i, j)])
                {
                    cost[cell(i, j)] = reached;
                    back[cell(i, j)] = at;
                }
            }
        }
    }

    /** A room at an end of the entry gallery, as wide as the shaft it stands against. */
    private void endRoom(UnderwayRoomKind kind, int cell, int stepAlong)
    {
        int a = cell / GRID * CELL;
        int c = cell % GRID * CELL;
        if (stepAlong != 0)
            room(kind, a + 1, c, a + CELL_END - 1, c + CELL_END, ENTRY_FLOOR, ENTRY_HEIGHT);
        else
            room(kind, a, c + 1, a + CELL_END, c + CELL_END - 1, ENTRY_FLOOR, ENTRY_HEIGHT);
    }

    private void link(int from, int to)
    {
        int a = Math.min(from / GRID, to / GRID) * CELL;
        int c = Math.min(from % GRID, to % GRID) * CELL;
        boolean along = from / GRID != to / GRID;
        room(UnderwayRoomKind.ENTRY_GALLERY, a + PASSAGE_LOW, c + PASSAGE_LOW, a + PASSAGE_HIGH + (along ? CELL : 0), c + PASSAGE_HIGH + (along ? 0 : CELL), ENTRY_FLOOR, ENTRY_HEIGHT);
    }

    /**
     * A steel bulkhead with one armoured door, where the gallery passes from a cell to the next. A tripwire crosses the gallery two steps past it.
     *
     * @param inside the cell of the two that lies deeper in the gallery
     */
    private void lock(int from, int to, int inside)
    {
        int a = Math.min(from / GRID, to / GRID) * CELL;
        int c = Math.min(from % GRID, to % GRID) * CELL;
        boolean along = from / GRID != to / GRID;
        turned(!along, null, UnderwayMaterial.STEEL, (along ? a : c) + CELL_END, ENTRY_FLOOR + 1, (along ? c : a) + PASSAGE_LOW, (along ? a : c) + CELL_END, ENTRY_FLOOR + ENTRY_HEIGHT, (along ? c : a) + PASSAGE_HIGH);
        turned(!along, null, UnderwayMaterial.DOOR, (along ? a : c) + CELL_END, ENTRY_FLOOR + 1, (along ? c : a) + PASSAGE_MIDDLE, (along ? a : c) + CELL_END, ENTRY_FLOOR + 2, (along ? c : a) + PASSAGE_MIDDLE);
        int wire = (along ? a : c) + CELL_END + (inside == Math.max(from, to) ? TRIPWIRE_PAST_DOOR : -TRIPWIRE_PAST_DOOR);
        if (along)
            fixtures.add(new UnderwayPropSpot(UnderwayProp.TRIPWIRE_EMITTER, wire, ENTRY_FLOOR + 1, c + PASSAGE_LOW, 0, 1));
        else
            fixtures.add(new UnderwayPropSpot(UnderwayProp.TRIPWIRE_EMITTER, a + PASSAGE_LOW, ENTRY_FLOOR + 1, wire, 1, 0));
    }

    /** Where the gallery bends, a guard room straight ahead watches whoever comes through a slit, with its own door beside it. */
    private void guard(int cell, int in, boolean[] taken)
    {
        boolean along = Math.abs(in) == GRID;
        int step = Integer.signum(in);
        int i = cell / GRID + (along ? step : 0);
        int j = cell % GRID + (along ? 0 : step);
        if (isOffGrid(i, j) || taken[cell(i, j)])
            return;
        taken[cell(i, j)] = true;

        int run = (along ? cell / GRID : cell % GRID) * CELL;
        int side = (along ? cell % GRID : cell / GRID) * CELL;
        int near = step > 0 ? run + CELL_END : run + 1;
        int far = step > 0 ? run + CELL + CELL_END - 1 : run - CELL + 1;
        int wall = step > 0 ? run + CELL_END - 1 : run + 2;
        if (along)
            room(UnderwayRoomKind.GUARD_ROOM, near, side + 1, far, side + CELL_END - 1, ENTRY_FLOOR, ENTRY_HEIGHT);
        else
            room(UnderwayRoomKind.GUARD_ROOM, side + 1, near, side + CELL_END - 1, far, ENTRY_FLOOR, ENTRY_HEIGHT);
        turned(!along, null, UnderwayMaterial.DOOR, wall, ENTRY_FLOOR + 1, side + PASSAGE_LOW, wall, ENTRY_FLOOR + 2, side + PASSAGE_LOW);
        turned(!along, null, UnderwayMaterial.EMBRASURE, wall, ENTRY_FLOOR + 2, side + PASSAGE_HIGH, wall, ENTRY_FLOOR + 2, side + PASSAGE_HIGH);
        // The gun stands inside the room, right behind its slit, and looks back down the gallery
        if (along)
            units.add(new UnderwayUnit(UnderwayUnitKind.EMBRASURE_GUN, wall + step, ENTRY_FLOOR + 2, side + PASSAGE_HIGH, -step, 0, true));
        else
            units.add(new UnderwayUnit(UnderwayUnitKind.EMBRASURE_GUN, side + PASSAGE_HIGH, ENTRY_FLOOR + 2, wall + step, 0, -step, true));
    }

    private void pit(int cell, int in)
    {
        int a = cell / GRID * CELL;
        int c = cell % GRID * CELL;
        room(UnderwayRoomKind.PIT, a + PASSAGE_LOW, c + PASSAGE_LOW, a + PASSAGE_HIGH, c + PASSAGE_HIGH, ENTRY_FLOOR - PIT_DEPTH, ENTRY_HEIGHT + PIT_DEPTH);
        if (Math.abs(in) == GRID)
            inAir(UnderwayMaterial.GRATING, a + PASSAGE_LOW, ENTRY_FLOOR, c + PASSAGE_MIDDLE, a + PASSAGE_HIGH, ENTRY_FLOOR, c + PASSAGE_MIDDLE);
        else
            inAir(UnderwayMaterial.GRATING, a + PASSAGE_MIDDLE, ENTRY_FLOOR, c + PASSAGE_LOW, a + PASSAGE_MIDDLE, ENTRY_FLOOR, c + PASSAGE_HIGH);
        // A beam crosses the middle of the grating, and drops it under whoever breaks it
        if (Math.abs(in) == GRID)
            fixtures.add(new UnderwayPropSpot(UnderwayProp.TRIPWIRE_EMITTER, a + PASSAGE_MIDDLE, ENTRY_FLOOR + 1, c + PASSAGE_LOW, 0, 1));
        else
            fixtures.add(new UnderwayPropSpot(UnderwayProp.TRIPWIRE_EMITTER, a + PASSAGE_LOW, ENTRY_FLOOR + 1, c + PASSAGE_MIDDLE, 1, 0));
    }

    private void sideRoom(UnderwayRoomKind kind, int from, int to)
    {
        int a = to / GRID * CELL;
        int c = to % GRID * CELL;
        room(kind, a + 1, c + 1, a + CELL_END - 1, c + CELL_END - 1, ENTRY_FLOOR, ENTRY_HEIGHT);
        int low = Math.min(from, to);
        int lowA = low / GRID * CELL;
        int lowC = low % GRID * CELL;
        boolean leavesLow = from == low;
        boolean along = from / GRID != to / GRID;
        int first = (along ? lowA : lowC) + (leavesLow ? PASSAGE_HIGH + 1 : CELL_END);
        int last = (along ? lowA : lowC) + (leavesLow ? CELL : CELL + PASSAGE_LOW - 1);
        int side = (along ? lowC : lowA) + PASSAGE_LOW;
        if (along)
            opening(first, side, last, side + 1, ENTRY_FLOOR, DOOR_HEIGHT);
        else
            opening(side, first, side + 1, last, ENTRY_FLOOR, DOOR_HEIGHT);
        // The shutter closes the doorway flush with the gallery's wall
        if (SHUTTERED.contains(kind))
        {
            int flush = leavesLow ? first : last;
            turned(!along, null, UnderwayMaterial.SHUTTER, flush, ENTRY_FLOOR + 1, side, flush, ENTRY_FLOOR + DOOR_HEIGHT, side + 1);
        }
    }
}
