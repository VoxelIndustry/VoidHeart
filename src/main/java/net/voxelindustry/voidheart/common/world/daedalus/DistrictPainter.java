package net.voxelindustry.voidheart.common.world.daedalus;

import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMaterial.*;
import static net.voxelindustry.voidheart.common.world.daedalus.DistrictPos.CELL;
import static net.voxelindustry.voidheart.common.world.daedalus.DistrictPos.floorY;

/**
 * Draws the part of a plan that falls inside a canvas. Each cell only ever draws inside its own cube, which keeps a chunk's cost at the handful of cells it holds and makes chunk borders invisible.
 * A cell's local coordinates run from 0 to 7, its floor is local y 0 and lanes are centred on local 3.
 */
final class DistrictPainter
{
    private static final int LAST = CELL - 1;
    private static final int LANE_CENTRE = 3;
    private static final int FULL_ROOM_TOP = 6;
    private static final int STAIR_RUN = CELL;
    private static final int MIN_STAIR_HEAD = 3;
    private static final int PILASTER_SPACING = 16;
    private static final int RING_WIDTH = 3;
    private static final int RING_CORNER_LAMP = 1;
    private static final int COLUMN_LAMP_HEIGHT = 4;
    private static final int SHELF_UPRIGHT_SPACING = 4;
    private static final int POOL_WALKWAY = 2;
    private static final int RANDOM_BITS = 8;

    private final DistrictPlan plan;
    private final DaedalusTheme theme;
    private final DaedalusTheme.Layout layout;
    private final DaedalusCanvas canvas;
    private final int originX;
    private final int originZ;

    private DistrictPainter(DistrictPlan plan, DaedalusCanvas canvas)
    {
        this.plan = plan;
        this.canvas = canvas;
        theme = plan.theme;
        layout = theme.layout();
        originX = plan.pos.originX();
        originZ = plan.pos.originZ();
    }

    static void paint(DistrictPlan plan, DaedalusCanvas canvas)
    {
        new DistrictPainter(plan, canvas).paint();
    }

    private static int laneLow(int width)
    {
        return LANE_CENTRE - (width - 1) / 2;
    }

    private static int laneHigh(int width)
    {
        return LANE_CENTRE + width / 2;
    }

    private int cellX(int i)
    {
        return originX + i * CELL;
    }

    private int cellZ(int j)
    {
        return originZ + j * CELL;
    }

    private void paint()
    {
        int minI = Math.max(0, Math.floorDiv(canvas.minX() - originX, CELL));
        int maxI = Math.min(DistrictPos.CELLS - 1, Math.floorDiv(canvas.maxX() - originX, CELL));
        int minJ = Math.max(0, Math.floorDiv(canvas.minZ() - originZ, CELL));
        int maxJ = Math.min(DistrictPos.CELLS - 1, Math.floorDiv(canvas.maxZ() - originZ, CELL));
        if (minI > maxI || minJ > maxJ)
            return;

        canvas.fill(originX, 1, originZ, originX + DistrictPos.SIZE - 1, DistrictPos.TOP_Y - 1, originZ + DistrictPos.SIZE - 1, FILL);
        for (int k = 0; k < DistrictPos.TIERS; k++)
        {
            for (int j = minJ; j <= maxJ; j++)
            {
                for (int i = minI; i <= maxI; i++)
                    paintCell(i, k, j);
            }
        }
        for (var room : plan.rooms())
        {
            if (canvas.overlaps(cellX(room.minI()), cellZ(room.minJ()), cellX(room.maxI()) + LAST, cellZ(room.maxJ()) + LAST))
                decorate(room);
        }
        paintFacade();
        for (var landing : plan.landings())
            paintLandingFrame(landing);
    }

    private void paintCell(int i, int k, int j)
    {
        switch (plan.kind(i, k, j))
        {
            case DistrictPlan.CORRIDOR -> paintCorridor(i, k, j);
            case DistrictPlan.ROOM -> paintRoomCell(i, k, j);
            case DistrictPlan.STAIR_LOW -> paintStairs(i, k, j);
            default ->
            {
                // Empty cells stay solid and the upper half of stairs is drawn with the lower half.
            }
        }
    }

    private int corridorWidth(int i, int k, int j)
    {
        if (theme == DaedalusTheme.WARREN && (plan.cellHash(i, k, j) & 1) == 0)
            return 2;
        return layout.corridorWidth();
    }

    private void paintCorridor(int i, int k, int j)
    {
        long hash = plan.cellHash(i, k, j);
        int armWidth = corridorWidth(i, k, j);
        int height = layout.corridorHeight();
        int hubWidth = armWidth;
        int hubHeight = height;
        if (theme == DaedalusTheme.WRITHING && (hash & 3) == 0)
        {
            hubWidth = armWidth + 2;
            hubHeight = height + 1;
        }
        paintPassage(i, k, j, hubWidth, hubHeight, armWidth, height, hash);
    }

    /**
     * Drawn in three passes because every piece's walls overlap the others' floors and every piece's floor overlaps the others' air.
     */
    private void paintPassage(int i, int k, int j, int hubWidth, int hubHeight, int armWidth, int armHeight, long hash)
    {
        int x = cellX(i);
        int y = floorY(k);
        int z = cellZ(j);
        int hubLow = laneLow(hubWidth);
        int hubHigh = laneHigh(hubWidth);
        int armLow = laneLow(armWidth);
        int armHigh = laneHigh(armWidth);
        int faces = plan.faces(i, k, j);

        shell(x, y, z, x + hubLow, z + hubLow, x + hubHigh, z + hubHigh, hubHeight);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if ((faces & (1 << direction)) != 0)
                armPass(x, y, z, direction, armLow, armHigh, hubLow, hubHigh, armHeight, Pass.SHELL);
        }
        lining(x, y, z, x + hubLow, z + hubLow, x + hubHigh, z + hubHigh, hubHeight);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if ((faces & (1 << direction)) != 0)
                armPass(x, y, z, direction, armLow, armHigh, hubLow, hubHigh, armHeight, Pass.LINING);
        }
        canvas.fill(x + hubLow, y + 1, z + hubLow, x + hubHigh, y + hubHeight, z + hubHigh, AIR);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if ((faces & (1 << direction)) != 0)
            {
                armPass(x, y, z, direction, armLow, armHigh, hubLow, hubHigh, armHeight, Pass.AIR);
                paintFrame(x, y, z, direction, armLow, armHigh, armHeight);
            }
        }
        if (DaedalusHash.pick(hash >>> RANDOM_BITS, layout.lampEvery()) == 0)
            canvas.set(x + LANE_CENTRE, y + hubHeight + 1, z + LANE_CENTRE, LAMP);
    }

    private enum Pass
    {
        SHELL,
        LINING,
        AIR
    }

    private void armPass(int x, int y, int z, int direction, int armLow, int armHigh, int hubLow, int hubHigh, int height, Pass pass)
    {
        int x0 = x + armLow;
        int x1 = x + armHigh;
        int z0 = z + armLow;
        int z1 = z + armHigh;
        switch (direction)
        {
            case Compass.NORTH ->
            {
                z0 = z;
                z1 = z + hubLow - 1;
            }
            case Compass.SOUTH ->
            {
                z0 = z + hubHigh + 1;
                z1 = z + LAST;
            }
            case Compass.WEST ->
            {
                x0 = x;
                x1 = x + hubLow - 1;
            }
            default ->
            {
                x0 = x + hubHigh + 1;
                x1 = x + LAST;
            }
        }
        switch (pass)
        {
            case SHELL -> shell(x, y, z, x0, z0, x1, z1, height);
            case LINING -> lining(x, y, z, x0, z0, x1, z1, height);
            case AIR -> canvas.fill(x0, y + 1, z0, x1, y + height, z1, AIR);
        }
    }

    /**
     * @param x0 x1 z0 z1 the walkable footprint, whose walls are clipped to the cell starting at x y z
     */
    private void shell(int x, int y, int z, int x0, int z0, int x1, int z1, int height)
    {
        int wallX0 = Math.max(x, x0 - 1);
        int wallX1 = Math.min(x + LAST, x1 + 1);
        int wallZ0 = Math.max(z, z0 - 1);
        int wallZ1 = Math.min(z + LAST, z1 + 1);
        canvas.fill(wallX0, y, wallZ0, wallX1, y + height + 1, wallZ1, WALL);
        switch (theme)
        {
            case ARCHIVE -> canvas.fill(wallX0, y + 2, wallZ0, wallX1, y + height - 1, wallZ1, SHELF);
            case GALLERY ->
            {
                canvas.fill(wallX0, y + 1, wallZ0, wallX1, y + 1, wallZ1, TRIM);
                canvas.fill(wallX0, y + height, wallZ0, wallX1, y + height, wallZ1, PANEL);
            }
            case CISTERN -> canvas.fill(wallX0, y + 1, wallZ0, wallX1, y + 1, wallZ1, PANEL);
            default ->
            {
                // Warren, Stairwell and Writhing walls have no bands.
            }
        }
    }

    private void lining(int x, int y, int z, int x0, int z0, int x1, int z1, int height)
    {
        canvas.fill(x0, y, z0, x1, y, z1, FLOOR);
        canvas.fill(x0, y + height + 1, z0, x1, y + height + 1, z1, CEILING);
    }

    /**
     * The repeating ribs that give corridors their rhythm. They stand on each side of every cell border, so a long corridor reads as a regular series of frames.
     */
    private void paintFrame(int x, int y, int z, int direction, int low, int high, int height)
    {
        if (theme == DaedalusTheme.WARREN || theme == DaedalusTheme.WRITHING)
            return;

        int along = direction == Compass.NORTH || direction == Compass.WEST ? 0 : LAST;
        for (int lateral = low - 1; lateral <= high + 1; lateral += high - low + 2)
        {
            int frameX = Compass.isAlongX(direction) ? x + along : x + lateral;
            int frameZ = Compass.isAlongX(direction) ? z + lateral : z + along;
            canvas.fill(frameX, y + 1, frameZ, frameX, y + height, frameZ, COLUMN);
        }
        if (height < 4)
            return;
        for (int lateral = low; lateral <= high; lateral++)
        {
            int frameX = Compass.isAlongX(direction) ? x + along : x + lateral;
            int frameZ = Compass.isAlongX(direction) ? z + lateral : z + along;
            canvas.set(frameX, y + height, frameZ, TRIM);
        }
    }

    private void paintStairs(int i, int k, int j)
    {
        int x = cellX(i);
        int y = floorY(k);
        int z = cellZ(j);
        int climb = plan.faces(i, k, j);
        int width = layout.corridorWidth();
        int head = Math.max(MIN_STAIR_HEAD, layout.corridorHeight());
        int low = laneLow(width);
        int high = laneHigh(width);
        var steps = stairs(climb);

        for (int run = 0; run < STAIR_RUN; run++)
        {
            int step = y + 1 + run;
            int along = climb == Compass.EAST || climb == Compass.SOUTH ? run : LAST - run;
            for (int lateral = low - 1; lateral <= high + 1; lateral++)
            {
                int blockX = Compass.isAlongX(climb) ? x + along : x + lateral;
                int blockZ = Compass.isAlongX(climb) ? z + lateral : z + along;
                if (lateral < low || lateral > high)
                {
                    canvas.fill(blockX, step - 1, blockZ, blockX, step + head + 1, blockZ, WALL);
                    continue;
                }
                canvas.set(blockX, step, blockZ, run == STAIR_RUN - 1 ? FLOOR : steps);
                canvas.fill(blockX, step + 1, blockZ, blockX, step + head, blockZ, AIR);
                canvas.set(blockX, step + head + 1, blockZ, CEILING);
            }
        }
    }

    private void paintRoomCell(int i, int k, int j)
    {
        var room = plan.room(i, k, j);
        if (room.shape() == RoomShape.CELL)
        {
            int doorWidth = corridorWidth(i, k, j);
            paintPassage(i, k, j, 4, layout.roomHeight() - 1, doorWidth, layout.corridorHeight(), plan.cellHash(i, k, j));
            return;
        }

        int x = cellX(i);
        int y = floorY(k);
        int z = cellZ(j);
        boolean below = plan.room(i, k - 1, j) == room;
        boolean above = plan.room(i, k + 1, j) == room;
        boolean north = plan.room(i, k, j - 1) == room;
        boolean east = plan.room(i + 1, k, j) == room;
        boolean south = plan.room(i, k, j + 1) == room;
        boolean west = plan.room(i - 1, k, j) == room;
        int top = room.minK() == room.maxK() ? layout.roomHeight() : FULL_ROOM_TOP;

        canvas.fill(x, y, z, x + LAST, y + LAST, z + LAST, WALL);
        if (theme == DaedalusTheme.GALLERY)
            canvas.fill(x, y + 1, z, x + LAST, y + 1, z + LAST, TRIM);
        if (!below)
            canvas.fill(x, y, z, x + LAST, y, z + LAST, FLOOR);
        if (!above)
            canvas.fill(x, y + top + 1, z, x + LAST, y + top + 1, z + LAST, CEILING);

        canvas.fill(x + (west ? 0 : 1), y + (below ? 0 : 1), z + (north ? 0 : 1),
                x + (east ? LAST : LAST - 1), y + (above ? LAST : top), z + (south ? LAST : LAST - 1), AIR);

        int width = layout.corridorWidth();
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (plan.isOpen(i, k, j, direction))
                paintDoor(x, y, z, direction, width, layout.corridorHeight());
        }
        boolean checkerboard = ((i - room.minI()) + (j - room.minJ())) % 2 == 0;
        if (!above && checkerboard)
            canvas.set(x + LANE_CENTRE, y + top + 1, z + LANE_CENTRE, LAMP);
    }

    private void paintDoor(int x, int y, int z, int direction, int width, int height)
    {
        int low = laneLow(width);
        int high = laneHigh(width);
        int along = direction == Compass.NORTH || direction == Compass.WEST ? 0 : LAST;
        for (int lateral = low; lateral <= high; lateral++)
        {
            int doorX = Compass.isAlongX(direction) ? x + along : x + lateral;
            int doorZ = Compass.isAlongX(direction) ? z + lateral : z + along;
            canvas.set(doorX, y, doorZ, FLOOR);
            canvas.fill(doorX, y + 1, doorZ, doorX, y + height, doorZ, AIR);
        }
        paintFrame(x, y, z, direction, low, high, height);
    }

    private void decorate(Room room)
    {
        switch (room.shape())
        {
            case STACKS -> decorateStacks(room);
            case COLONNADE ->
            {
                paintColumns(room, floorY(room.minK()) + 1, floorY(room.maxK()) + COLUMN_LAMP_HEIGHT);
                if (room.maxK() > room.minK())
                    paintRings(room);
            }
            case FLOOD -> decorateFlood(room);
            case ATRIUM -> paintRings(room);
            case CHAMBER -> decorateChamber(room);
            default ->
            {
                // Plain rooms and cells are only their walls.
            }
        }
    }

    private int interiorMinX(Room room)
    {
        return cellX(room.minI()) + 1;
    }

    private int interiorMaxX(Room room)
    {
        return cellX(room.maxI()) + LAST - 1;
    }

    private int interiorMinZ(Room room)
    {
        return cellZ(room.minJ()) + 1;
    }

    private int interiorMaxZ(Room room)
    {
        return cellZ(room.maxJ()) + LAST - 1;
    }

    private int roomTop(Room room)
    {
        int top = room.minK() == room.maxK() ? layout.roomHeight() : FULL_ROOM_TOP;
        return floorY(room.maxK()) + top;
    }

    /**
     * Rows of shelves running the room's length. They stop short of its ends, so the aisles all meet in a clear lane at each end where doors arrive, and stay out of the lanes at its sides for the same reason.
     */
    private void decorateStacks(Room room)
    {
        boolean alongX = (room.variant() & 1) != 0;
        int bottom = floorY(room.minK()) + 1;
        int top = roomTop(room);
        int crossCells = alongX ? room.cellsDeep() : room.cellsWide();
        int crossOrigin = alongX ? cellZ(room.minJ()) : cellX(room.minI());
        int alongStart = (alongX ? interiorMinX(room) : interiorMinZ(room)) + RING_WIDTH;
        int alongEnd = (alongX ? interiorMaxX(room) : interiorMaxZ(room)) - RING_WIDTH;

        for (int crossCell = 0; crossCell < crossCells; crossCell++)
        {
            if (crossCell > 0)
                paintShelfRow(alongX, crossOrigin + crossCell * CELL + 1, alongStart, alongEnd, bottom, top);
            if (crossCell < crossCells - 1)
                paintShelfRow(alongX, crossOrigin + crossCell * CELL + 5, alongStart, alongEnd, bottom, top);
        }
    }

    private void paintShelfRow(boolean alongX, int cross, int alongStart, int alongEnd, int bottom, int top)
    {
        for (int along = alongStart; along <= alongEnd; along++)
        {
            int relative = Math.floorMod(along, CELL * 3);
            if (relative >= 2 && relative <= 4)
                continue;
            int x = alongX ? along : cross;
            int z = alongX ? cross : along;
            canvas.fill(x, bottom, z, x, top - 1, z, relative % SHELF_UPRIGHT_SPACING == 0 ? COLUMN : SHELF);
            canvas.set(x, top, z, TRIM);
        }
    }

    /**
     * Square columns on the lattice corners, which is never where a door lane or walkway runs.
     */
    private void paintColumns(Room room, int bottom, int lampY)
    {
        int top = roomTop(room);
        for (int i = room.minI() + 1; i <= room.maxI(); i++)
        {
            for (int j = room.minJ() + 1; j <= room.maxJ(); j++)
            {
                int x = cellX(i);
                int z = cellZ(j);
                canvas.fill(x - 1, bottom, z - 1, x, top, z, COLUMN);
                canvas.fill(x - 1, bottom, z - 1, x, bottom, z, TRIM);
                canvas.fill(x - 1, lampY, z - 1, x, lampY, z, LAMP);
            }
        }
    }

    /**
     * A walkway round the walls at every upper tier, with the flight that climbs to it. A flight keeps to the inner two rows, so its wall's row stays free for doors on both floors.
     */
    private void paintRings(Room room)
    {
        for (int k = room.minK() + 1; k <= room.maxK(); k++)
            paintRing(room, floorY(k));
        for (int k = room.minK(); k < room.maxK(); k++)
            paintRingFlight(room, k);
    }

    private void paintRing(Room room, int y)
    {
        int minX = interiorMinX(room);
        int maxX = interiorMaxX(room);
        int minZ = interiorMinZ(room);
        int maxZ = interiorMaxZ(room);
        for (int z = Math.max(minZ, canvas.minZ()); z <= Math.min(maxZ, canvas.maxZ()); z++)
        {
            for (int x = Math.max(minX, canvas.minX()); x <= Math.min(maxX, canvas.maxX()); x++)
            {
                int fromX = Math.min(x - minX, maxX - x);
                int fromZ = Math.min(z - minZ, maxZ - z);
                if (Math.min(fromX, fromZ) >= RING_WIDTH)
                    continue;

                boolean corner = fromX == RING_CORNER_LAMP && fromZ == RING_CORNER_LAMP;
                canvas.set(x, y, z, corner ? LAMP : FLOOR);
                if (Math.min(fromX, fromZ) != RING_WIDTH - 1)
                    continue;
                var rail = fromX == fromZ ? RAIL_POST : fromX > fromZ ? RAIL_X : RAIL_Z;
                canvas.set(x, y + 1, z, rail);
            }
        }
    }

    private void paintRingFlight(Room room, int k)
    {
        int side = (room.variant() + k) & 3;
        boolean alongX = side == Compass.NORTH || side == Compass.SOUTH;
        int alongMin = alongX ? interiorMinX(room) : interiorMinZ(room);
        int alongMax = alongX ? interiorMaxX(room) : interiorMaxZ(room);
        int crossWall = switch (side)
        {
            case Compass.NORTH -> interiorMinZ(room);
            case Compass.SOUTH -> interiorMaxZ(room);
            case Compass.WEST -> interiorMinX(room);
            default -> interiorMaxX(room);
        };
        int inward = side == Compass.NORTH || side == Compass.WEST ? 1 : -1;
        int slack = alongMax - alongMin + 1 - STAIR_RUN - RING_WIDTH * 2;
        int start = alongMin + RING_WIDTH + (slack > 0 ? DaedalusHash.pick(room.variant() >>> RANDOM_BITS, slack + 1) : 0);
        int climb = alongX ? Compass.EAST : Compass.SOUTH;
        int floor = floorY(k);

        for (int run = 0; run < STAIR_RUN; run++)
        {
            int along = start + run;
            int step = floor + 1 + run;
            for (int row = 1; row <= 2; row++)
            {
                int cross = crossWall + inward * row;
                int x = alongX ? along : cross;
                int z = alongX ? cross : along;
                canvas.fill(x, floor + 1, z, x, step - 1, z, WALL);
                canvas.set(x, step, z, run == STAIR_RUN - 1 ? FLOOR : stairs(climb));
                canvas.fill(x, step + 1, z, x, step + MIN_STAIR_HEAD, z, AIR);
            }
        }
    }

    /**
     * Water fills the lower tier up to the walkways' own height, so it is held in by the walkways and walls without needing a rim.
     */
    private void decorateFlood(Room room)
    {
        int bottom = floorY(room.minK()) + 1;
        int surface = floorY(room.maxK());
        int minX = interiorMinX(room);
        int maxX = interiorMaxX(room);
        int minZ = interiorMinZ(room);
        int maxZ = interiorMaxZ(room);
        canvas.fill(minX, bottom, minZ, maxX, surface, maxZ, WATER);

        boolean causewayAlongX = room.cellsWide() >= room.cellsDeep();
        int causewayCell = causewayAlongX ? room.minJ() + room.cellsDeep() / 2 : room.minI() + room.cellsWide() / 2;
        int causewayLow = (causewayAlongX ? cellZ(causewayCell) : cellX(causewayCell)) + laneLow(3);
        int causewayHigh = (causewayAlongX ? cellZ(causewayCell) : cellX(causewayCell)) + laneHigh(3);
        for (int z = Math.max(minZ, canvas.minZ()); z <= Math.min(maxZ, canvas.maxZ()); z++)
        {
            for (int x = Math.max(minX, canvas.minX()); x <= Math.min(maxX, canvas.maxX()); x++)
            {
                int cross = causewayAlongX ? z : x;
                boolean onCauseway = cross >= causewayLow && cross <= causewayHigh;
                boolean onWalkway = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z)) < POOL_WALKWAY;
                if (onCauseway || onWalkway)
                    canvas.set(x, surface, z, FLOOR);
            }
        }
        paintColumns(room, bottom, surface + COLUMN_LAMP_HEIGHT);
    }

    /**
     * Rock swelling in from the walls, except on a side with a door, so that no chamber is ever a clean box.
     */
    private void decorateChamber(Room room)
    {
        int y = floorY(room.minK());
        int top = y + layout.roomHeight();
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                for (int direction = 0; direction < Compass.COUNT; direction++)
                {
                    if (plan.room(i + Compass.dx(direction), room.minK(), j + Compass.dz(direction)) != room && !plan.isOpen(i, room.minK(), j, direction))
                        swellWall(i, j, direction, y, top);
                }
            }
        }
    }

    private void swellWall(int i, int j, int direction, int y, int top)
    {
        int x = cellX(i);
        int z = cellZ(j);
        for (int lateral = 1; lateral < LAST; lateral++)
        {
            int depth = DaedalusHash.pick(DaedalusHash.mix(plan.worldSeed, x * 31L + z, lateral, direction), 3);
            for (int inset = 1; inset <= depth; inset++)
            {
                int along = direction == Compass.NORTH || direction == Compass.WEST ? inset : LAST - inset;
                int blockX = Compass.isAlongX(direction) ? x + along : x + lateral;
                int blockZ = Compass.isAlongX(direction) ? z + lateral : z + along;
                canvas.fill(blockX, y + 1, blockZ, blockX, top, blockZ, WALL);
            }
        }
    }

    /**
     * The district seen from the chasm: pilasters on the lattice lines, which door lanes never cross, and a band at every tier.
     */
    private void paintFacade()
    {
        for (int along = 0; along < DistrictPos.SIZE; along++)
        {
            facadeColumn(originX + along, originZ, along, 0, 1);
            facadeColumn(originX + along, originZ + DistrictPos.SIZE - 1, along, 0, -1);
            facadeColumn(originX, originZ + along, along, 1, 0);
            facadeColumn(originX + DistrictPos.SIZE - 1, originZ + along, along, -1, 0);
        }
    }

    /**
     * @param inwardX inwardZ the way into the district, where a passage's wall keeps its own material on its inner face
     */
    private void facadeColumn(int x, int z, int along, int inwardX, int inwardZ)
    {
        if (x < canvas.minX() || x > canvas.maxX() || z < canvas.minZ() || z > canvas.maxZ())
            return;

        int relative = along % PILASTER_SPACING;
        boolean pilaster = relative == PILASTER_SPACING - 1 || relative == 0;
        for (int y = 1; y < DistrictPos.TOP_Y; y++)
        {
            if (canvas.get(x, y, z) == AIR || canvas.get(x + inwardX, y, z + inwardZ) == AIR)
                continue;
            boolean band = (y - 1) % CELL == 0;
            canvas.set(x, y, z, band ? FACADE_TRIM : pilaster ? COLUMN : FACADE);
        }
    }

    private void paintLandingFrame(DistrictPlan.Landing landing)
    {
        int x = cellX(landing.i());
        int y = floorY(landing.k());
        int z = cellZ(landing.j());
        int width = corridorWidth(landing.i(), landing.k(), landing.j());
        paintFrame(x, y, z, landing.direction(), laneLow(width), laneHigh(width), layout.corridorHeight());
    }
}
