package net.voxelindustry.voidheart.common.world.daedalus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class DaedalusDistrictTest
{
    private static final long[] SEEDS = { 0L, 1234L, -987654321L, 0x123456789ABCDEFL };
    private static final int PER_THEME = 12;
    private static final int SEARCH_RADIUS = 40;
    private static final int LANE_CENTRE = 3;
    private static final int DEEP_PIT_TIERS = 10;
    private static final int MIN_PROPS_PER_DISTRICT = 100;
    private static final int WANDER_STEPS = 120;
    private static final int WANDERS_PER_DISTRICT = 20;
    private static final float MIN_ROOMS_WANDERED = 7;

    private static List<DistrictPos> districtsOf(DaedalusTheme theme, int wanted)
    {
        var found = new ArrayList<DistrictPos>();
        for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS && found.size() < wanted; z++)
        {
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS && found.size() < wanted; x++)
            {
                var pos = new DistrictPos(x, z);
                if (DaedalusTheme.at(pos) == theme)
                    found.add(pos);
            }
        }
        return found;
    }

    private static DaedalusCanvas painted(DistrictPlan plan)
    {
        var canvas = new DaedalusCanvas(DistrictPos.SIZE, DistrictPos.SIZE);
        canvas.reset(plan.pos.originX(), plan.pos.originZ());
        DistrictPainter.paint(plan, canvas);
        return canvas;
    }

    private static int[] landingPosition(DistrictPlan plan, DistrictPlan.Landing landing)
    {
        int along = landing.direction() == Compass.NORTH || landing.direction() == Compass.WEST ? 0 : DistrictPos.CELL - 1;
        int localX = Compass.isAlongX(landing.direction()) ? along : LANE_CENTRE;
        int localZ = Compass.isAlongX(landing.direction()) ? LANE_CENTRE : along;
        return new int[] {
                plan.pos.originX() + landing.i() * DistrictPos.CELL + localX,
                DistrictPos.floorY(landing.k()) + 1,
                plan.pos.originZ() + landing.j() * DistrictPos.CELL + localZ };
    }

    @Test
    void everyLandingAndRoomIsWalkableFromAnyOther()
    {
        for (long seed : SEEDS)
        {
            for (var theme : DaedalusTheme.values())
            {
                for (var pos : districtsOf(theme, PER_THEME))
                {
                    var plan = DistrictPlanner.generate(seed, pos);
                    var canvas = painted(plan);
                    var walker = new DaedalusWalker(canvas);
                    int[] start = landingPosition(plan, plan.landings().getFirst());
                    walker.walkFrom(start[0], start[1], start[2]);

                    String where = theme + " " + pos + " seed " + seed;
                    for (var landing : plan.landings())
                    {
                        int[] at = landingPosition(plan, landing);
                        assertTrue(walker.hasReached(at[0], at[1], at[2]), "Landing " + landing + " is out of reach in " + where);
                    }
                    for (var room : plan.rooms())
                    {
                        int x = plan.pos.originX();
                        int z = plan.pos.originZ();
                        assertTrue(walker.hasReachedWithin(x + room.minI() * DistrictPos.CELL, DistrictPos.floorY(room.minK()), z + room.minJ() * DistrictPos.CELL,
                                        x + room.maxI() * DistrictPos.CELL + 7, DistrictPos.floorY(room.maxK()) + 7, z + room.maxJ() * DistrictPos.CELL + 7),
                                "Room " + room + " is out of reach in " + where);
                    }
                }
            }
        }
    }

    /**
     * Stairs are walked up, never jumped: every landing can be reached from any other without climbing a full block.
     */
    @Test
    void everyLandingIsReachedWithoutJumping()
    {
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, PER_THEME))
            {
                var plan = DistrictPlanner.generate(SEEDS[1], pos);
                var walker = new DaedalusWalker(painted(plan), false);
                int[] start = landingPosition(plan, plan.landings().getFirst());
                walker.walkFrom(start[0], start[1], start[2]);
                for (var landing : plan.landings())
                {
                    int[] at = landingPosition(plan, landing);
                    assertTrue(walker.hasReached(at[0], at[1], at[2]), "Landing " + landing + " needs a jump to reach in " + theme + " " + pos);
                }
            }
        }
    }

    /**
     * Rooms should be on the way rather than beside it: most have doors on several sides, and the shortest way between two bridges usually crosses one.
     */
    @Test
    void roomsAreCrossedOnTheWayBetweenBridges()
    {
        for (var theme : DaedalusTheme.values())
        {
            int rooms = 0;
            int throughRooms = 0;
            int paths = 0;
            int pathsThroughRooms = 0;
            for (var pos : districtsOf(theme, PER_THEME))
            {
                var plan = DistrictPlanner.generate(SEEDS[1], pos);
                for (var room : plan.rooms())
                {
                    rooms++;
                    if (doorSides(plan, room) >= 2)
                        throughRooms++;
                }

                var start = plan.landings().getFirst();
                int[] parents = latticePaths(plan, DistrictPlan.index(start.i(), start.k(), start.j()));
                for (var landing : plan.landings().subList(1, plan.landings().size()))
                {
                    paths++;
                    for (int cell = DistrictPlan.index(landing.i(), landing.k(), landing.j()); cell >= 0; cell = parents[cell])
                    {
                        if (kindOf(plan, cell) == DistrictPlan.ROOM)
                        {
                            pathsThroughRooms++;
                            break;
                        }
                    }
                }
            }
            float throughShare = throughRooms / (float) rooms;
            float crossedShare = pathsThroughRooms / (float) paths;
            float wandered = roomsMetWandering(theme);
            System.out.printf("Rooms in %s: %.0f%% have doors on several sides, %.0f%% of ways between bridges cross one, %.1f rooms or voids entered per %d cells wandered%n",
                    theme, throughShare * 100, crossedShare * 100, wandered, WANDER_STEPS);
            assertTrue(throughShare > 0.75F, theme + " has too many dead end rooms: " + throughShare);
            assertTrue(crossedShare > 0.6F, "The way between bridges rarely crosses a room in " + theme + ": " + crossedShare);
            assertTrue(wandered >= MIN_ROOMS_WANDERED, "Wandering " + theme + " finds too few rooms: " + wandered);
        }
    }

    private static Room voidAt(DistrictPlan plan, int i, int k, int j)
    {
        for (var opening : plan.voids())
        {
            if (i >= opening.minI() && i <= opening.maxI() && j >= opening.minJ() && j <= opening.maxJ() && k >= opening.minK() && k <= opening.maxK())
                return opening;
        }
        throw new AssertionError("A catwalk outside every void at " + i + " " + k + " " + j);
    }

    /**
     * A walk that never turns back unless it must, choosing at random at every junction, as someone exploring would. Crossing a void on a catwalk counts as much as entering a room, since both break up the corridors.
     */
    private static float roomsMetWandering(DaedalusTheme theme)
    {
        var random = new java.util.SplittableRandom(7);
        int walks = 0;
        int met = 0;
        for (var pos : districtsOf(theme, PER_THEME))
        {
            var plan = DistrictPlanner.generate(SEEDS[1], pos);
            for (int walk = 0; walk < WANDERS_PER_DISTRICT; walk++)
            {
                var landing = plan.landings().get(random.nextInt(plan.landings().size()));
                int cell = DistrictPlan.index(landing.i(), landing.k(), landing.j());
                int previous = -1;
                var entered = new HashSet<Room>();
                for (int step = 0; step < WANDER_STEPS; step++)
                {
                    var options = new ArrayList<>(latticeNeighbors(plan, cell));
                    if (options.size() > 1)
                        options.remove(Integer.valueOf(previous));
                    previous = cell;
                    cell = options.get(random.nextInt(options.size()));
                    int i = cell & 15;
                    int k = cell >> 8;
                    int j = (cell >> 4) & 15;
                    var room = plan.room(i, k, j);
                    if (room != null)
                        entered.add(room);
                    if (plan.isCatwalk(i, k, j))
                        entered.add(voidAt(plan, i, k, j));
                }
                met += entered.size();
                walks++;
            }
        }
        return met / (float) walks;
    }

    private static int doorSides(DistrictPlan plan, Room room)
    {
        int sides = 0;
        for (int k = room.minK(); k <= room.maxK(); k++)
        {
            for (int j = room.minJ(); j <= room.maxJ(); j++)
            {
                for (int i = room.minI(); i <= room.maxI(); i++)
                    sides |= plan.faces(i, k, j) & 15;
            }
        }
        return Integer.bitCount(sides);
    }

    private static byte kindOf(DistrictPlan plan, int cell)
    {
        return plan.kind(cell & 15, cell >> 8, (cell >> 4) & 15);
    }

    /**
     * Breadth first over lattice cells, following doors, room interiors and flights.
     *
     * @return the cell each reached cell was entered from, -1 for the start and unreached cells
     */
    private static int[] latticePaths(DistrictPlan plan, int start)
    {
        var parents = new int[DistrictPlan.CELL_COUNT];
        var seen = new boolean[DistrictPlan.CELL_COUNT];
        java.util.Arrays.fill(parents, -1);
        var queue = new java.util.ArrayDeque<Integer>();
        queue.add(start);
        seen[start] = true;
        while (!queue.isEmpty())
        {
            int cell = queue.poll();
            for (int next : latticeNeighbors(plan, cell))
            {
                if (next < 0 || seen[next])
                    continue;
                seen[next] = true;
                parents[next] = cell;
                queue.add(next);
            }
        }
        return parents;
    }

    private static List<Integer> latticeNeighbors(DistrictPlan plan, int cell)
    {
        int i = cell & 15;
        int j = (cell >> 4) & 15;
        int k = cell >> 8;
        byte kind = plan.kind(i, k, j);
        var next = new ArrayList<Integer>();
        if (kind == DistrictPlan.STAIR_LOW || kind == DistrictPlan.STAIR_HIGH)
        {
            int climb = plan.faces(i, k, j);
            int otherTier = kind == DistrictPlan.STAIR_LOW ? k + 1 : k - 1;
            next.add(DistrictPlan.index(i, otherTier, j));
            int side = kind == DistrictPlan.STAIR_LOW ? Compass.opposite(climb) : climb;
            if (DistrictPlan.isInside(i + Compass.dx(side), k, j + Compass.dz(side)))
                next.add(DistrictPlan.index(i + Compass.dx(side), k, j + Compass.dz(side)));
            return next;
        }

        var room = plan.room(i, k, j);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            int ni = i + Compass.dx(direction);
            int nj = j + Compass.dz(direction);
            if (!DistrictPlan.isInside(ni, k, nj))
                continue;
            byte other = plan.kind(ni, k, nj);
            boolean sameRoom = room != null && plan.room(ni, k, nj) == room;
            boolean walkable = other == DistrictPlan.CORRIDOR || other == DistrictPlan.ROOM || other == DistrictPlan.CATWALK;
            boolean throughDoor = plan.isOpen(i, k, j, direction) && walkable && plan.isOpen(ni, k, nj, Compass.opposite(direction));
            boolean ontoStairs = plan.isOpen(i, k, j, direction)
                                 && ((other == DistrictPlan.STAIR_LOW && plan.faces(ni, k, nj) == direction) || (other == DistrictPlan.STAIR_HIGH && plan.faces(ni, k, nj) == Compass.opposite(direction)));
            if (sameRoom || throughDoor || ontoStairs)
                next.add(DistrictPlan.index(ni, k, nj));
        }
        if (room != null && (room.shape() == RoomShape.ATRIUM || room.shape() == RoomShape.COLONNADE))
        {
            if (plan.room(i, k + 1, j) == room)
                next.add(DistrictPlan.index(i, k + 1, j));
            if (plan.room(i, k - 1, j) == room)
                next.add(DistrictPlan.index(i, k - 1, j));
        }
        return next;
    }

    /**
     * Every district opens onto deep voids somewhere, and the catwalks and balconies looking into them can be walked to.
     */
    @Test
    void voidsGiveVistasThatCanBeReached()
    {
        for (var theme : DaedalusTheme.values())
        {
            int voids = 0;
            int reachedVoids = 0;
            int deepPits = 0;
            int districts = 0;
            for (var pos : districtsOf(theme, PER_THEME / 2))
            {
                districts++;
                var plan = DistrictPlanner.generate(SEEDS[2], pos);
                assertTrue(plan.voids().length > 0, "No void at all in " + theme + " " + pos);
                var canvas = painted(plan);
                var walker = new DaedalusWalker(canvas);
                int[] start = landingPosition(plan, plan.landings().getFirst());
                walker.walkFrom(start[0], start[1], start[2]);

                boolean hasDeepPit = false;
                for (var opening : plan.voids())
                {
                    voids++;
                    hasDeepPit |= opening.shape() == RoomShape.PIT && opening.maxK() - opening.minK() + 1 >= DEEP_PIT_TIERS;
                    if (hasReachableLookout(plan, walker, opening))
                        reachedVoids++;
                }
                deepPits += hasDeepPit ? 1 : 0;
            }
            float reachedShare = reachedVoids / (float) voids;
            System.out.printf("Voids in %s: %.1f per district, %.0f%% can be looked into from a catwalk or balcony, %d of %d districts have a pit %d tiers deep%n",
                    theme, voids / (float) districts, reachedShare * 100, deepPits, districts, DEEP_PIT_TIERS);
            assertTrue(reachedShare > 0.8F, "Too many voids in " + theme + " are out of sight: " + reachedShare);
            assertTrue(deepPits * 2 >= districts, "Too few deep pits in " + theme);
        }
    }

    private static boolean hasReachableLookout(DistrictPlan plan, DaedalusWalker walker, Room opening)
    {
        int originX = plan.pos.originX();
        int originZ = plan.pos.originZ();
        for (int k = opening.minK(); k <= opening.maxK(); k++)
        {
            for (int j = opening.minJ(); j <= opening.maxJ(); j++)
            {
                for (int i = opening.minI(); i <= opening.maxI(); i++)
                {
                    boolean lookout = plan.isCatwalk(i, k, j);
                    for (int direction = 0; direction < Compass.COUNT && !lookout; direction++)
                        lookout = plan.hasBalcony(i, k, j, direction);
                    if (!lookout)
                        continue;
                    int x = originX + i * DistrictPos.CELL;
                    int z = originZ + j * DistrictPos.CELL;
                    int y = DistrictPos.floorY(k) + 1;
                    if (walker.hasReachedWithin(x, y, z, x + DistrictPos.CELL - 1, y, z + DistrictPos.CELL - 1))
                        return true;
                }
            }
        }
        return false;
    }

    @Test
    void roomsAreFurnishedWithEveryProp()
    {
        var counts = new int[DaedalusProp.values().length];
        int districts = 0;
        int total = 0;
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, 3))
            {
                districts++;
                var canvas = painted(DistrictPlanner.generate(SEEDS[1], pos));
                for (int y = 1; y < DistrictPos.TOP_Y; y++)
                {
                    for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
                    {
                        for (int x = canvas.minX(); x <= canvas.maxX(); x++)
                        {
                            int raw = canvas.raw(x, y, z);
                            if (raw < DaedalusCanvas.PROP_BASE)
                                continue;
                            counts[(raw - DaedalusCanvas.PROP_BASE) / Compass.COUNT]++;
                            total++;
                        }
                    }
                }
            }
        }
        var report = new StringBuilder("Props per district:");
        for (var prop : DaedalusProp.values())
            report.append(' ').append(prop.name().toLowerCase()).append('=').append(String.format("%.1f", counts[prop.ordinal()] / (float) districts));
        System.out.println(report);
        for (var prop : DaedalusProp.values())
            assertTrue(counts[prop.ordinal()] > 0, prop + " is never placed");
        assertTrue(total / districts >= MIN_PROPS_PER_DISTRICT, "Districts are too bare: " + total / districts + " props each");
    }

    @Test
    void ritualsAreWholeWhereverTheyStand()
    {
        int[][] pillars = { { -1, 3 }, { 1, 3 }, { 1, -3 }, { -1, -3 }, { 3, -1 }, { 3, 1 }, { -3, -1 }, { -3, 1 } };
        int altars = 0;
        int monoliths = 0;
        int shrines = 0;
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, 3))
            {
                var canvas = painted(DistrictPlanner.generate(SEEDS[1], pos));
                for (int y = 1; y < DistrictPos.TOP_Y; y++)
                {
                    for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
                    {
                        for (int x = canvas.minX(); x <= canvas.maxX(); x++)
                        {
                            var material = canvas.get(x, y, z);
                            if (material == DaedalusMaterial.ALTAR)
                            {
                                altars++;
                                for (int[] offset : pillars)
                                    assertEquals(DaedalusMaterial.PILLAR, canvas.get(x + offset[0], y, z + offset[1]), "Altar at " + x + " " + y + " " + z + " is missing a pillar");
                            }
                            if (material == DaedalusMaterial.SHRINE_LAMP)
                            {
                                shrines++;
                                assertEquals(DaedalusMaterial.PILLAR, canvas.get(x, y - 1, z), "Shrine lamp at " + x + " " + y + " " + z + " stands on no pillar");
                            }
                            if (!material.isMonolith())
                                continue;
                            boolean below = canvas.get(x, y - 1, z).isMonolith();
                            boolean above = canvas.get(x, y + 1, z).isMonolith();
                            if (!below)
                                monoliths++;
                            var expected = below ? above ? DaedalusMaterial.MONOLITH_SHAFT : DaedalusMaterial.MONOLITH_TIP : above ? DaedalusMaterial.MONOLITH_BASE : DaedalusMaterial.MONOLITH_ALONE;
                            assertEquals(expected, material, "Monolith at " + x + " " + y + " " + z + " is joined wrongly");
                            if (!above)
                                assertTrue(canvas.get(x, y + 1, z).isPassable(), "Monolith at " + x + " " + y + " " + z + " touches what is above it");
                        }
                    }
                }
            }
        }
        System.out.println("Relics in 18 districts: " + altars + " altars, " + monoliths + " monoliths, " + shrines + " shrines");
        assertTrue(altars > 0, "No altar circle was raised");
        assertTrue(monoliths > 0, "No monolith was raised");
        assertTrue(shrines > 0, "No shrine was raised");
    }

    private static boolean isArch(DaedalusCanvas canvas, int x, int y, int z)
    {
        int ordinal = canvas.get(x, y, z).ordinal();
        return ordinal >= DaedalusMaterial.ARCH_NORTH.ordinal() && ordinal <= DaedalusMaterial.ARCH_WEST.ordinal();
    }

    private static boolean isProp(DaedalusCanvas canvas, int x, int y, int z, DaedalusProp prop)
    {
        int raw = canvas.raw(x, y, z);
        return raw >= DaedalusCanvas.PROP_BASE && (raw - DaedalusCanvas.PROP_BASE) / Compass.COUNT == prop.ordinal();
    }

    @Test
    void everyMaterialAndPropFitsInACanvasByte()
    {
        assertTrue(DaedalusMaterial.values().length <= DaedalusCanvas.PROP_BASE, DaedalusMaterial.values().length + " materials run into the props");
        assertTrue(DaedalusProp.values().length <= DaedalusCanvas.MAX_PROPS, DaedalusProp.values().length + " props do not fit in " + DaedalusCanvas.MAX_PROPS);
    }

    @Test
    void largeRoomsHaveRaisedFeaturesTablesAndAlcoves()
    {
        int daises = 0;
        int tables = 0;
        int pools = 0;
        int alcoves = 0;
        int engines = 0;
        int boilers = 0;
        int pipes = 0;
        int sarcophagi = 0;
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, 3))
            {
                var canvas = painted(DistrictPlanner.generate(SEEDS[1], pos));
                for (int y = 2; y < DistrictPos.TOP_Y - 1; y++)
                {
                    for (int z = canvas.minZ() + 2; z <= canvas.maxZ() - 2; z++)
                    {
                        for (int x = canvas.minX() + 2; x <= canvas.maxX() - 2; x++)
                        {
                            var material = canvas.get(x, y, z);
                            if (material == DaedalusMaterial.FLOOR_TRIM && canvas.get(x + 2, y, z).isStep() && canvas.get(x - 2, y, z).isStep() && canvas.get(x, y, z + 2).isStep())
                                daises++;
                            if (isProp(canvas, x, y, z, DaedalusProp.TABLE) && !isProp(canvas, x - 1, y, z, DaedalusProp.TABLE) && isProp(canvas, x + 1, y, z, DaedalusProp.TABLE))
                                tables++;
                            if (material == DaedalusMaterial.COLUMN && canvas.get(x + 1, y, z) == DaedalusMaterial.WATER && isProp(canvas, x, y + 1, z, DaedalusProp.BASIN))
                                pools++;
                            if (isProp(canvas, x, y, z, DaedalusProp.CONDENSER) && isProp(canvas, x + 2, y, z + 2, DaedalusProp.COIL))
                                engines++;
                            if (material == DaedalusMaterial.COPPER && canvas.get(x, y + 1, z) == DaedalusMaterial.COPPER
                                && (canvas.get(x + 1, y, z) == DaedalusMaterial.VENT || canvas.get(x - 1, y, z) == DaedalusMaterial.VENT
                                    || canvas.get(x, y, z + 1) == DaedalusMaterial.VENT || canvas.get(x, y, z - 1) == DaedalusMaterial.VENT))
                                boilers++;
                            if (material == DaedalusMaterial.PIPE_X || material == DaedalusMaterial.PIPE_Z)
                                pipes++;
                            if (isProp(canvas, x, y, z, DaedalusProp.SARCOPHAGUS) || isProp(canvas, x, y, z, DaedalusProp.SARCOPHAGUS_OPEN))
                            {
                                sarcophagi++;
                                int facing = (canvas.raw(x, y, z) - DaedalusCanvas.PROP_BASE) % Compass.COUNT;
                                assertTrue(isProp(canvas, x + Compass.dx(facing), y, z + Compass.dz(facing), DaedalusProp.SARCOPHAGUS_FOOT), "Sarcophagus at " + x + " " + y + " " + z + " has no foot");
                            }
                            if (material == DaedalusMaterial.TRIM && canvas.get(x, y - 1, z) == DaedalusMaterial.AIR && !canvas.get(x, y - 4, z).isPassable()
                                && (isArch(canvas, x - 1, y - 1, z) && isArch(canvas, x + 1, y - 1, z) || isArch(canvas, x, y - 1, z - 1) && isArch(canvas, x, y - 1, z + 1)))
                                alcoves++;
                        }
                    }
                }
            }
        }
        System.out.println("Features in 18 districts: " + daises + " daises, " + tables + " table rows, " + pools + " pools, " + alcoves + " alcoves, " + engines + " engines, "
                           + boilers + " boilers, " + pipes + " pipe blocks, " + sarcophagi + " sarcophagi");
        assertTrue(daises > 0 && tables > 0 && pools > 0 && alcoves > 0 && engines > 0 && boilers > 0 && pipes > 0 && sarcophagi > 0, "A feature is never built");
    }

    @Test
    void districtsHaveEnoughBridgesOnEverySide()
    {
        for (var pos : List.of(new DistrictPos(0, 0), new DistrictPos(-3, 7), new DistrictPos(1000, -1000)))
        {
            var plan = DistrictPlanner.generate(SEEDS[1], pos);
            var perSide = new int[Compass.COUNT];
            for (var landing : plan.landings())
                perSide[landing.direction()]++;
            for (int side = 0; side < Compass.COUNT; side++)
                assertTrue(perSide[side] >= 2, "Too few bridges on side " + side + " of " + pos);
        }
    }

    @Test
    void bridgesOfASideHaveVaryingHeightsAndDoNotOverlap()
    {
        for (int x = -20; x < 20; x++)
        {
            var bridges = DaedalusBridges.between(SEEDS[2], new DistrictPos(x, x * 3), x % 2 == 0 ? Compass.EAST : Compass.SOUTH);
            var heights = new HashSet<Integer>();
            for (var bridge : bridges)
                heights.add(bridge.fromTier());
            assertEquals(bridges.size(), heights.size(), "Bridges of a side should not share a height");

            for (var bridge : bridges)
            {
                for (var other : bridges)
                {
                    if (bridge != other)
                        assertTrue(Math.abs(bridge.cell() - other.cell()) >= 2, "Bridges too close: " + bridge + " and " + other);
                }
            }
        }
    }

    @Test
    void walkingAcrossABridgeReachesTheNextDistrict()
    {
        for (var theme : DaedalusTheme.values())
        {
            var from = districtsOf(theme, 1).getFirst();
            for (int direction : new int[] { Compass.EAST, Compass.SOUTH })
            {
                var to = new DistrictPos(from.x() + Compass.dx(direction), from.z() + Compass.dz(direction));
                var canvas = new DaedalusCanvas(direction == Compass.EAST ? DistrictPos.PITCH + DistrictPos.SIZE : DistrictPos.SIZE,
                        direction == Compass.SOUTH ? DistrictPos.PITCH + DistrictPos.SIZE : DistrictPos.SIZE);
                canvas.reset(from.originX(), from.originZ());
                DistrictPlans.paint(SEEDS[1], canvas);

                var plan = DistrictPlans.get(SEEDS[1], from);
                var target = DistrictPlans.get(SEEDS[1], to);
                int[] start = landingPosition(plan, plan.landings().getFirst());
                var walker = new DaedalusWalker(canvas);
                walker.walkFrom(start[0], start[1], start[2]);

                int[] arrival = landingPosition(target, target.landings().stream().filter(landing -> landing.direction() == Compass.opposite(direction)).findFirst().orElseThrow());
                assertTrue(walker.hasReached(arrival[0], arrival[1], arrival[2]), "No way across from " + from + " to " + to + " for " + theme);
            }
        }
    }

    @Test
    void everyBridgeStyleCanBeWalkedEndToEnd()
    {
        var seen = EnumSet.noneOf(DaedalusBridge.BridgeStyle.class);
        for (int x = 0; x < 60; x++)
        {
            for (int direction : new int[] { Compass.EAST, Compass.SOUTH })
            {
                var from = new DistrictPos(x, -x);
                for (var bridge : DaedalusBridges.between(SEEDS[3], from, direction))
                {
                    seen.add(bridge.style());
                    var canvas = new DaedalusCanvas(DistrictPos.CHASM + 2, DistrictPos.CHASM + 2);
                    int minX = bridge.worldX(0, LANE_CENTRE) - 1;
                    int minZ = bridge.worldZ(0, LANE_CENTRE) - 1;
                    if (bridge.isAlongX())
                        minZ -= DistrictPos.CHASM / 2;
                    else
                        minX -= DistrictPos.CHASM / 2;
                    canvas.reset(minX, minZ);
                    BridgePainter.paint(bridge, canvas);

                    var walker = new DaedalusWalker(canvas);
                    walker.walkFrom(bridge.worldX(0, LANE_CENTRE), bridge.walkY(0), bridge.worldZ(0, LANE_CENTRE));
                    int last = DistrictPos.CHASM - 1;
                    assertTrue(walker.hasReached(bridge.worldX(last, LANE_CENTRE), bridge.walkY(last), bridge.worldZ(last, LANE_CENTRE)), "Cannot cross " + bridge);
                }
            }
        }
        assertEquals(EnumSet.allOf(DaedalusBridge.BridgeStyle.class), seen);
    }

    @Test
    void waterCannotFlowAnywhere()
    {
        var positions = new ArrayList<DistrictPos>();
        for (var theme : DaedalusTheme.values())
            positions.addAll(districtsOf(theme, theme == DaedalusTheme.CISTERN ? PER_THEME : 3));
        for (var pos : positions)
        {
            var canvas = painted(DistrictPlanner.generate(SEEDS[0], pos));
            for (int y = 1; y < DistrictPos.TOP_Y; y++)
            {
                for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
                {
                    for (int x = canvas.minX(); x <= canvas.maxX(); x++)
                    {
                        if (canvas.get(x, y, z) != DaedalusMaterial.WATER)
                            continue;
                        assertFalse(canvas.get(x, y - 1, z).isPassable() && canvas.get(x, y - 1, z) != DaedalusMaterial.WATER, "Water can fall at " + x + " " + y + " " + z);
                        for (int direction = 0; direction < Compass.COUNT; direction++)
                        {
                            var beside = canvas.get(x + Compass.dx(direction), y, z + Compass.dz(direction));
                            assertFalse(beside.isPassable() && beside != DaedalusMaterial.WATER, "Water can spread at " + x + " " + y + " " + z);
                        }
                    }
                }
            }
        }
    }

    @Test
    void floorAndCeilingCoverEveryColumnIncludingChasms()
    {
        var canvas = new DaedalusCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
        canvas.reset(-DistrictPos.PITCH, -DistrictPos.PITCH);
        DistrictPlans.paint(SEEDS[0], canvas);
        for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
        {
            for (int x = canvas.minX(); x <= canvas.maxX(); x++)
            {
                assertEquals(DaedalusMaterial.BOUND, canvas.get(x, 0, z));
                assertEquals(DaedalusMaterial.BOUND, canvas.get(x, DistrictPos.TOP_Y, z));
            }
        }
    }

    @Test
    void chunkByChunkDrawsTheSameAsAllAtOnce()
    {
        for (var theme : DaedalusTheme.values())
        {
            var pos = districtsOf(theme, 1).getFirst();
            var whole = new DaedalusCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
            whole.reset(pos.originX(), pos.originZ());
            DistrictPlans.paint(SEEDS[1], whole);

            var chunk = new DaedalusCanvas(16, 16);
            for (int cz = 0; cz < DistrictPos.PITCH; cz += 16)
            {
                for (int cx = 0; cx < DistrictPos.PITCH; cx += 16)
                {
                    chunk.reset(pos.originX() + cx, pos.originZ() + cz);
                    DistrictPlans.paint(SEEDS[1], chunk);
                    for (int y = 0; y < DistrictPos.HEIGHT; y++)
                    {
                        for (int z = 0; z < 16; z++)
                        {
                            for (int x = 0; x < 16; x++)
                            {
                                int worldX = chunk.minX() + x;
                                int worldZ = chunk.minZ() + z;
                                assertEquals(whole.get(worldX, y, worldZ), chunk.get(worldX, y, worldZ), "Differs at " + worldX + " " + y + " " + worldZ + " in " + theme);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void generationDependsOnlyOnSeedAndPosition()
    {
        var pos = new DistrictPos(17, -42);
        var first = painted(DistrictPlanner.generate(SEEDS[1], pos));
        var second = painted(DistrictPlanner.generate(SEEDS[1], pos));
        var otherSeed = painted(DistrictPlanner.generate(SEEDS[2], pos));
        assertArrayEquals(first.cells(), second.cells());
        assertFalse(java.util.Arrays.equals(first.cells(), otherSeed.cells()), "Another world should lay the district out differently");
    }

    @Test
    void negativeCoordinatesFallInTheRightDistrict()
    {
        assertEquals(new DistrictPos(-1, -1), DistrictPos.at(-1, -1));
        assertEquals(new DistrictPos(0, 0), DistrictPos.at(DistrictPos.PITCH - 1, 0));
        assertTrue(DistrictPos.isInsideDistrict(-DistrictPos.PITCH, -DistrictPos.PITCH));
        assertFalse(DistrictPos.isInsideDistrict(-1, 0));
    }

    @Test
    void themesAreEvenlySpread()
    {
        Map<DaedalusTheme, Integer> counts = new EnumMap<>(DaedalusTheme.class);
        int total = 0;
        for (int z = -50; z < 50; z++)
        {
            for (int x = -50; x < 50; x++)
            {
                counts.merge(DaedalusTheme.at(new DistrictPos(x, z)), 1, Integer::sum);
                total++;
            }
        }
        for (var theme : DaedalusTheme.values())
        {
            float share = counts.getOrDefault(theme, 0) / (float) total;
            assertTrue(share > 0.12F && share < 0.22F, theme + " has a share of " + share);
        }
    }

    @Test
    void namesAreVariedAndAllExistInTheLanguageFile() throws Exception
    {
        JsonObject language;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/assets/voidheart/lang/en_us.json")))
        {
            language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        for (var pattern : List.of("plain", "of"))
            assertTrue(language.has("voidheart.daedalus.pattern." + pattern), "Missing name pattern " + pattern);
        for (var theme : DaedalusTheme.values())
        {
            String prefix = "voidheart.daedalus." + theme.getSerializedName();
            for (int word = 0; word < DistrictName.ADJECTIVES; word++)
                assertTrue(language.has(prefix + ".adjective." + word), "Missing " + prefix + ".adjective." + word);
            for (int word = 0; word < DistrictName.NOUNS; word++)
                assertTrue(language.has(prefix + ".noun." + word), "Missing " + prefix + ".noun." + word);
            for (int word = 0; word < DistrictName.SUBJECTS; word++)
                assertTrue(language.has(prefix + ".subject." + word), "Missing " + prefix + ".subject." + word);
        }

        var names = new HashSet<DistrictName>();
        int count = 0;
        for (int x = 0; x < 200; x++)
        {
            var pos = new DistrictPos(x, x * 7);
            var name = DistrictName.of(SEEDS[1], pos);
            assertTrue(name.words().getFirst().contains(DaedalusTheme.at(pos).getSerializedName()), "A name should use words of its theme: " + name);
            names.add(name);
            count++;
        }
        assertTrue(names.size() > count * 3 / 4, "Names repeat too much: " + names.size() + " of " + count);
    }

    @Test
    void plansAreFastToMakeAndChunksFastToDraw()
    {
        var positions = new ArrayList<DistrictPos>();
        for (var theme : DaedalusTheme.values())
            positions.addAll(districtsOf(theme, PER_THEME));

        for (var pos : positions)
            DistrictPlanner.generate(SEEDS[0], pos);

        long planStart = System.nanoTime();
        for (var pos : positions)
            DistrictPlanner.generate(SEEDS[3], pos);
        double planMillis = (System.nanoTime() - planStart) / 1e6 / positions.size();

        var chunk = new DaedalusCanvas(16, 16);
        int drawn = 0;
        long drawStart = System.nanoTime();
        for (var pos : positions)
        {
            for (int cz = 0; cz < DistrictPos.PITCH; cz += 16)
            {
                for (int cx = 0; cx < DistrictPos.PITCH; cx += 16)
                {
                    chunk.reset(pos.originX() + cx, pos.originZ() + cz);
                    DistrictPlans.paint(SEEDS[3], chunk);
                    drawn++;
                }
            }
        }
        double drawMillis = (System.nanoTime() - drawStart) / 1e6 / drawn;

        System.out.printf("Daedalus: %.2f ms per district plan, %.3f ms per chunk drawn%n", planMillis, drawMillis);
        assertTrue(planMillis < 25, "A district plan took " + planMillis + " ms");
        assertTrue(drawMillis < 2, "A chunk took " + drawMillis + " ms to draw");
    }

    /** Caches hold the Sparks of the Daedalus, so a district has a few of them, each standing on a floor with room to open it. */
    @Test
    void districtsKeepAFewCaches()
    {
        int districts = 0;
        int caches = 0;
        for (var theme : DaedalusTheme.values())
        {
            for (var pos : districtsOf(theme, 3))
            {
                districts++;
                var canvas = painted(DistrictPlanner.generate(SEEDS[1], pos));
                for (int y = 1; y < DistrictPos.TOP_Y; y++)
                {
                    for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
                    {
                        for (int x = canvas.minX(); x <= canvas.maxX(); x++)
                        {
                            if (canvas.raw(x, y, z) != DaedalusMaterial.CACHE.ordinal())
                                continue;
                            caches++;
                            assertTrue(!canvas.get(x, y - 1, z).isPassable(), "A cache at " + x + " " + y + " " + z + " floats");
                            assertTrue(canvas.get(x, y + 1, z).isPassable(), "A cache at " + x + " " + y + " " + z + " cannot be opened");
                        }
                    }
                }
            }
        }
        System.out.println("Caches per district: " + String.format("%.1f", caches / (float) districts));
        assertTrue(caches >= districts, "Districts should keep at least one cache each on average, got " + caches + " in " + districts);
    }
}
