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
    private static final long PIPE_SALT_X = 0x91BE5L;
    private static final long PIPE_SALT_Z = 0x91BE6L;
    private static final int MIN_STAIR_HEAD = 3;
    private static final int PILASTER_SPACING = 16;
    /** Wide enough for a flight on the two rows inside the wall's row, with the rail beyond it. */
    static final int RING_WIDTH = 4;
    /** How far a flight keeps from the ends of its wall, clear of the flights on the walls beside it. */
    private static final int FLIGHT_MARGIN = 3;
    private static final int STACK_END_LANE = 3;
    private static final int RING_CORNER_LAMP = 1;
    private static final int COLUMN_LAMP_HEIGHT = 4;
    private static final int SHELF_UPRIGHT_SPACING = 4;
    private static final int POOL_WALKWAY = 2;
    private static final int RANDOM_BITS = 8;
    private static final int WINDOW_LOW = 3;
    private static final int WINDOW_HIGH = 4;
    private static final int WINDOW_BOTTOM = 2;
    private static final int WINDOW_TOP = 3;
    private static final int WALL_LAMP_HEIGHT = 4;
    private static final int BALCONY_DEPTH = 3;

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
        var furnisher = new RoomFurnisher(plan, canvas);
        for (var room : plan.rooms())
        {
            if (canvas.overlaps(cellX(room.minI()), cellZ(room.minJ()), cellX(room.maxI()) + LAST, cellZ(room.maxJ()) + LAST))
            {
                furnisher.detail(room);
                if (room.shape() == RoomShape.VAULT)
                {
                    for (int j = room.minJ(); j <= room.maxJ(); j++)
                    {
                        for (int i = room.minI(); i <= room.maxI(); i++)
                            paintVaultColumns(i, room.minK(), j, floorY(room.minK()));
                    }
                }
                decorate(room);
                furnisher.furnish(room);
            }
        }
        for (var opening : plan.voids())
        {
            if (canvas.overlaps(cellX(opening.minI()), cellZ(opening.minJ()), cellX(opening.maxI()) + LAST, cellZ(opening.maxJ()) + LAST))
                furnisher.hangInVoid(opening);
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
            case DistrictPlan.VOID, DistrictPlan.FLOODED -> paintVoid(i, k, j);
            case DistrictPlan.CATWALK ->
            {
                paintVoid(i, k, j);
                paintCatwalk(i, k, j);
            }
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
        paintCorridorPipe(i, k, j);
    }

    /**
     * A pipe of the old machines under the ceiling of a straight corridor, along one wall. A whole row of straight corridors shares it, so it runs on unbroken and turns into the wall where the row ends.
     */
    private void paintCorridorPipe(int i, int k, int j)
    {
        boolean alongX = isStraight(i, k, j, true);
        if (!alongX && !isStraight(i, k, j, false))
            return;
        long line = DaedalusHash.mix(plan.worldSeed, plan.pos.x() * 31L + plan.pos.z(), (alongX ? j : i) * DistrictPos.TIERS + k, alongX ? PIPE_SALT_X : PIPE_SALT_Z);
        if (DaedalusHash.pick(line, pipeOdds()) != 0)
            return;
        int width = corridorWidth(i, k, j);
        boolean low = (line >>> RANDOM_BITS & 1) == 0;
        int lateral = low ? laneLow(width) : laneHigh(width);
        int wallLateral = low ? lateral - 1 : lateral + 1;
        int x = cellX(i);
        int z = cellZ(j);
        int y = floorY(k) + layout.corridorHeight();
        var pipe = alongX ? PIPE_X : PIPE_Z;
        for (int along = 0; along < CELL; along++)
        {
            int blockX = alongX ? x + along : x + lateral;
            int blockZ = alongX ? z + lateral : z + along;
            var current = canvas.get(blockX, y, blockZ);
            if (current == AIR || current == TRIM)
                canvas.set(blockX, y, blockZ, pipe);
        }
        for (int end = 0; end < 2; end++)
        {
            int direction = alongX ? (end == 0 ? Compass.WEST : Compass.EAST) : (end == 0 ? Compass.NORTH : Compass.SOUTH);
            int nextI = i + Compass.dx(direction);
            int nextJ = j + Compass.dz(direction);
            if (DistrictPlan.isInside(nextI, k, nextJ) && isStraight(nextI, k, nextJ, alongX) && corridorWidth(nextI, k, nextJ) == width)
                continue;
            int along = end == 0 ? 0 : LAST;
            int wallX = alongX ? x + along : x + wallLateral;
            int wallZ = alongX ? z + wallLateral : z + along;
            var wall = canvas.get(wallX, y, wallZ);
            if (wall == WALL || wall == COLUMN || wall == TRIM || wall == PANEL)
                canvas.set(wallX, y, wallZ, VENT);
        }
    }

    private boolean isStraight(int i, int k, int j, boolean alongX)
    {
        if (plan.kind(i, k, j) != DistrictPlan.CORRIDOR)
            return false;
        int faces = plan.faces(i, k, j);
        return alongX ? faces == (1 << Compass.EAST | 1 << Compass.WEST) : faces == (1 << Compass.NORTH | 1 << Compass.SOUTH);
    }

    private int pipeOdds()
    {
        return switch (theme)
        {
            case CISTERN -> 2;
            case STAIRWELL, WARREN -> 3;
            case ARCHIVE, WRITHING -> 4;
            case GALLERY -> 6;
        };
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
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (plan.hasWindow(i, k, j, direction))
                paintWindow(x, y, z, direction, direction == Compass.NORTH || direction == Compass.WEST ? hubLow - 1 : LAST - hubHigh - 1);
        }
    }

    /**
     * An opening from a passage's side out to the edge of its cell, at eye height and too high to walk through.
     *
     * @param depth how many blocks in from the cell's edge to cut
     */
    private void paintWindow(int x, int y, int z, int direction, int depth)
    {
        for (int inset = 0; inset <= depth; inset++)
        {
            int along = direction == Compass.NORTH || direction == Compass.WEST ? inset : LAST - inset;
            for (int lateral = WINDOW_LOW; lateral <= WINDOW_HIGH; lateral++)
            {
                int windowX = Compass.isAlongX(direction) ? x + along : x + lateral;
                int windowZ = Compass.isAlongX(direction) ? z + lateral : z + along;
                canvas.fill(windowX, y + WINDOW_BOTTOM, windowZ, windowX, y + WINDOW_TOP, windowZ, OPENING);
            }
        }
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
                canvas.set(blockX, step, blockZ, steps);
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
        boolean above = plan.room(i, k + 1, j) == room || (room.shape() == RoomShape.VAULT && plan.isOpenVolume(i, k + 1, j));
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
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (plan.hasWindow(i, k, j, direction))
                paintWindow(x, y, z, direction, 0);
        }
    }

    /**
     * A cell of a pit or vault: open from wall to wall, with walls only where it meets something else. The walls carry a band at every tier and lamps, so the depth can be read through the fog.
     * Flooded cells only open onto other flooded cells, so their water is always walled in.
     */
    private void paintVoid(int i, int k, int j)
    {
        int x = cellX(i);
        int y = floorY(k);
        int z = cellZ(j);
        boolean flooded = plan.kind(i, k, j) == DistrictPlan.FLOODED;
        var below = plan.room(i, k - 1, j);
        boolean openBelow = flooded
                            ? plan.kind(i, k - 1, j) == DistrictPlan.FLOODED
                            : plan.isOpenVolume(i, k - 1, j) || (below != null && below.shape() == RoomShape.VAULT);
        boolean openAbove = plan.isOpenVolume(i, k + 1, j);
        boolean[] openSide = new boolean[Compass.COUNT];
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            int ni = i + Compass.dx(direction);
            int nj = j + Compass.dz(direction);
            openSide[direction] = flooded ? plan.kind(ni, k, nj) == DistrictPlan.FLOODED : plan.isOpenVolume(ni, k, nj);
        }

        canvas.fill(x, y, z, x + LAST, y + LAST, z + LAST, WALL);
        canvas.fill(x, y, z, x + LAST, y, z + LAST, TRIM);
        int x0 = x + (openSide[Compass.WEST] ? 0 : 1);
        int x1 = x + (openSide[Compass.EAST] ? LAST : LAST - 1);
        int z0 = z + (openSide[Compass.NORTH] ? 0 : 1);
        int z1 = z + (openSide[Compass.SOUTH] ? LAST : LAST - 1);
        canvas.fill(x0, y + (openBelow ? 0 : 1), z0, x1, y + (openAbove ? LAST : LAST - 1), z1, flooded ? WATER : AIR);
        if (!openBelow)
        {
            canvas.fill(x0, y, z0, x1, y, z1, FLOOR);
            canvas.set(x + LANE_CENTRE, y, z + LANE_CENTRE, LAMP);
        }
        if (!openAbove)
        {
            canvas.fill(x0, y + LAST, z0, x1, y + LAST, z1, CEILING);
            canvas.set(x + LANE_CENTRE, y + LAST, z + LANE_CENTRE, LAMP);
        }

        long hash = plan.cellHash(i, k, j);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (openSide[direction])
                continue;
            int along = direction == Compass.NORTH || direction == Compass.WEST ? 0 : LAST;
            for (int height = 1; height <= LAST; height++)
            {
                setInWall(x, z, direction, along, 0, y + height, COLUMN);
                setInWall(x, z, direction, along, LAST, y + height, COLUMN);
            }
        }
        paintVaultColumns(i, k, j, y);
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (openSide[direction])
                continue;
            int along = direction == Compass.NORTH || direction == Compass.WEST ? 0 : LAST;
            if (((hash >>> direction) & 1) == 0)
                setInWall(x, z, direction, along, LANE_CENTRE, y + WALL_LAMP_HEIGHT, LAMP);
            if (plan.hasWindow(i, k, j, direction))
                paintWindow(x, y, z, direction, 0);
            if (plan.isOpen(i, k, j, direction) && plan.kind(i, k, j) == DistrictPlan.VOID)
                paintBalcony(x, y, z, direction);
        }
    }

    /**
     * Columns rising through a vault from its floor to its top, on every other lattice corner inside it. Each cell draws its own quarter of the columns at its corners.
     */
    void paintVaultColumns(int i, int k, int j, int bottom)
    {
        for (var opening : plan.voids())
        {
            if (opening.shape() != RoomShape.VAULT || i < opening.minI() || i > opening.maxI() || j < opening.minJ() || j > opening.maxJ() || k < opening.minK() || k > opening.maxK())
                continue;
            for (int cornerI = 0; cornerI <= 1; cornerI++)
            {
                for (int cornerJ = 0; cornerJ <= 1; cornerJ++)
                {
                    int junctionI = i + cornerI;
                    int junctionJ = j + cornerJ;
                    boolean inside = junctionI > opening.minI() && junctionI <= opening.maxI() && junctionJ > opening.minJ() && junctionJ <= opening.maxJ();
                    boolean spaced = (junctionI - opening.minI()) % 2 == 0 && (junctionJ - opening.minJ()) % 2 == 0;
                    if (!inside || !spaced)
                        continue;
                    int x = cellX(i) + cornerI * LAST;
                    int z = cellZ(j) + cornerJ * LAST;
                    int from = k == opening.minK() ? bottom + 1 : bottom;
                    canvas.fill(x, from, z, x, bottom + LAST, z, COLUMN);
                    if (k == opening.minK())
                        canvas.set(x, from, z, TRIM);
                }
            }
            return;
        }
    }

    private void setInWall(int x, int z, int direction, int along, int lateral, int y, DaedalusMaterial material)
    {
        int blockX = Compass.isAlongX(direction) ? x + along : x + lateral;
        int blockZ = Compass.isAlongX(direction) ? z + lateral : z + along;
        canvas.set(blockX, y, blockZ, material);
    }

    /**
     * A ledge jutting from a doorway into the void, railed on its open sides, where a corridor stops to look out.
     */
    private void paintBalcony(int x, int y, int z, int direction)
    {
        int low = laneLow(layout.corridorWidth()) - 1;
        int high = laneHigh(layout.corridorWidth()) + 1;
        for (int inset = 0; inset < BALCONY_DEPTH; inset++)
        {
            int along = direction == Compass.NORTH || direction == Compass.WEST ? inset : LAST - inset;
            for (int lateral = low; lateral <= high; lateral++)
            {
                setInWall(x, z, direction, along, lateral, y, FLOOR);
                boolean edge = lateral == low || lateral == high || inset == BALCONY_DEPTH - 1;
                boolean inDoorway = inset == 0 && lateral > low && lateral < high;
                var railing = lateral == low || lateral == high
                              ? (inset == BALCONY_DEPTH - 1 ? RAIL_POST : Compass.isAlongX(direction) ? RAIL_X : RAIL_Z)
                              : Compass.isAlongX(direction) ? RAIL_Z : RAIL_X;
                if (inDoorway)
                {
                    for (int height = 1; height <= layout.corridorHeight(); height++)
                        setInWall(x, z, direction, along, lateral, y + height, AIR);
                }
                else if (edge && inset > 0)
                {
                    setInWall(x, z, direction, along, lateral, y + 1, railing);
                }
            }
        }
    }

    /**
     * A railed walkway through the void along the cell's open sides, with the doorway carved wherever it meets a wall.
     */
    private void paintCatwalk(int i, int k, int j)
    {
        int x = cellX(i);
        int y = floorY(k);
        int z = cellZ(j);
        int low = laneLow(layout.corridorWidth());
        int high = laneHigh(layout.corridorWidth());
        boolean[][] walk = new boolean[CELL][CELL];
        for (int a = low; a <= high; a++)
        {
            for (int b = low; b <= high; b++)
                walk[a][b] = true;
        }
        for (int direction = 0; direction < Compass.COUNT; direction++)
        {
            if (!plan.isOpen(i, k, j, direction))
                continue;
            for (int along = 0; along < CELL; along++)
            {
                boolean towards = direction == Compass.NORTH || direction == Compass.WEST ? along < low : along > high;
                if (!towards)
                    continue;
                for (int lateral = low; lateral <= high; lateral++)
                {
                    if (Compass.isAlongX(direction))
                        walk[along][lateral] = true;
                    else
                        walk[lateral][along] = true;
                }
            }
        }

        for (int localZ = 0; localZ < CELL; localZ++)
        {
            for (int localX = 0; localX < CELL; localX++)
            {
                if (walk[localX][localZ])
                {
                    canvas.set(x + localX, y, z + localZ, FLOOR);
                    for (int height = 1; height <= layout.corridorHeight(); height++)
                        canvas.set(x + localX, y + height, z + localZ, AIR);
                    continue;
                }
                boolean besideX = (localX > 0 && walk[localX - 1][localZ]) || (localX < LAST && walk[localX + 1][localZ]);
                boolean besideZ = (localZ > 0 && walk[localX][localZ - 1]) || (localZ < LAST && walk[localX][localZ + 1]);
                boolean diagonal = !besideX && !besideZ && isDiagonalToWalk(walk, localX, localZ);
                if (!besideX && !besideZ && !diagonal)
                    continue;
                if (canvas.get(x + localX, y + 1, z + localZ) != AIR)
                    continue;
                canvas.set(x + localX, y, z + localZ, TRIM);
                canvas.set(x + localX, y + 1, z + localZ, besideX && besideZ || diagonal ? RAIL_POST : besideX ? RAIL_Z : RAIL_X);
            }
        }
    }

    private static boolean isDiagonalToWalk(boolean[][] walk, int localX, int localZ)
    {
        for (int dx = -1; dx <= 1; dx += 2)
        {
            for (int dz = -1; dz <= 1; dz += 2)
            {
                int nx = localX + dx;
                int nz = localZ + dz;
                if (nx >= 0 && nz >= 0 && nx < CELL && nz < CELL && walk[nx][nz])
                    return true;
            }
        }
        return false;
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
        int alongStart = (alongX ? interiorMinX(room) : interiorMinZ(room)) + STACK_END_LANE;
        int alongEnd = (alongX ? interiorMaxX(room) : interiorMaxZ(room)) - STACK_END_LANE;

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
     * A walkway round the walls at every upper tier, with the flight that climbs to it. A flight keeps to the two rows inside the wall's row, so that row stays free for doors on both floors and the rail beyond the flight still guards it.
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
        int slack = alongMax - alongMin + 1 - STAIR_RUN - FLIGHT_MARGIN * 2;
        int start = alongMin + FLIGHT_MARGIN + (slack > 0 ? DaedalusHash.pick(room.variant() >>> RANDOM_BITS, slack + 1) : 0);
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
                canvas.set(x, step, z, stairs(climb));
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
            if (canvas.get(x, y, z).isPassable() || canvas.get(x + inwardX, y, z + inwardZ).isPassable())
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
