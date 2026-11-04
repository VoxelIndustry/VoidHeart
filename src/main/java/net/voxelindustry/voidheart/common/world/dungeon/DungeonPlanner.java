package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.DungeonKit;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.RoomKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.plane;

/**
 * Decides where rooms go and routes corridors between them on the lattice. Every room kept is reachable from the entry room, since rooms that cannot be reached are dropped.
 */
final class DungeonPlanner
{
    /**
     * A corridor cell with a flat floor. Its openings lead to neighbouring corridors, stairs and rooms.
     */
    static final class Flat
    {
        final int i;
        final int j;
        final int k;
        final Set<Direction> openings = EnumSet.noneOf(Direction.class);

        Flat(int i, int j, int k)
        {
            this.i = i;
            this.j = j;
            this.k = k;
        }
    }

    /**
     * A flight of stairs in cell (i, j, k), rising toward its heading and up through the cell above. Flat cells at both ends lead to it.
     */
    record Stair(int i, int j, int k, Direction ascending)
    {
    }

    private record Step(int cell, int through, boolean isStair, Direction heading)
    {
    }

    private static final int MAX_SEARCH = 40_000;
    private static final int COST_NEW_FLAT = 4;
    private static final int COST_EXISTING_FLAT = 1;
    private static final int COST_STAIR = 12;
    private static final int PLACEMENT_ATTEMPTS_PER_ROOM = 60;
    private static final int[] TARGET_FLOATING = { 3, 9, 26 };
    private static final int[] TARGET_BURIED = { 4, 13, 42 };
    private static final int[] LANDMARKS = { 0, 1, 3 };
    private static final int[] MAX_LEAVES = { 1, 3, 7 };
    private static final double LOOP_CHANCE = 0.3;

    final List<DungeonRoom> rooms = new ArrayList<>();
    final Map<Integer, Flat> flats = new LinkedHashMap<>();
    final List<Stair> stairs = new ArrayList<>();
    DungeonRoom entry;

    private final DungeonBlueprint blueprint;
    private final RandomSource random;
    private final int cells;
    private final boolean[] taken;

    DungeonPlanner(DungeonBlueprint blueprint, RandomSource random)
    {
        this.blueprint = blueprint;
        this.random = random;
        cells = blueprint.size().cells();
        taken = new boolean[cells * cells * cells];

        placeRooms();
        connectRooms();
    }

    private int node(int i, int j, int k)
    {
        return (j * cells + k) * cells + i;
    }

    private boolean inBounds(int i, int j, int k)
    {
        return i >= 0 && j >= 0 && k >= 0 && i < cells && j < cells && k < cells;
    }

    private int nodeI(int node)
    {
        return node % cells;
    }

    private int nodeK(int node)
    {
        return node / cells % cells;
    }

    private int nodeJ(int node)
    {
        return node / cells / cells;
    }

    private void placeRooms()
    {
        var kit = DungeonKit.of(blueprint.theme());
        int sizeIndex = blueprint.size().ordinal();
        placeEntryRoom(kit);
        placeLandmarks(kit, LANDMARKS[sizeIndex]);

        int target = (blueprint.isBuried() ? TARGET_BURIED : TARGET_FLOATING)[sizeIndex];
        int leaves = 0;
        var candidates = kit.rooms().stream().filter(kind -> kind.fits(blueprint.size())).toList();
        for (int attempt = 0; attempt < target * PLACEMENT_ATTEMPTS_PER_ROOM && rooms.size() < target; attempt++)
        {
            var kind = pick(candidates);
            if (kind.isLeaf() && leaves >= MAX_LEAVES[sizeIndex])
                continue;

            int width = between(kind.minWidth(), Math.min(kind.maxWidth(), cells - 2));
            int depth = kind.template() != null ? width : between(kind.minWidth(), Math.min(kind.maxWidth(), cells - 2));
            int height = between(kind.minHeight(), Math.min(kind.maxHeight(), cells - 2));
            if (width > cells || depth > cells || height > cells)
                continue;

            int i = random.nextInt(cells - width + 1);
            int j = random.nextInt(cells - height + 1);
            int k = random.nextInt(cells - depth + 1);
            if (tryPlace(kind, i, j, k, width, height, depth) && kind.isLeaf())
                leaves++;
        }
    }

    /**
     * Players start low and at the edge, with the rest of the dungeon rising before them. Placed first so that a landmark cannot take all the room.
     */
    private void placeEntryRoom(DungeonKit kit)
    {
        var candidates = kit.rooms().stream().filter(kind -> !kind.isLeaf() && kind.minWidth() <= cells / 2).toList();
        var kind = pick(candidates);
        int width = kind.minWidth();
        int depth = kind.minWidth();
        int height = Math.min(kind.minHeight() + 1, kind.maxHeight());
        int i = random.nextBoolean() ? 0 : cells - width;
        int k = random.nextInt(cells - depth + 1);
        var room = new DungeonRoom(kind, i, 0, k, i + width, height, k + depth);
        rooms.add(room);
        markRoom(room, true);
        entry = room;
    }

    private void placeLandmarks(DungeonKit kit, int count)
    {
        var candidates = new ArrayList<>(kit.landmarks().stream().filter(kind -> kind.fits(blueprint.size())).toList());
        for (int landmark = 0; landmark < count && !candidates.isEmpty(); landmark++)
        {
            for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS_PER_ROOM; attempt++)
            {
                var kind = pick(candidates);
                int width = Math.min(kind.maxWidth(), Math.max(kind.minWidth(), cells / 2));
                width = between(Math.min(width, kind.minWidth() + 2), width);
                int depth = kind.template() != null ? width : between(kind.minWidth(), kind.maxWidth());
                int height = between(kind.minHeight(), kind.maxHeight());
                if (width > cells || depth > cells || height > cells)
                    continue;

                int i = centered(cells - width);
                int j = centered(cells - height);
                int k = centered(cells - depth);
                if (tryPlace(kind, i, j, k, width, height, depth))
                {
                    if (kind.template() != null)
                        candidates.remove(kind);
                    break;
                }
            }
        }
    }

    private int centered(int range)
    {
        if (range <= 0)
            return 0;
        return (random.nextInt(range + 1) + random.nextInt(range + 1)) / 2;
    }

    private int between(int min, int max)
    {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private RoomKind pick(List<RoomKind> kinds)
    {
        int total = kinds.stream().mapToInt(RoomKind::weight).sum();
        int roll = random.nextInt(total);
        for (var kind : kinds)
        {
            roll -= kind.weight();
            if (roll < 0)
                return kind;
        }
        return kinds.getLast();
    }

    private boolean tryPlace(RoomKind kind, int i, int j, int k, int width, int height, int depth)
    {
        int gapSide = blueprint.isBuried() ? 1 : 2;
        int gapHigh = 1;
        for (int cj = j - gapHigh; cj < j + height + gapHigh; cj++)
        {
            for (int ck = k - gapSide; ck < k + depth + gapSide; ck++)
            {
                for (int ci = i - gapSide; ci < i + width + gapSide; ci++)
                {
                    if (inBounds(ci, cj, ck) && taken[node(ci, cj, ck)])
                        return false;
                }
            }
        }

        var room = new DungeonRoom(kind, i, j, k, i + width, j + height, k + depth);
        rooms.add(room);
        markRoom(room, true);
        return true;
    }

    private void markRoom(DungeonRoom room, boolean value)
    {
        for (int j = room.j0; j < room.j1; j++)
        {
            for (int k = room.k0; k < room.k1; k++)
            {
                for (int i = room.i0; i < room.i1; i++)
                    taken[node(i, j, k)] = value;
            }
        }
    }

    private void connectRooms()
    {
        var connected = new ArrayList<DungeonRoom>();
        connected.add(entry);
        var failed = new HashSet<Long>();

        connectAll(connected, failed, false);
        connectAll(connected, failed, true);

        var unreachable = new ArrayList<>(rooms);
        unreachable.removeAll(connected);
        for (var room : unreachable)
        {
            markRoom(room, false);
            rooms.remove(room);
        }

        addLoops();
    }

    private void connectAll(List<DungeonRoom> connected, Set<Long> failed, boolean leaves)
    {
        var pending = new ArrayList<>(rooms.stream().filter(room -> room.kind.isLeaf() == leaves && !connected.contains(room)).toList());
        while (!pending.isEmpty())
        {
            DungeonRoom bestFrom = null;
            DungeonRoom bestTo = null;
            int bestDistance = Integer.MAX_VALUE;
            for (var from : connected)
            {
                if (from.kind.isLeaf())
                    continue;
                for (var to : pending)
                {
                    int distance = from.distanceTo(to);
                    if (distance < bestDistance && !failed.contains(pairKey(from, to)))
                    {
                        bestDistance = distance;
                        bestFrom = from;
                        bestTo = to;
                    }
                }
            }
            if (bestFrom == null)
                return;

            if (route(bestFrom, bestTo))
            {
                connected.add(bestTo);
                pending.remove(bestTo);
            }
            else
                failed.add(pairKey(bestFrom, bestTo));
        }
    }

    private long pairKey(DungeonRoom a, DungeonRoom b)
    {
        return ((long) rooms.indexOf(a) << 32) | rooms.indexOf(b);
    }

    private void addLoops()
    {
        var regular = rooms.stream().filter(room -> !room.kind.isLeaf()).toList();
        for (var room : regular)
        {
            if (random.nextDouble() >= LOOP_CHANCE)
                continue;

            regular.stream()
                    .filter(other -> other != room && room.distanceTo(other) > 0)
                    .min(Comparator.comparingInt(room::distanceTo))
                    .ifPresent(other -> route(room, other));
        }
    }

    private Map<Integer, Direction> perimeter(DungeonRoom room)
    {
        var cellsToRoom = new HashMap<Integer, Direction>();
        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (!room.kind.doorSides().isEmpty() && !room.kind.doorSides().contains(side))
                continue;

            int fromI = side == Direction.EAST ? room.i1 : side == Direction.WEST ? room.i0 - 1 : room.i0;
            int toI = side == Direction.EAST ? room.i1 : side == Direction.WEST ? room.i0 - 1 : room.i1 - 1;
            int fromK = side == Direction.SOUTH ? room.k1 : side == Direction.NORTH ? room.k0 - 1 : room.k0;
            int toK = side == Direction.SOUTH ? room.k1 : side == Direction.NORTH ? room.k0 - 1 : room.k1 - 1;
            for (int k = fromK; k <= toK; k++)
            {
                for (int i = fromI; i <= toI; i++)
                {
                    if (inBounds(i, room.j0, k) && !taken[node(i, room.j0, k)])
                        cellsToRoom.put(node(i, room.j0, k), side.getOpposite());
                }
            }
        }
        return cellsToRoom;
    }

    private boolean route(DungeonRoom from, DungeonRoom to)
    {
        var starts = perimeter(from);
        var goals = perimeter(to);
        if (starts.isEmpty() || goals.isEmpty())
            return false;

        var path = search(starts, goals, to);
        return path != null && commit(path, from, starts, to, goals);
    }

    private @Nullable List<Step> search(Map<Integer, Direction> starts, Map<Integer, Direction> goals, DungeonRoom to)
    {
        int[] best = new int[taken.length];
        Arrays.fill(best, Integer.MAX_VALUE);
        Step[] cameBy = new Step[taken.length];
        int[] cameFrom = new int[taken.length];
        var open = new PriorityQueue<long[]>(Comparator.comparingLong(entry -> entry[0]));

        int goalI = (to.i0 + to.i1) / 2;
        int goalK = (to.k0 + to.k1) / 2;
        for (int start : starts.keySet())
        {
            best[start] = 0;
            cameFrom[start] = -1;
            open.add(new long[] { heuristic(start, goalI, to.j0, goalK), start });
        }

        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_SEARCH)
        {
            var top = open.poll();
            int current = (int) top[1];
            if (goals.containsKey(current))
                return rebuild(current, cameFrom, cameBy);

            int cost = best[current];
            int i = nodeI(current);
            int j = nodeJ(current);
            int k = nodeK(current);
            for (var side : Direction.Plane.HORIZONTAL)
            {
                int di = side.getStepX();
                int dk = side.getStepZ();
                int ni = i + di;
                int nk = k + dk;

                if (isWalkableFlat(ni, j, nk))
                {
                    int next = node(ni, j, nk);
                    int step = flats.containsKey(next) ? COST_EXISTING_FLAT : COST_NEW_FLAT;
                    relax(open, best, cameFrom, cameBy, current, next, cost + step, new Step(next, -1, false, side), goalI, to.j0, goalK);
                }

                if (isFreeForStair(ni, j, nk) && isFreeForStair(ni, j + 1, nk) && isWalkableFlat(ni + di, j + 1, nk + dk))
                {
                    int next = node(ni + di, j + 1, nk + dk);
                    relax(open, best, cameFrom, cameBy, current, next, cost + COST_STAIR, new Step(next, node(ni, j, nk), true, side), goalI, to.j0, goalK);
                }

                if (isFreeForStair(ni, j - 1, nk) && isFreeForStair(ni, j, nk) && isWalkableFlat(ni + di, j - 1, nk + dk))
                {
                    int next = node(ni + di, j - 1, nk + dk);
                    relax(open, best, cameFrom, cameBy, current, next, cost + COST_STAIR, new Step(next, node(ni, j - 1, nk), true, side.getOpposite()), goalI, to.j0, goalK);
                }
            }
        }
        return null;
    }

    private void relax(PriorityQueue<long[]> open, int[] best, int[] cameFrom, Step[] cameBy, int current, int next, int cost, Step step, int goalI, int goalJ, int goalK)
    {
        if (cost >= best[next] || crossesOwnPath(current, step, cameFrom, cameBy))
            return;

        best[next] = cost;
        cameFrom[next] = current;
        cameBy[next] = step;
        open.add(new long[] { cost + heuristic(next, goalI, goalJ, goalK), next });
    }

    private boolean crossesOwnPath(int current, Step step, int[] cameFrom, Step[] cameBy)
    {
        int above = cells * cells;
        for (int node = current; node != -1; node = cameFrom[node])
        {
            if (step.isStair() && (node == step.through() || node == step.through() + above))
                return true;

            var earlier = cameBy[node];
            if (earlier == null || !earlier.isStair())
                continue;

            int low = earlier.through();
            if (step.cell() == low || step.cell() == low + above)
                return true;
            if (step.isStair() && Math.abs(step.through() - low) % above == 0 && Math.abs(step.through() - low) <= above)
                return true;
        }
        return false;
    }

    private int heuristic(int node, int goalI, int goalJ, int goalK)
    {
        return (Math.abs(nodeI(node) - goalI) + Math.abs(nodeK(node) - goalK)) * COST_NEW_FLAT + Math.abs(nodeJ(node) - goalJ) * COST_STAIR;
    }

    private boolean isWalkableFlat(int i, int j, int k)
    {
        return inBounds(i, j, k) && !taken[node(i, j, k)];
    }

    private boolean isFreeForStair(int i, int j, int k)
    {
        return inBounds(i, j, k) && !taken[node(i, j, k)] && !flats.containsKey(node(i, j, k));
    }

    private List<Step> rebuild(int goal, int[] cameFrom, Step[] cameBy)
    {
        var path = new ArrayList<Step>();
        int current = goal;
        while (cameFrom[current] != -1)
        {
            path.addFirst(cameBy[current]);
            current = cameFrom[current];
        }
        path.addFirst(new Step(current, -1, false, Direction.NORTH));
        return path;
    }

    private boolean commit(List<Step> path, DungeonRoom from, Map<Integer, Direction> starts, DungeonRoom to, Map<Integer, Direction> goals)
    {
        // The search can re-parent a cell after its descendants were checked, so the finished path may still cross itself
        var usedCells = new HashSet<Integer>();
        path.forEach(step -> usedCells.add(step.cell()));
        for (var step : path)
        {
            if (step.isStair() && (!usedCells.add(step.through()) || !usedCells.add(step.through() + cells * cells)))
                return false;
        }

        var previous = flat(path.getFirst().cell());
        for (int index = 1; index < path.size(); index++)
        {
            var step = path.get(index);
            var next = flat(step.cell());
            if (step.isStair())
            {
                boolean rising = step.heading() == headingBetween(previous, next);
                int through = step.through();
                stairs.add(new Stair(nodeI(through), nodeJ(through), nodeK(through), step.heading()));
                taken[through] = true;
                taken[through + cells * cells] = true;
                var travel = rising ? step.heading() : step.heading().getOpposite();
                previous.openings.add(travel);
                next.openings.add(travel.getOpposite());
            }
            else
            {
                previous.openings.add(step.heading());
                next.openings.add(step.heading().getOpposite());
            }
            previous = next;
        }

        addDoor(from, flat(path.getFirst().cell()), starts.get(path.getFirst().cell()));
        addDoor(to, flat(path.getLast().cell()), goals.get(path.getLast().cell()));
        return true;
    }

    private Direction headingBetween(Flat from, Flat to)
    {
        return Direction.getApproximateNearest(to.i - from.i, 0, to.k - from.k);
    }

    private Flat flat(int node)
    {
        return flats.computeIfAbsent(node, key -> new Flat(nodeI(key), nodeJ(key), nodeK(key)));
    }

    private void addDoor(DungeonRoom room, Flat flat, Direction into)
    {
        flat.openings.add(into);
        int y = plane(flat.j);
        int x = into.getAxis() == Direction.Axis.X ? plane(into == Direction.EAST ? flat.i + 1 : flat.i) : plane(flat.i) + DungeonSize.CELL / 2;
        int z = into.getAxis() == Direction.Axis.Z ? plane(into == Direction.SOUTH ? flat.k + 1 : flat.k) : plane(flat.k) + DungeonSize.CELL / 2;
        var door = new DungeonRoom.Door(x, y, z, into);
        if (!room.doors.contains(door))
            room.doors.add(door);
    }
}
