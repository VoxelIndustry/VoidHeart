package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp.*;
import static net.voxelindustry.voidheart.common.world.daedalus.DistrictPos.CELL;
import static net.voxelindustry.voidheart.common.world.daedalus.DistrictPos.floorY;

/**
 * Furnishes rooms after their walls are drawn: furniture along the walls and in the corners, a centrepiece in large rooms and chandeliers on chains wherever there is height for them.
 * Nothing is ever placed in front of a door, and every check reads only the block's own column, so chunks agree at their borders.
 */
final class RoomFurnisher
{
    private static final int LAST = CELL - 1;
    private static final int LANE_CENTRE = 3;
    private static final int WIDEST_DOOR = 5;
    private static final int DOOR_CLEARANCE = 2;
    private static final int LARGE_ROOM = 10;
    private static final int FULL_ROOM_TOP = 6;
    private static final int HEADROOM_UNDER_CHANDELIER = 6;
    private static final int MIN_DEEP_HANG = 8;
    private static final int TALL_HANG = 3;

    private final DistrictPlan plan;
    private final DaedalusCanvas canvas;
    private final DaedalusTheme theme;
    private final DaedalusTheme.Layout layout;
    private final int originX;
    private final int originZ;
    private static final int PILASTER_SPACING = 4;
    private static final int NICHE_SPACING = 8;
    private static final int NICHE_HEIGHT = 3;
    private static final int WAINSCOT_HEIGHT = 2;
    private static final int MEDALLION_DIVISOR = 4;
    private static final int MIN_MEDALLION = 2;
    private static final int RUNNER_HALF_WIDTH = 1;
    private static final int COLONNADE_CELLS = 3;

    private final List<int[]> doorZones = new ArrayList<>();

    RoomFurnisher(DistrictPlan plan, DaedalusCanvas canvas)
    {
        this.plan = plan;
        this.canvas = canvas;
        theme = plan.theme;
        layout = theme.layout();
        originX = plan.pos.originX();
        originZ = plan.pos.originZ();
    }

    private int cellX(int i)
    {
        return originX + i * CELL;
    }

    private int cellZ(int j)
    {
        return originZ + j * CELL;
    }

    private long hash(Room room, int salt)
    {
        return DaedalusHash.mix(room.variant(), room.minI() * 31L + room.minJ(), room.minK(), salt);
    }

    /**
     * The room's architecture, drawn before its furniture: floor patterns everywhere a floor shows, and for rooms of a single floor wall motifs and a coffered ceiling.
     */
    void detail(Room room)
    {
        switch (room.shape())
        {
            case PLAIN ->
            {
                detailFloor(room, room.minK(), true);
                detailWalls(room, roomTopAir(room));
                if (room.cellsWide() * room.cellsDeep() > 1)
                    detailCeiling(room);
                if (room.cellsWide() >= COLONNADE_CELLS && room.cellsDeep() >= COLONNADE_CELLS)
                    raiseInnerColonnade(room);
            }
            case VAULT ->
            {
                detailFloor(room, room.minK(), true);
                detailWalls(room, floorY(room.minK()) + LAST);
            }
            case COLONNADE, ATRIUM -> detailFloor(room, room.minK(), true);
            case STACKS -> detailFloor(room, room.minK(), false);
            case CHAMBER -> mottleFloor(room);
            default ->
            {
                // Cells, flooded halls and the rest keep their walls plain.
            }
        }
    }

    private boolean hasCarpets()
    {
        return theme == DaedalusTheme.ARCHIVE || theme == DaedalusTheme.GALLERY || theme == DaedalusTheme.STAIRWELL;
    }

    /**
     * A trim border, then by size and luck an inlaid diamond in the middle, a checkered field, seams on the lattice lines and a carpet running the room's length.
     */
    private void detailFloor(Room room, int tier, boolean carpets)
    {
        int floor = floorY(tier);
        int x0 = minX(room);
        int x1 = maxX(room);
        int z0 = minZ(room);
        int z1 = maxZ(room);
        int cx = centreX(room);
        int cz = centreZ(room);
        long bits = hash(room, 8);
        boolean large = isLarge(room);
        int radius = Math.min(x1 - x0 + 1, z1 - z0 + 1) / MEDALLION_DIVISOR;
        boolean medallion = radius >= MIN_MEDALLION;
        boolean checker = (bits & 1) != 0 && room.cellsWide() * room.cellsDeep() > 1;
        boolean runner = carpets && large && hasCarpets() && (bits & 2) != 0;
        boolean runnerAlongX = x1 - x0 >= z1 - z0;
        for (int z = Math.max(z0, canvas.minZ()); z <= Math.min(z1, canvas.maxZ()); z++)
        {
            for (int x = Math.max(x0, canvas.minX()); x <= Math.min(x1, canvas.maxX()); x++)
            {
                if (canvas.get(x, floor, z) != DaedalusMaterial.FLOOR)
                    continue;
                int edge = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
                int diamond = Math.abs(x - cx) + Math.abs(z - cz);
                int localX = Math.floorMod(x - originX, CELL);
                int localZ = Math.floorMod(z - originZ, CELL);
                boolean seam = large && (localX == LAST || localX == 0 || localZ == LAST || localZ == 0);
                DaedalusMaterial pattern = null;
                if (edge == 0)
                    pattern = DaedalusMaterial.FLOOR_TRIM;
                else if (medallion && diamond <= radius)
                    pattern = diamond % 2 == 0 ? DaedalusMaterial.FLOOR_INLAY : DaedalusMaterial.FLOOR_TRIM;
                else if (checker && edge >= 2)
                    pattern = (((x >> 1) + (z >> 1)) & 1) == 0 ? DaedalusMaterial.FLOOR_INLAY : null;
                else if (seam)
                    pattern = DaedalusMaterial.FLOOR_TRIM;
                if (medallion && diamond == radius && (x == cx || z == cz))
                    pattern = DaedalusMaterial.LAMP;
                if (pattern != null)
                    canvas.set(x, floor, z, pattern);

                int lateral = runnerAlongX ? z - cz : x - cx;
                if (runner && edge >= 1 && Math.abs(lateral) <= RUNNER_HALF_WIDTH && (!medallion || diamond > radius + 1)
                    && canvas.raw(x, floor + 1, z) == DaedalusMaterial.AIR.ordinal())
                    canvas.set(x, floor + 1, z, DaedalusMaterial.CARPET);
            }
        }
    }

    private void mottleFloor(Room room)
    {
        int floor = floorY(room.minK());
        for (int z = Math.max(minZ(room), canvas.minZ()); z <= Math.min(maxZ(room), canvas.maxZ()); z++)
        {
            for (int x = Math.max(minX(room), canvas.minX()); x <= Math.min(maxX(room), canvas.maxX()); x++)
            {
                if (canvas.get(x, floor, z) == DaedalusMaterial.FLOOR && DaedalusHash.pick(DaedalusHash.mix(x * 31L + z + floor * 961L), 4) == 0)
                    canvas.set(x, floor, z, DaedalusMaterial.FLOOR_INLAY);
            }
        }
    }

    /**
     * Pilasters standing out of the walls, a wainscot capped with trim, a moulded cornice under the ceiling and arched niches where solid rock lies behind the wall.
     *
     * @param top the highest air of the room against its walls
     */
    private void detailWalls(Room room, int top)
    {
        int floor = floorY(room.minK());
        int y = floor + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        long bits = hash(room, 9);
        var nicheProp = theme == DaedalusTheme.ARCHIVE || theme == DaedalusTheme.STAIRWELL ? CANDELABRUM : URN;
        int x0 = minX(room);
        int x1 = maxX(room);
        int z0 = minZ(room);
        int z1 = maxZ(room);
        for (int side = 0; side < Compass.COUNT; side++)
        {
            boolean alongX = side == Compass.NORTH || side == Compass.SOUTH;
            int from = alongX ? x0 : z0;
            int to = alongX ? x1 : z1;
            int inside = switch (side)
            {
                case Compass.NORTH -> z0;
                case Compass.SOUTH -> z1;
                case Compass.WEST -> x0;
                default -> x1;
            };
            int wall = inside + (side == Compass.NORTH || side == Compass.WEST ? -1 : 1);
            for (int along = from; along <= to; along++)
            {
                int ix = alongX ? along : inside;
                int iz = alongX ? inside : along;
                int wx = alongX ? along : wall;
                int wz = alongX ? wall : along;
                if (wx < canvas.minX() || wx > canvas.maxX() || wz < canvas.minZ() || wz > canvas.maxZ())
                    continue;
                boolean clear = isClear(ix, iz);
                int step = along - from;

                for (int height = 0; height <= WAINSCOT_HEIGHT; height++)
                {
                    var current = canvas.get(wx, y + height, wz);
                    if (current == DaedalusMaterial.WALL || current == DaedalusMaterial.TRIM || current == DaedalusMaterial.PANEL)
                        canvas.set(wx, y + height, wz, height == WAINSCOT_HEIGHT ? DaedalusMaterial.TRIM : DaedalusMaterial.PANEL);
                }

                boolean corner = along == from || along == to;
                if (!clear || corner)
                    continue;
                boolean pilaster = Math.floorMod(step - PILASTER_SPACING / 2 + (int) (bits & 1), PILASTER_SPACING) == 0;
                if (pilaster)
                {
                    for (int height = y; height <= top; height++)
                    {
                        if (canvas.raw(ix, height, iz) == DaedalusMaterial.AIR.ordinal())
                            canvas.set(ix, height, iz, DaedalusMaterial.COLUMN);
                    }
                    continue;
                }
                if (canvas.raw(ix, top, iz) == DaedalusMaterial.AIR.ordinal())
                    canvas.set(ix, top, iz, DaedalusMaterial.arch(side));
                boolean niche = Math.floorMod(step + (int) (bits >>> 1 & 7), NICHE_SPACING) == 0 && isRockBehind(wx, room.minK(), wz, side);
                if (niche && canvas.get(wx, y, wz) == DaedalusMaterial.PANEL)
                {
                    canvas.fill(wx, y, wz, wx, y + NICHE_HEIGHT - 1, wz, DaedalusMaterial.AIR);
                    canvas.set(wx, y + NICHE_HEIGHT, wz, DaedalusMaterial.arch(Compass.opposite(side)));
                    canvas.setProp(wx, y, wz, nicheProp, Compass.opposite(side));
                }
            }
        }
    }

    /**
     * @return whether the cell beyond this wall block is solid rock, so a niche cut into the wall stays a niche
     */
    private boolean isRockBehind(int wallX, int tier, int wallZ, int side)
    {
        int i = Math.floorDiv(wallX + Compass.dx(side) - originX, CELL);
        int j = Math.floorDiv(wallZ + Compass.dz(side) - originZ, CELL);
        int ownI = Math.floorDiv(wallX - originX, CELL);
        int ownJ = Math.floorDiv(wallZ - originZ, CELL);
        if (i == ownI && j == ownJ)
            return false;
        return DistrictPlan.isInside(i, tier, j) && plan.kind(i, tier, j) == DistrictPlan.EMPTY;
    }

    /**
     * Beams along the lattice lines under the ceiling, leaving a coffer above every cell with its lamp in the middle.
     */
    private void detailCeiling(Room room)
    {
        int top = roomTopAir(room);
        for (int z = Math.max(minZ(room), canvas.minZ()); z <= Math.min(maxZ(room), canvas.maxZ()); z++)
        {
            for (int x = Math.max(minX(room), canvas.minX()); x <= Math.min(maxX(room), canvas.maxX()); x++)
            {
                int localX = Math.floorMod(x - originX, CELL);
                int localZ = Math.floorMod(z - originZ, CELL);
                boolean beam = localX == LAST || localX == 0 || localZ == LAST || localZ == 0;
                if (beam && canvas.raw(x, top, z) == DaedalusMaterial.AIR.ordinal())
                    canvas.set(x, top, z, DaedalusMaterial.PANEL);
            }
        }
    }

    private void raiseInnerColonnade(Room room)
    {
        int y = floorY(room.minK()) + 1;
        int top = roomTopAir(room);
        for (int i = room.minI() + 1; i <= room.maxI(); i++)
        {
            for (int j = room.minJ() + 1; j <= room.maxJ(); j++)
            {
                int x = cellX(i);
                int z = cellZ(j);
                canvas.fill(x - 1, y, z - 1, x, top, z, DaedalusMaterial.COLUMN);
                canvas.fill(x - 1, y, z - 1, x, y, z, DaedalusMaterial.TRIM);
            }
        }
    }

    void furnish(Room room)
    {
        switch (room.shape())
        {
            case PLAIN -> furnishPlain(room);
            case CELL -> furnishCell(room);
            case CHAMBER -> furnishChamber(room);
            case STACKS -> furnishStacks(room);
            case COLONNADE -> furnishColonnade(room);
            case FLOOD -> furnishFlood(room);
            case VAULT -> furnishVault(room);
            case ATRIUM -> hangFromTop(centreX(room), centreZ(room), topAir(room.maxK()), (room.maxK() - room.minK()) * CELL / 2);
            default ->
            {
                // Other shapes carry their character in their walls.
            }
        }
    }

    /**
     * A chandelier on a long chain down the middle of every pit wide enough to show it, lighting the depth below the catwalks.
     */
    void hangInVoid(Room opening)
    {
        if (opening.shape() != RoomShape.PIT || opening.cellsWide() < 2 || opening.cellsDeep() < 2)
            return;
        int depth = (opening.maxK() - opening.minK() + 1) * CELL;
        int length = MIN_DEEP_HANG + DaedalusHash.pick(hash(opening, 1), Math.max(1, depth / 2 - MIN_DEEP_HANG));
        hangFromTop(cellX(opening.minI()) + opening.cellsWide() * CELL / 2, cellZ(opening.minJ()) + opening.cellsDeep() * CELL / 2, topAir(opening.maxK()), length);
    }

    private int minX(Room room)
    {
        return cellX(room.minI()) + 1;
    }

    private int maxX(Room room)
    {
        return cellX(room.maxI()) + LAST - 1;
    }

    private int minZ(Room room)
    {
        return cellZ(room.minJ()) + 1;
    }

    private int maxZ(Room room)
    {
        return cellZ(room.maxJ()) + LAST - 1;
    }

    private int centreX(Room room)
    {
        return (minX(room) + maxX(room)) / 2;
    }

    private int centreZ(Room room)
    {
        return (minZ(room) + maxZ(room)) / 2;
    }

    private boolean isLarge(Room room)
    {
        return maxX(room) - minX(room) + 1 >= LARGE_ROOM && maxZ(room) - minZ(room) + 1 >= LARGE_ROOM;
    }

    /**
     * @return the highest air under the ceiling of a tier, which for the top tier is below the world's ceiling
     */
    private static int topAir(int tier)
    {
        return Math.min(floorY(tier) + FULL_ROOM_TOP, DistrictPos.TOP_Y - 1);
    }

    private int roomTopAir(Room room)
    {
        int top = room.minK() == room.maxK() ? layout.roomHeight() : FULL_ROOM_TOP;
        return Math.min(floorY(room.maxK()) + top, DistrictPos.TOP_Y - 1);
    }

    /**
     * Collects where doors open into the room on one tier, so that nothing lands in a doorway or just inside it.
     */
    private void collectDoors(Room room, int tier, int lateralMargin, int depth)
    {
        doorZones.clear();
        int width = Math.max(WIDEST_DOOR, layout.corridorWidth());
        int low = LANE_CENTRE - (width - 1) / 2 - lateralMargin;
        int high = LANE_CENTRE + width / 2 + lateralMargin;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                for (int side = 0; side < Compass.COUNT; side++)
                {
                    if (!plan.isOpen(i, tier, j, side) && !plan.hasWindow(i, tier, j, side))
                        continue;
                    int x = cellX(i);
                    int z = cellZ(j);
                    switch (side)
                    {
                        case Compass.NORTH -> doorZones.add(new int[] { x + low, minZ(room), x + high, minZ(room) + depth });
                        case Compass.SOUTH -> doorZones.add(new int[] { x + low, maxZ(room) - depth, x + high, maxZ(room) });
                        case Compass.WEST -> doorZones.add(new int[] { minX(room), z + low, minX(room) + depth, z + high });
                        default -> doorZones.add(new int[] { maxX(room) - depth, z + low, maxX(room), z + high });
                    }
                }
            }
        }
    }

    private boolean isClear(int x, int z)
    {
        for (int[] zone : doorZones)
        {
            if (x >= zone[0] && x <= zone[2] && z >= zone[1] && z <= zone[3])
                return false;
        }
        return true;
    }

    private boolean placeOnFloor(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (!isClear(x, z) || canvas.raw(x, y, z) != DaedalusMaterial.AIR.ordinal() || canvas.get(x, y - 1, z).isPassable())
            return false;
        canvas.setProp(x, y, z, prop, facing);
        return true;
    }

    private void placeOnWall(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (isClear(x, z) && canvas.raw(x, y, z) == DaedalusMaterial.AIR.ordinal())
            canvas.setProp(x, y, z, prop, facing);
    }

    private void placeStatue(int x, int y, int z, DaedalusProp top, int facing)
    {
        if (canvas.raw(x, y + 1, z) == DaedalusMaterial.AIR.ordinal() && placeOnFloor(x, y, z, PLINTH, facing))
            canvas.setProp(x, y + 1, z, top, facing);
    }

    /**
     * A chandelier hung by a chain from the air under a ceiling, kept only if the whole drop and a block below it are clear.
     */
    private void hangFromTop(int x, int z, int top, int length)
    {
        int bottom = top - length;
        if (bottom < 1)
            return;
        for (int y = top; y >= bottom - 1; y--)
        {
            if (canvas.raw(x, y, z) != DaedalusMaterial.AIR.ordinal())
                return;
        }
        canvas.fill(x, bottom + 1, z, x, top, z, DaedalusMaterial.CHAIN);
        canvas.setProp(x, bottom, z, CHANDELIER, Compass.NORTH);
    }

    private interface WallSpot
    {
        void visit(int x, int z, int facing);
    }

    /**
     * Visits every spacing-th block along the inside of the room's walls, corners excepted, with the way that faces into the room.
     */
    private void alongWalls(Room room, int spacing, int offset, WallSpot spot)
    {
        int x0 = minX(room);
        int x1 = maxX(room);
        int z0 = minZ(room);
        int z1 = maxZ(room);
        for (int x = x0 + 1; x < x1; x++)
        {
            if (Math.floorMod(x - x0 + offset, spacing) != 0)
                continue;
            spot.visit(x, z0, Compass.SOUTH);
            spot.visit(x, z1, Compass.NORTH);
        }
        for (int z = z0 + 1; z < z1; z++)
        {
            if (Math.floorMod(z - z0 + offset, spacing) != 0)
                continue;
            spot.visit(x0, z, Compass.EAST);
            spot.visit(x1, z, Compass.WEST);
        }
    }

    private void inCorners(Room room, boolean allFour, DaedalusProp prop, int y)
    {
        placeOnFloor(minX(room), y, minZ(room), prop, Compass.SOUTH);
        placeOnFloor(maxX(room), y, maxZ(room), prop, Compass.NORTH);
        if (!allFour)
            return;
        placeOnFloor(maxX(room), y, minZ(room), prop, Compass.SOUTH);
        placeOnFloor(minX(room), y, maxZ(room), prop, Compass.NORTH);
    }

    private void furnishPlain(Room room)
    {
        int y = floorY(room.minK()) + 1;
        long bits = hash(room, 2);
        int offset = (int) (bits >>> 4 & 3);
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);

        var corner = switch (theme)
        {
            case ARCHIVE, STAIRWELL -> CANDELABRUM;
            case WRITHING -> GROWTH;
            default -> URN;
        };
        inCorners(room, (bits & 1) == 0, corner, y);

        switch (theme)
        {
            case ARCHIVE -> alongWalls(room, 4, offset, (x, z, facing) ->
            {
                if (!placeOnFloor(x, y, z, DESK, facing))
                    placeOnFloor(x, y, z, LEDGERS, facing);
            });
            case GALLERY ->
            {
                alongWalls(room, 3, offset, (x, z, facing) -> placeOnWall(x, y + 1, z, FRAME, facing));
                alongWalls(room, 6, offset + 1, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
            }
            case CISTERN ->
            {
                alongWalls(room, 5, offset, (x, z, facing) -> placeOnWall(x, y + 1, z, VALVE, facing));
                alongWalls(room, 7, offset + 2, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
            }
            case STAIRWELL -> alongWalls(room, 4, offset, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
            case WRITHING -> alongWalls(room, 2, offset, (x, z, facing) ->
            {
                if (DaedalusHash.pick(DaedalusHash.mix(x * 31L + z), 2) == 0)
                    placeOnFloor(x, y, z, GROWTH, facing);
            });
            case WARREN -> alongWalls(room, 5, offset, (x, z, facing) -> placeOnFloor(x, y, z, COT, facing));
        }

        if (!isLarge(room))
            return;
        int x = centreX(room);
        int z = centreZ(room);
        ringCentre(room, y);
        switch (theme)
        {
            case GALLERY -> placeStatue(x, y, z, BUST, Compass.SOUTH);
            case ARCHIVE -> placeStatue(x, y, z, LEDGERS, Compass.SOUTH);
            case CISTERN ->
            {
                for (int dx = 0; dx <= 1; dx++)
                {
                    for (int dz = 0; dz <= 1; dz++)
                        placeGrate(x + dx, y, z + dz);
                }
            }
            case WRITHING ->
            {
                placeOnFloor(x, y, z, GROWTH, Compass.SOUTH);
                placeOnFloor(x + 1, y, z + 1, GROWTH, Compass.WEST);
            }
            default -> placeStatue(x, y, z, URN, Compass.SOUTH);
        }
        if (theme == DaedalusTheme.WRITHING || theme == DaedalusTheme.WARREN)
            return;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                if (((i + j) & 1) == 0)
                    hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE + 1, roomTopAir(room), 1);
            }
        }
    }

    /**
     * Candelabra standing at the four tips of the floor's medallion, around whatever stands in the middle.
     */
    private void ringCentre(Room room, int y)
    {
        int radius = Math.min(maxX(room) - minX(room) + 1, maxZ(room) - minZ(room) + 1) / MEDALLION_DIVISOR;
        if (radius < MIN_MEDALLION)
            return;
        int x = centreX(room);
        int z = centreZ(room);
        var prop = theme == DaedalusTheme.WRITHING ? GROWTH : theme == DaedalusTheme.CISTERN ? URN : CANDELABRUM;
        placeOnFloor(x + radius + 1, y, z, prop, Compass.WEST);
        placeOnFloor(x - radius - 1, y, z, prop, Compass.EAST);
        placeOnFloor(x, y, z + radius + 1, prop, Compass.NORTH);
        placeOnFloor(x, y, z - radius - 1, prop, Compass.SOUTH);
    }

    private void placeGrate(int x, int feet, int z)
    {
        var floor = canvas.get(x, feet - 1, z);
        boolean onFloor = floor == DaedalusMaterial.FLOOR || floor == DaedalusMaterial.FLOOR_TRIM || floor == DaedalusMaterial.FLOOR_INLAY;
        if (isClear(x, z) && onFloor && canvas.raw(x, feet, z) == DaedalusMaterial.AIR.ordinal())
            canvas.setProp(x, feet - 1, z, GRATE, Compass.NORTH);
    }

    /**
     * A cot against the first side of the cell with no door, and sometimes a jar in a corner.
     */
    private void furnishCell(Room room)
    {
        int x = cellX(room.minI());
        int z = cellZ(room.minJ());
        int y = floorY(room.minK()) + 1;
        long bits = hash(room, 3);
        int first = (int) (bits & 3);
        for (int turn = 0; turn < Compass.COUNT; turn++)
        {
            int side = (first + turn) & 3;
            if (plan.isOpen(room.minI(), room.minK(), room.minJ(), side))
                continue;
            int along = side == Compass.NORTH || side == Compass.WEST ? 2 : 5;
            int otherDoors = 0;
            for (int other = 0; other < Compass.COUNT; other++)
                otherDoors += plan.isOpen(room.minI(), room.minK(), room.minJ(), other) ? 1 : 0;
            if (otherDoors == 0)
                return;
            int cotX = Compass.isAlongX(side) ? x + along : x + 5;
            int cotZ = Compass.isAlongX(side) ? z + 5 : z + along;
            if (isFreeOfCellLanes(room, cotX - x, cotZ - z))
                placeCellProp(cotX, y, cotZ, COT, Compass.opposite(side));
            if ((bits & 8) == 0 && isFreeOfCellLanes(room, 2, 2))
                placeCellProp(x + 2, y, z + 2, URN, Compass.SOUTH);
            return;
        }
    }

    private boolean isFreeOfCellLanes(Room room, int localX, int localZ)
    {
        for (int side = 0; side < Compass.COUNT; side++)
        {
            if (!plan.isOpen(room.minI(), room.minK(), room.minJ(), side))
                continue;
            int lateral = Compass.isAlongX(side) ? localZ : localX;
            if (lateral >= LANE_CENTRE - 1 && lateral <= LANE_CENTRE + 1)
                return false;
        }
        return true;
    }

    private void placeCellProp(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (canvas.raw(x, y, z) == DaedalusMaterial.AIR.ordinal() && !canvas.get(x, y - 1, z).isPassable())
            canvas.setProp(x, y, z, prop, facing);
    }

    private void furnishChamber(Room room)
    {
        int y = floorY(room.minK()) + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        alongWalls(room, 2, (int) (hash(room, 4) & 1), (x, z, facing) ->
        {
            if (DaedalusHash.pick(DaedalusHash.mix(x * 31L + z), 3) == 0)
                placeOnFloor(x, y, z, GROWTH, facing);
        });
    }

    /**
     * Ledgers left on the floor at the ends of the shelf rows, and chandeliers down the aisles of tall halls.
     */
    private void furnishStacks(Room room)
    {
        boolean alongX = (room.variant() & 1) != 0;
        int y = floorY(room.minK()) + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        int crossCells = alongX ? room.cellsDeep() : room.cellsWide();
        int crossOrigin = alongX ? cellZ(room.minJ()) : cellX(room.minI());
        int rowStart = (alongX ? minX(room) : minZ(room)) + 2;
        int rowEnd = (alongX ? maxX(room) : maxZ(room)) - 2;
        for (int crossCell = 0; crossCell < crossCells; crossCell++)
        {
            int cross = crossOrigin + crossCell * CELL + 5;
            if (crossCell == crossCells - 1 || (crossCell & 1) != 0)
                continue;
            int startX = alongX ? rowStart : cross;
            int startZ = alongX ? cross : rowStart;
            int endX = alongX ? rowEnd : cross;
            int endZ = alongX ? cross : rowEnd;
            placeOnFloor(startX, y, startZ, LEDGERS, alongX ? Compass.WEST : Compass.NORTH);
            placeOnFloor(endX, y, endZ, LEDGERS, alongX ? Compass.EAST : Compass.SOUTH);
        }

        int hang = room.maxK() > room.minK() ? TALL_HANG : 1;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                if (((i + j) & 1) == 0)
                    hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE, roomTopAir(room), hang);
            }
        }
    }

    /**
     * Faceless busts on plinths and empty frames between the columns, under a chandelier in every inner bay.
     */
    private void furnishColonnade(Room room)
    {
        int y = floorY(room.minK()) + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        alongWalls(room, CELL, 2, (x, z, facing) -> placeStatue(x, y, z, BUST, facing));
        alongWalls(room, CELL, 6, (x, z, facing) -> placeOnWall(x, y + 1, z, FRAME, facing));
        alongWalls(room, CELL, 4, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
        int hang = room.maxK() > room.minK() ? roomTopAir(room) - (y + HEADROOM_UNDER_CHANDELIER) : 1;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
                hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE, roomTopAir(room), hang);
        }
    }

    private void furnishFlood(Room room)
    {
        int y = floorY(room.maxK()) + 1;
        collectDoors(room, room.maxK(), 1, DOOR_CLEARANCE);
        alongWalls(room, 6, (int) (hash(room, 5) & 3), (x, z, facing) -> placeOnWall(x, y + 1, z, VALVE, facing));
        for (int j = room.minJ() + 1; j < room.maxJ(); j++)
        {
            for (int i = room.minI() + 1; i < room.maxI(); i++)
                hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE, roomTopAir(room), 2);
        }
    }

    /**
     * Benches round the floor of the vault facing a plinth in the middle, under chandeliers on chains long enough to be lost in the height.
     */
    private void furnishVault(Room room)
    {
        int y = floorY(room.minK()) + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        alongWalls(room, 5, (int) (hash(room, 6) & 3), (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
        placeStatue(centreX(room), y, centreZ(room), theme == DaedalusTheme.GALLERY ? BUST : URN, Compass.SOUTH);
        ringCentre(room, y);

        Room opening = null;
        for (var candidate : plan.voids())
        {
            if (candidate.shape() == RoomShape.VAULT && candidate.minI() == room.minI() && candidate.minJ() == room.minJ() && candidate.minK() == room.minK())
                opening = candidate;
        }
        if (opening == null)
            return;
        int top = topAir(opening.maxK());
        int longest = top - (y + HEADROOM_UNDER_CHANDELIER);
        if (longest < 1)
            return;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                if (((i + j) & 1) != 0)
                    continue;
                long bits = DaedalusHash.mix(room.variant(), i, j, 7);
                int length = longest / 2 + DaedalusHash.pick(bits, Math.max(1, longest / 2));
                hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE, top, length);
            }
        }
    }
}
