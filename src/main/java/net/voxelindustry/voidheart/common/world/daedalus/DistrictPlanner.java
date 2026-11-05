package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static net.voxelindustry.voidheart.common.world.daedalus.DistrictPlan.*;

/**
 * Lays out one district on its lattice: bridge landings first since they are fixed, then the rooms of its theme, then corridors and stairs until everything is one walkable whole.
 * Routing is Dijkstra over (cell, heading) states, which is what lets a theme price corners and stairs.
 */
final class DistrictPlanner
{
    private static final long PLAN_SALT = 0x706C616EL;
    private static final int STEP_COST = 4;
    private static final int MAX_ROOMS = 250;
    private static final int ROOM_PLACE_ATTEMPTS = 6;
    private static final int ROOM_ENDING_PERCENT = 65;
    private static final int ROOM_DOOR_SIDES = 4;
    private static final int CELL_DOOR_SIDES = 2;
    private static final int THROUGH_DOOR_COST_LIMIT = 60;
    private static final int WIDE_CORRIDOR = 5;
    private static final int ROUTE_ATTEMPTS = 32;
    private static final int STATES = CELL_COUNT * Compass.COUNT;
    private static final int UNREACHED = Integer.MAX_VALUE;
    private static final int LAST_CELL = DistrictPos.CELLS - 1;

    private static final byte MOVE_START = 0;
    private static final byte MOVE_WALK = 1;
    private static final byte MOVE_TURN = 2;
    private static final byte MOVE_UP = 3;
    private static final byte MOVE_DOWN = 4;

    private static final int ROUTE_NONE = 0;
    private static final int ROUTE_DONE = 1;
    private static final int ROUTE_RETRY = 2;

    private final long worldSeed;
    private final DistrictPos pos;
    private final DaedalusTheme theme;
    private final DaedalusTheme.Layout layout;
    private final SplittableRandom random;

    private final byte[] kinds = new byte[CELL_COUNT];
    private final byte[] faces = new byte[CELL_COUNT];
    private final byte[] roomIds = new byte[CELL_COUNT];
    private final byte[] noise = new byte[CELL_COUNT];
    private final int[] parent = new int[CELL_COUNT];
    private final List<Room> rooms = new ArrayList<>();
    private final List<Landing> landings = new ArrayList<>();
    private int carved;

    private final int[] costs = new int[STATES];
    private final int[] previous = new int[STATES];
    private final byte[] moves = new byte[STATES];
    private final byte[] claims = new byte[CELL_COUNT];
    private final boolean[] stairBanned = new boolean[CELL_COUNT];
    private long[] heap = new long[256];
    private int heapSize;
    private int goalState;
    private int goalCell;
    private byte goalMove;
    private int searchRoot;
    private byte searchRoom;
    private int searchCostLimit;

    private DistrictPlanner(long worldSeed, DistrictPos pos)
    {
        this.worldSeed = worldSeed;
        this.pos = pos;
        theme = DaedalusTheme.at(pos);
        layout = theme.layout();
        random = new SplittableRandom(DaedalusHash.mix(worldSeed, pos.x(), pos.z(), PLAN_SALT));
        Arrays.fill(parent, -1);
        for (int cell = 0; cell < CELL_COUNT; cell++)
            noise[cell] = (byte) random.nextInt(layout.jitter() + 1);
    }

    static DistrictPlan generate(long worldSeed, DistrictPos pos)
    {
        var planner = new DistrictPlanner(worldSeed, pos);
        planner.placeLandings();
        planner.placeTheme();
        planner.connectEverything();
        planner.grow();
        planner.addLoops();
        planner.openRoomsOnTheWay();
        planner.widenCorridorsIntoRooms();
        return planner.build();
    }

    private static int tierOf(int cell)
    {
        return cell / (DistrictPos.CELLS * DistrictPos.CELLS);
    }

    private static int neighbor(int cell, int direction)
    {
        int i = (cell & LAST_CELL) + Compass.dx(direction);
        int j = ((cell >> 4) & LAST_CELL) + Compass.dz(direction);
        if (i < 0 || j < 0 || i > LAST_CELL || j > LAST_CELL)
            return -1;
        return index(i, tierOf(cell), j);
    }

    private static int above(int cell)
    {
        return tierOf(cell) == DistrictPos.TIERS - 1 ? -1 : cell + DistrictPos.CELLS * DistrictPos.CELLS;
    }

    private static int below(int cell)
    {
        return tierOf(cell) == 0 ? -1 : cell - DistrictPos.CELLS * DistrictPos.CELLS;
    }

    private int find(int cell)
    {
        int root = cell;
        while (parent[root] != root)
            root = parent[root];
        while (parent[cell] != root)
        {
            int next = parent[cell];
            parent[cell] = root;
            cell = next;
        }
        return root;
    }

    private void union(int a, int b)
    {
        parent[find(a)] = find(b);
    }

    private void claim(int cell, byte kind)
    {
        if (kinds[cell] == EMPTY)
        {
            carved++;
            parent[cell] = cell;
        }
        kinds[cell] = kind;
    }

    private void open(int cell, int direction)
    {
        faces[cell] |= (byte) (1 << direction);
    }

    private void link(int from, int direction, int to)
    {
        open(from, direction);
        open(to, Compass.opposite(direction));
        union(from, to);
    }

    private void placeLandings()
    {
        var west = new DistrictPos(pos.x() - 1, pos.z());
        var north = new DistrictPos(pos.x(), pos.z() - 1);
        for (var bridge : DaedalusBridges.between(worldSeed, pos, Compass.EAST))
            placeLanding(LAST_CELL, bridge.fromTier(), bridge.cell(), Compass.EAST);
        for (var bridge : DaedalusBridges.between(worldSeed, pos, Compass.SOUTH))
            placeLanding(bridge.cell(), bridge.fromTier(), LAST_CELL, Compass.SOUTH);
        for (var bridge : DaedalusBridges.between(worldSeed, west, Compass.EAST))
            placeLanding(0, bridge.toTier(), bridge.cell(), Compass.WEST);
        for (var bridge : DaedalusBridges.between(worldSeed, north, Compass.SOUTH))
            placeLanding(bridge.cell(), bridge.toTier(), 0, Compass.NORTH);
    }

    private void placeLanding(int i, int k, int j, int direction)
    {
        int cell = index(i, k, j);
        claim(cell, CORRIDOR);
        open(cell, direction);
        landings.add(new Landing(i, k, j, direction));
    }

    private void placeTheme()
    {
        switch (theme)
        {
            case ARCHIVE -> placeArchive();
            case WARREN -> placeWarren();
            case GALLERY -> placeGallery();
            case CISTERN -> placeCistern();
            case STAIRWELL -> placeStairwell();
            case WRITHING -> placeWrithing();
        }
    }

    private int between(int min, int max)
    {
        return min + random.nextInt(max - min + 1);
    }

    private void placeArchive()
    {
        boolean alongX = random.nextBoolean();
        int variant = random.nextInt() & ~1 | (alongX ? 1 : 0);
        for (int hall = between(4, 7); hall > 0; hall--)
        {
            int length = between(5, 10);
            int across = between(3, 6);
            tryRoom(RoomShape.STACKS, alongX ? length : across, between(1, 2), alongX ? across : length, variant, 1);
        }
        scatterRooms(RoomShape.PLAIN, between(10, 16), 1, 3, 1, 3, 1, 1);
        for (int aisle = between(2, 4); aisle > 0; aisle--)
        {
            int row = random.nextInt(DistrictPos.CELLS);
            int tier = random.nextInt(DistrictPos.TIERS);
            int start = alongX ? index(1, tier, row) : index(row, tier, 1);
            if (kinds[start] == EMPTY)
                carveRun(start, alongX ? Compass.EAST : Compass.SOUTH, DistrictPos.CELLS - 2);
        }
    }

    private void placeWarren()
    {
        scatterRooms(RoomShape.CELL, between(50, 80), 1, 1, 1, 1, 1, 1);
        for (int walker = between(16, 24); walker > 0; walker--)
            wander(index(random.nextInt(DistrictPos.CELLS), random.nextInt(DistrictPos.TIERS), random.nextInt(DistrictPos.CELLS)), between(10, 26));
    }

    private void placeGallery()
    {
        scatterRooms(RoomShape.COLONNADE, between(8, 12), 3, 6, 2, 4, 2, 2);
        scatterRooms(RoomShape.PLAIN, between(6, 10), 2, 3, 2, 3, 1, 1);
    }

    private void placeCistern()
    {
        scatterRooms(RoomShape.FLOOD, between(5, 8), 3, 7, 3, 6, 2, 1);
        scatterRooms(RoomShape.PLAIN, between(8, 12), 2, 3, 2, 3, 1, 1);
    }

    private void placeStairwell()
    {
        for (int atrium = between(3, 5); atrium > 0; atrium--)
            tryRoom(RoomShape.ATRIUM, between(2, 4), between(5, DistrictPos.TIERS), between(2, 4), random.nextInt(), 1);
        scatterRooms(RoomShape.PLAIN, between(8, 12), 1, 2, 1, 2, 1, 1);
    }

    private void placeWrithing()
    {
        scatterRooms(RoomShape.CHAMBER, between(20, 32), 1, 3, 1, 3, 1, 1);
    }

    private void scatterRooms(RoomShape shape, int count, int minWide, int maxWide, int minDeep, int maxDeep, int tall, int tierStep)
    {
        for (int room = 0; room < count; room++)
            tryRoom(shape, between(minWide, maxWide), tall, between(minDeep, maxDeep), random.nextInt(), tierStep);
    }

    /**
     * Tries a few places, since a crowded lattice refuses most of them.
     */
    private void tryRoom(RoomShape shape, int wide, int tall, int deep, int variant, int tierStep)
    {
        int spareTiers = (DistrictPos.TIERS - tall) / tierStep;
        for (int attempt = 0; attempt < ROOM_PLACE_ATTEMPTS; attempt++)
        {
            int minI = 1 + random.nextInt(DistrictPos.CELLS - 2 - wide + 1);
            int minJ = 1 + random.nextInt(DistrictPos.CELLS - 2 - deep + 1);
            int minK = random.nextInt(spareTiers + 1) * tierStep;
            if (placeRoom(shape, minI, minK, minJ, wide, tall, deep, variant))
                return;
        }
    }

    private boolean placeRoom(RoomShape shape, int minI, int minK, int minJ, int wide, int tall, int deep, int variant)
    {
        if (rooms.size() >= MAX_ROOMS || minI < 1 || minJ < 1 || minI + wide > DistrictPos.CELLS - 1 || minJ + deep > DistrictPos.CELLS - 1)
            return false;
        for (int k = minK; k < minK + tall; k++)
        {
            for (int j = minJ; j < minJ + deep; j++)
            {
                for (int i = minI; i < minI + wide; i++)
                {
                    if (kinds[index(i, k, j)] != EMPTY)
                        return false;
                }
            }
        }

        rooms.add(new Room(minI, minK, minJ, minI + wide - 1, minK + tall - 1, minJ + deep - 1, shape, variant));
        byte id = (byte) rooms.size();
        int first = index(minI, minK, minJ);
        for (int k = minK; k < minK + tall; k++)
        {
            for (int j = minJ; j < minJ + deep; j++)
            {
                for (int i = minI; i < minI + wide; i++)
                {
                    int cell = index(i, k, j);
                    claim(cell, ROOM);
                    roomIds[cell] = id;
                    union(cell, first);
                }
            }
        }
        return true;
    }

    private void carveRun(int start, int direction, int length)
    {
        claim(start, CORRIDOR);
        walkFrom(start, direction, length - 1);
    }

    /**
     * @return the cell the walk ended on, or -1 if it ran into something and so needs no ending of its own
     */
    private int walkFrom(int start, int direction, int length)
    {
        int current = start;
        for (int step = 0; step < length; step++)
        {
            int next = neighbor(current, direction);
            if (next < 0)
                return current;
            if (kinds[next] != EMPTY)
            {
                if (hasOpening(next, Compass.opposite(direction)))
                    link(current, direction, next);
                return -1;
            }
            claim(next, CORRIDOR);
            link(current, direction, next);
            current = next;
        }
        return current;
    }

    private void wander(int start, int length)
    {
        if (kinds[start] != EMPTY)
            return;

        claim(start, CORRIDOR);
        int current = start;
        int direction = random.nextInt(Compass.COUNT);
        for (int step = 0; step < length; step++)
        {
            if (random.nextInt(100) < 35)
                direction = random.nextBoolean() ? Compass.clockwise(direction) : Compass.counterClockwise(direction);
            int next = neighbor(current, direction);
            if (next < 0)
                continue;
            if (kinds[next] != EMPTY)
            {
                if (hasOpening(next, Compass.opposite(direction)))
                    link(current, direction, next);
                return;
            }
            claim(next, CORRIDOR);
            link(current, direction, next);
            current = next;
        }
    }

    /**
     * @return whether a passage can arrive at or leave from this side of the cell
     */
    private boolean hasOpening(int cell, int direction)
    {
        if (kinds[cell] == CORRIDOR)
            return true;
        if (kinds[cell] != ROOM)
            return false;

        int outside = neighbor(cell, direction);
        if (outside < 0 || roomIds[outside] == roomIds[cell])
            return false;
        var room = rooms.get((roomIds[cell] & 0xFF) - 1);
        return room.shape().hasDoorsAt(tierOf(cell), room);
    }

    private void connectEverything()
    {
        while (true)
        {
            int unconnected = pickUnconnectedRoot();
            if (unconnected < 0)
                return;
            if (!routeWithRetries(unconnected))
                resolveUnroutable(unconnected);
        }
    }

    /**
     * @return the root of a structure other than the first one, or -1 once all are joined
     */
    private int pickUnconnectedRoot()
    {
        int first = -1;
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (kinds[cell] == EMPTY)
                continue;
            int root = find(cell);
            if (first == -1)
                first = root;
            else if (root != first)
                return root;
        }
        return -1;
    }

    private boolean routeWithRetries(int root)
    {
        return search(root, (byte) 0, null, 0, UNREACHED);
    }

    /**
     * @param room        when not 0, routes from {@code sourceCells} of that room to anything but the room itself, instead of from structure {@code root} to any other
     * @param sourceCells the cells to start from, leaving by {@code sourceSide}, or null to start from all of {@code root}
     */
    private boolean search(int root, byte room, int[] sourceCells, int sourceSide, int costLimit)
    {
        searchRoot = root;
        searchRoom = room;
        searchCostLimit = costLimit;
        Arrays.fill(stairBanned, false);
        for (int attempt = 0; attempt < ROUTE_ATTEMPTS; attempt++)
        {
            int result = route(sourceCells, sourceSide);
            if (result != ROUTE_RETRY)
                return result == ROUTE_DONE;
        }
        return false;
    }

    private int route(int[] sourceCells, int sourceSide)
    {
        Arrays.fill(costs, UNREACHED);
        heapSize = 0;
        if (sourceCells != null)
        {
            for (int cell : sourceCells)
                relax(cell * Compass.COUNT + sourceSide, 0, -1, MOVE_START);
        }
        else
        {
            for (int cell = 0; cell < CELL_COUNT; cell++)
            {
                if (kinds[cell] == EMPTY || find(cell) != searchRoot)
                    continue;
                for (int direction = 0; direction < Compass.COUNT; direction++)
                {
                    if (hasOpening(cell, direction))
                        relax(cell * Compass.COUNT + direction, 0, -1, MOVE_START);
                }
            }
        }

        while (heapSize > 0)
        {
            long top = pop();
            int state = (int) top;
            int cost = (int) (top >>> 32);
            if (cost > searchCostLimit)
                return ROUTE_NONE;
            if (cost != costs[state])
                continue;
            if (expand(state, cost))
                return carvePath() ? ROUTE_DONE : ROUTE_RETRY;
        }
        return ROUTE_NONE;
    }

    /**
     * @param arrivalDirection the way the search moves as it reaches the cell
     */
    private boolean isGoal(int cell, int arrivalDirection)
    {
        boolean isStart = searchRoom != 0 ? roomIds[cell] == searchRoom : find(cell) == searchRoot;
        return !isStart && hasOpening(cell, Compass.opposite(arrivalDirection));
    }

    /**
     * @return whether another structure was reached from this state
     */
    private boolean expand(int state, int cost)
    {
        int cell = state / Compass.COUNT;
        int direction = state % Compass.COUNT;
        if (kinds[cell] == EMPTY)
        {
            relax(cell * Compass.COUNT + Compass.clockwise(direction), cost + layout.turnCost(), state, MOVE_TURN);
            relax(cell * Compass.COUNT + Compass.counterClockwise(direction), cost + layout.turnCost(), state, MOVE_TURN);
        }

        int next = neighbor(cell, direction);
        if (next < 0)
            return false;
        if (kinds[next] != EMPTY)
        {
            return isGoal(next, direction) && reachGoal(state, MOVE_WALK, next);
        }

        relax(next * Compass.COUNT + direction, cost + STEP_COST + noise[next], state, MOVE_WALK);
        return expandStair(state, cost, direction, above(next), next, MOVE_UP)
               || expandStair(state, cost, direction, next, below(next), MOVE_DOWN);
    }

    private boolean expandStair(int state, int cost, int direction, int high, int low, byte move)
    {
        if (high < 0 || low < 0 || kinds[high] != EMPTY || kinds[low] != EMPTY || stairBanned[high] || stairBanned[low])
            return false;

        int exit = neighbor(move == MOVE_UP ? high : low, direction);
        if (exit < 0)
            return false;
        if (kinds[exit] == EMPTY)
        {
            relax(exit * Compass.COUNT + direction, cost + STEP_COST * 2 + layout.stairCost() + noise[high] + noise[low] + noise[exit], state, move);
            return false;
        }
        return isGoal(exit, direction) && reachGoal(state, move, exit);
    }

    private boolean reachGoal(int state, byte move, int cell)
    {
        goalState = state;
        goalMove = move;
        goalCell = cell;
        return true;
    }

    private void relax(int state, int cost, int from, byte move)
    {
        if (cost >= costs[state])
            return;
        costs[state] = cost;
        previous[state] = from;
        moves[state] = move;
        push(((long) cost << 32) | state);
    }

    /**
     * The search does not know the cells a flight of its own path has taken, so a path can double back through one. That is checked here, and the cell then barred from flights for the next attempt.
     */
    private boolean carvePath()
    {
        Arrays.fill(claims, (byte) 0);
        boolean clear = claimMove(goalState, goalMove, goalCell);
        for (int state = goalState; clear && moves[state] != MOVE_START; state = previous[state])
        {
            if (moves[state] != MOVE_TURN)
                clear = claimMove(previous[state], moves[state], state / Compass.COUNT);
        }
        if (!clear)
            return false;

        applyMove(goalState, goalMove, goalCell);
        for (int state = goalState; moves[state] != MOVE_START; state = previous[state])
        {
            if (moves[state] != MOVE_TURN)
                applyMove(previous[state], moves[state], state / Compass.COUNT);
        }
        return true;
    }

    private boolean claimMove(int fromState, byte move, int to)
    {
        int direction = fromState % Compass.COUNT;
        int through = neighbor(fromState / Compass.COUNT, direction);
        if (move == MOVE_WALK)
            return claimCell(to, (byte) 1);
        int other = move == MOVE_UP ? above(through) : below(through);
        return claimCell(through, (byte) 2) && claimCell(other, (byte) 2) && claimCell(to, (byte) 1);
    }

    private boolean claimCell(int cell, byte role)
    {
        if (claims[cell] != 0 && (claims[cell] != role || role == 2))
        {
            stairBanned[cell] = true;
            return false;
        }
        claims[cell] = role;
        return true;
    }

    private void applyMove(int fromState, byte move, int to)
    {
        int from = fromState / Compass.COUNT;
        int direction = fromState % Compass.COUNT;
        if (kinds[from] == EMPTY)
            claim(from, CORRIDOR);
        if (kinds[to] == EMPTY)
            claim(to, CORRIDOR);
        if (move == MOVE_WALK)
        {
            link(from, direction, to);
            return;
        }

        int through = neighbor(from, direction);
        int low = move == MOVE_UP ? through : below(through);
        int high = move == MOVE_UP ? above(through) : through;
        int climb = move == MOVE_UP ? direction : Compass.opposite(direction);
        claim(low, STAIR_LOW);
        claim(high, STAIR_HIGH);
        faces[low] = (byte) climb;
        faces[high] = (byte) climb;
        open(from, direction);
        open(to, Compass.opposite(direction));
        union(from, low);
        union(low, high);
        union(high, to);
    }

    /**
     * Rooms and passages are optional, landings are not. Whatever cannot be joined goes, unless it holds a landing, in which case what stands around it goes instead.
     */
    private void resolveUnroutable(int root)
    {
        boolean holdsLanding = false;
        for (var landing : landings)
            holdsLanding |= find(index(landing.i(), landing.k(), landing.j())) == root;
        if (!holdsLanding)
        {
            clearStructure(root);
            return;
        }

        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (kinds[cell] == ROOM && find(cell) != root)
            {
                clearRoomOf(cell);
                rebuildStructures();
                return;
            }
        }
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (kinds[cell] == STAIR_LOW && find(cell) != root)
            {
                int high = above(cell);
                clearCell(cell);
                clearCell(high);
                rebuildStructures();
                return;
            }
        }
        throw new IllegalStateException("District " + pos + " cannot connect a bridge landing");
    }

    private void clearStructure(int root)
    {
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (kinds[cell] == EMPTY || find(cell) != root)
                continue;
            if (kinds[cell] == ROOM)
                clearRoomOf(cell);
            else
                clearCell(cell);
        }
        rebuildStructures();
    }

    private void clearRoomOf(int roomCell)
    {
        byte id = roomIds[roomCell];
        rooms.set((id & 0xFF) - 1, null);
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (roomIds[cell] == id)
                clearCell(cell);
        }
    }

    private void clearCell(int cell)
    {
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            int outside = neighbor(cell, direction);
            if (outside >= 0 && (kinds[outside] == CORRIDOR || kinds[outside] == ROOM))
                faces[outside] &= (byte) ~(1 << Compass.opposite(direction));
        }
        kinds[cell] = EMPTY;
        faces[cell] = 0;
        roomIds[cell] = 0;
        carved--;
    }

    private void rebuildStructures()
    {
        Arrays.fill(parent, -1);
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            if (kinds[cell] != EMPTY)
                parent[cell] = cell;
        }
        int[] firstOfRoom = new int[rooms.size() + 1];
        Arrays.fill(firstOfRoom, -1);
        for (int cell = 0; cell < CELL_COUNT; cell++)
        {
            byte kind = kinds[cell];
            if (kind == EMPTY)
                continue;
            if (kind == ROOM)
            {
                int id = roomIds[cell] & 0xFF;
                if (firstOfRoom[id] < 0)
                    firstOfRoom[id] = cell;
                union(cell, firstOfRoom[id]);
            }
            if (kind == STAIR_LOW)
            {
                union(cell, above(cell));
                joinIfPresent(cell, neighbor(cell, Compass.opposite(faces[cell])));
                joinIfPresent(above(cell), neighbor(above(cell), faces[cell]));
                continue;
            }
            if (kind == STAIR_HIGH)
                continue;
            for (int direction = 0; direction < Compass.COUNT; direction++)
            {
                int outside = neighbor(cell, direction);
                if ((faces[cell] & (1 << direction)) != 0 && outside >= 0 && kinds[outside] != EMPTY && kinds[outside] != STAIR_LOW && kinds[outside] != STAIR_HIGH)
                    union(cell, outside);
            }
        }
    }

    private void joinIfPresent(int cell, int other)
    {
        if (other >= 0 && kinds[other] != EMPTY)
            union(cell, other);
    }

    /**
     * Side passages fill what routing left solid, and most of them lead to a room, so a district never reads as a few lines through rock.
     */
    private void grow()
    {
        int target = (int) (layout.density() * CELL_COUNT);
        for (int attempt = 0; attempt < CELL_COUNT * 2 && carved < target; attempt++)
        {
            int cell = random.nextInt(CELL_COUNT);
            if (kinds[cell] != CORRIDOR)
                continue;
            int direction = random.nextInt(Compass.COUNT);
            int outside = neighbor(cell, direction);
            if (outside < 0 || kinds[outside] != EMPTY)
                continue;

            int end = walkFrom(cell, direction, between(1, 4));
            if (end >= 0 && random.nextInt(100) < ROOM_ENDING_PERCENT)
                endInRoom(end, direction);
        }
    }

    private void endInRoom(int end, int direction)
    {
        var shape = switch (theme)
        {
            case WARREN -> RoomShape.CELL;
            case WRITHING -> RoomShape.CHAMBER;
            default -> RoomShape.PLAIN;
        };
        int wide = shape == RoomShape.CELL ? 1 : between(1, 3);
        int deep = shape == RoomShape.CELL ? 1 : between(1, 3);
        int entrance = neighbor(end, direction);
        if (entrance < 0 || kinds[entrance] != EMPTY)
            return;

        int entranceI = entrance & LAST_CELL;
        int entranceJ = (entrance >> 4) & LAST_CELL;
        int minI = Compass.dx(direction) > 0 ? entranceI : Compass.dx(direction) < 0 ? entranceI - wide + 1 : entranceI - random.nextInt(wide);
        int minJ = Compass.dz(direction) > 0 ? entranceJ : Compass.dz(direction) < 0 ? entranceJ - deep + 1 : entranceJ - random.nextInt(deep);
        if (placeRoom(shape, minI, tierOf(end), minJ, wide, 1, deep, random.nextInt()))
            link(end, direction, entrance);
    }

    private void addLoops()
    {
        int attempts = CELL_COUNT / 16;
        for (int attempt = 0; attempt < attempts; attempt++)
        {
            int cell = random.nextInt(CELL_COUNT);
            if (kinds[cell] != CORRIDOR)
                continue;
            int direction = random.nextInt(Compass.COUNT);
            int outside = neighbor(cell, direction);
            if (outside < 0)
                continue;
            if (kinds[outside] == CORRIDOR)
                link(cell, direction, outside);
        }
    }

    /**
     * Routing alone joins every room to the rest by a single door, leaving rooms as dead ends beside the corridors people actually walk.
     * Doors on several sides, onto a corridor already running past or a short new passage, put the rooms on the way instead.
     */
    private void openRoomsOnTheWay()
    {
        for (int index = 0; index < rooms.size(); index++)
        {
            var room = rooms.get(index);
            if (room == null)
                continue;

            byte id = (byte) (index + 1);
            int wanted = room.shape() == RoomShape.CELL ? CELL_DOOR_SIDES : ROOM_DOOR_SIDES;
            int doored = 0;
            for (int side = 0; side < Compass.COUNT; side++)
                doored += hasDoorOn(room, side) ? 1 : 0;

            int firstSide = random.nextInt(Compass.COUNT);
            for (int turn = 0; turn < Compass.COUNT && doored < wanted; turn++)
            {
                int side = (firstSide + turn) & 3;
                if (!hasDoorOn(room, side) && connectSide(room, id, side))
                    doored++;
            }
        }
    }

    private boolean hasDoorOn(Room room, int side)
    {
        for (int cell : sideCells(room, side, false))
        {
            if ((faces[cell] & (1 << side)) != 0)
                return true;
        }
        return false;
    }

    /**
     * @param onlyDoorable keep only the cells a door can be opened from
     */
    private int[] sideCells(Room room, int side, boolean onlyDoorable)
    {
        boolean alongX = side == Compass.NORTH || side == Compass.SOUTH;
        int fixed = switch (side)
        {
            case Compass.NORTH -> room.minJ();
            case Compass.SOUTH -> room.maxJ();
            case Compass.WEST -> room.minI();
            default -> room.maxI();
        };
        int from = alongX ? room.minI() : room.minJ();
        int to = alongX ? room.maxI() : room.maxJ();
        var cells = new int[(to - from + 1) * (room.maxK() - room.minK() + 1)];
        int count = 0;
        for (int k = room.minK(); k <= room.maxK(); k++)
        {
            for (int along = from; along <= to; along++)
            {
                int cell = alongX ? index(along, k, fixed) : index(fixed, k, along);
                if (!onlyDoorable || hasOpening(cell, side))
                    cells[count++] = cell;
            }
        }
        return Arrays.copyOf(cells, count);
    }

    private boolean connectSide(Room room, byte id, int side)
    {
        int[] cells = sideCells(room, side, true);
        if (cells.length == 0)
            return false;

        int offset = random.nextInt(cells.length);
        for (int index = 0; index < cells.length; index++)
        {
            int cell = cells[(index + offset) % cells.length];
            int outside = neighbor(cell, side);
            if (kinds[outside] != EMPTY && roomIds[outside] != id && hasOpening(outside, Compass.opposite(side)))
            {
                link(cell, side, outside);
                return true;
            }
        }
        return search(-1, id, cells, side, THROUGH_DOOR_COST_LIMIT);
    }

    /**
     * Turns junctions, dead ends and some plain stretches of corridor into rooms, so that walking any passage soon opens into one.
     * A room grows into the solid rock beside it where it can, since a single cell is hardly wider than a corridor. Doors stay where the corridor's arms were, so the routing is unchanged.
     */
    private void widenCorridorsIntoRooms()
    {
        var shape = switch (theme)
        {
            case WARREN -> RoomShape.CELL;
            case WRITHING -> RoomShape.CHAMBER;
            default -> RoomShape.PLAIN;
        };
        int junctionPercent = theme == DaedalusTheme.WARREN ? 40 : 70;
        int deadEndPercent = 70;
        int passagePercent = theme == DaedalusTheme.WARREN ? 5 : 15;
        for (int cell = 0; cell < CELL_COUNT && rooms.size() < MAX_ROOMS; cell++)
        {
            int i = cell & LAST_CELL;
            int j = (cell >> 4) & LAST_CELL;
            if (kinds[cell] != CORRIDOR || i == 0 || j == 0 || i == LAST_CELL || j == LAST_CELL)
                continue;

            int open = Integer.bitCount(faces[cell] & 15);
            int percent = open >= 3 ? junctionPercent : open == 1 ? deadEndPercent : passagePercent;
            if (random.nextInt(100) >= percent)
                continue;

            int k = tierOf(cell);
            int[] box = shape == RoomShape.CELL ? new int[] { i, j, i, j } : roomyBoxAround(i, k, j);
            boolean singleCell = box[0] == box[2] && box[1] == box[3];
            if (singleCell && layout.corridorWidth() >= WIDE_CORRIDOR)
                continue;
            rooms.add(new Room(box[0], k, box[1], box[2], k, box[3], shape, random.nextInt()));
            byte id = (byte) rooms.size();
            for (int boxJ = box[1]; boxJ <= box[3]; boxJ++)
            {
                for (int boxI = box[0]; boxI <= box[2]; boxI++)
                {
                    int roomCell = index(boxI, k, boxJ);
                    claim(roomCell, ROOM);
                    roomIds[roomCell] = id;
                    union(roomCell, cell);
                }
            }
        }
    }

    /**
     * @return {minI, minJ, maxI, maxJ} of the largest box holding the cell, up to 2 x 2, whose other cells are all solid and off the outer ring
     */
    private int[] roomyBoxAround(int i, int k, int j)
    {
        int[][] shapes = { { 2, 2 }, { 2, 1 }, { 1, 2 } };
        for (int[] size : shapes)
        {
            int offset = random.nextInt(4);
            for (int attempt = 0; attempt < 4; attempt++)
            {
                int corner = (attempt + offset) & 3;
                int minI = i - (size[0] == 2 && (corner & 1) != 0 ? 1 : 0);
                int minJ = j - (size[1] == 2 && (corner & 2) != 0 ? 1 : 0);
                if (isSolidAround(i, k, j, minI, minJ, minI + size[0] - 1, minJ + size[1] - 1))
                    return new int[] { minI, minJ, minI + size[0] - 1, minJ + size[1] - 1 };
            }
        }
        return new int[] { i, j, i, j };
    }

    private boolean isSolidAround(int i, int k, int j, int minI, int minJ, int maxI, int maxJ)
    {
        if (minI < 1 || minJ < 1 || maxI > LAST_CELL - 1 || maxJ > LAST_CELL - 1)
            return false;
        for (int boxJ = minJ; boxJ <= maxJ; boxJ++)
        {
            for (int boxI = minI; boxI <= maxI; boxI++)
            {
                if ((boxI != i || boxJ != j) && kinds[index(boxI, k, boxJ)] != EMPTY)
                    return false;
            }
        }
        return true;
    }

    private DistrictPlan build()
    {
        var kept = new ArrayList<Room>(rooms.size());
        byte[] remapped = new byte[rooms.size() + 1];
        for (int index = 0; index < rooms.size(); index++)
        {
            if (rooms.get(index) == null)
                continue;
            kept.add(rooms.get(index));
            remapped[index + 1] = (byte) kept.size();
        }
        for (int cell = 0; cell < CELL_COUNT; cell++)
            roomIds[cell] = remapped[roomIds[cell] & 0xFF];
        return new DistrictPlan(worldSeed, pos, theme, kinds, faces, roomIds, kept.toArray(Room[]::new), List.copyOf(landings));
    }

    private void push(long value)
    {
        if (heapSize == heap.length)
            heap = Arrays.copyOf(heap, heap.length * 2);
        int at = heapSize++;
        while (at > 0)
        {
            int parentAt = (at - 1) / 2;
            if (heap[parentAt] <= value)
                break;
            heap[at] = heap[parentAt];
            at = parentAt;
        }
        heap[at] = value;
    }

    private long pop()
    {
        long top = heap[0];
        long last = heap[--heapSize];
        int at = 0;
        while (true)
        {
            int child = at * 2 + 1;
            if (child >= heapSize)
                break;
            if (child + 1 < heapSize && heap[child + 1] < heap[child])
                child++;
            if (heap[child] >= last)
                break;
            heap[at] = heap[child];
            at = child;
        }
        heap[at] = last;
        return top;
    }
}
