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
    private static final int SHRINE_ODDS = 3;
    /** One room in this many keeps a cache in one of its corners. */
    private static final int CACHE_ODDS = 5;
    private static final int RITUAL_ODDS = 3;
    private static final int VAULT_RELIC_ODDS = 2;
    private static final int RITUAL_ROOM = 13;
    private static final int RING_MARGIN = 3;
    private static final int VAULT_RING_GAP = 4;
    private static final int SHORTEST_VAULT_MONOLITH = 3;
    private static final int TALLEST_VAULT_MONOLITH = 8;
    private static final int RITUAL_RING_RADIUS = 3;
    private static final int CLOCK_SPACING = 8;
    private static final int PIPE_SPACING = 10;
    /** The corner of a cell's floor that no lane crosses, since cells are carved four wide from {@link #LANE_CENTRE}. */
    private static final int CELL_CORNER = 5;
    private static final long PICK_SALT = 0x5EED_F0E5L;

    private static final int VALVE_SPACING = 10;
    private static final int SCONCE_SPACING = 6;
    private static final int TABLE_MARGIN = 3;
    private static final int TABLE_ROWS_SPAN = 13;
    private static final int TABLE_ROW_GAP = 3;
    private static final int POOL_ROOM = 13;
    private static final int POOL_REACH = 3;
    private static final int ALCOVE_HEIGHT = 3;
    /** How far into a cell from the room's wall a group of furniture stands, leaving the wall's own furniture and the doors their room. */
    private static final int GROUP_SET_BACK = 5;
    private static final int RITUAL_CLEARING = RoomRelics.RITUAL_REACH + 1;
    private static final int DAIS_CLEARING = RoomFeatures.DAIS_REACH + 1;
    private static final int POOL_CLEARING = POOL_REACH + 1;
    private static final int ENGINE_CLEARING = RoomFeatures.ENGINE_REACH + 1;
    private static final int ENGINE_ODDS = 3;
    private static final int VAULT_PISTON_RING = 7;
    private static final int GEAR_SPACING = 9;
    private static final int CRYPT_ODDS = 4;
    private static final int BOILER_ODDS = 3;
    private static final int BOILER_CLEARING = RoomFeatures.BOILER_REACH + 2;
    private static final int MIN_BOILER_AIR = 3;

    private enum Purpose
    {
        FURNISHED,
        CRYPT,
        BOILER_ROOM
    }

    private final List<int[]> doorZones = new ArrayList<>();
    private final RoomRelics relics;
    private final RoomFeatures features;

    RoomFurnisher(DistrictPlan plan, DaedalusCanvas canvas)
    {
        this.plan = plan;
        this.canvas = canvas;
        theme = plan.theme;
        layout = theme.layout();
        originX = plan.pos.originX();
        originZ = plan.pos.originZ();
        relics = new RoomRelics(canvas, this::isClear);
        features = new RoomFeatures(canvas, this::isClear);
    }

    private static int pick(int x, int z, int salt, int odds)
    {
        return DaedalusHash.pick(DaedalusHash.mix(x, z, salt, PICK_SALT), odds);
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
                int nicheOffset = (int) (bits >>> 1 & 7);

                for (int height = 0; height <= WAINSCOT_HEIGHT; height++)
                {
                    var current = canvas.get(wx, y + height, wz);
                    if (current == DaedalusMaterial.WALL || current == DaedalusMaterial.TRIM || current == DaedalusMaterial.PANEL)
                        canvas.set(wx, y + height, wz, height == WAINSCOT_HEIGHT ? DaedalusMaterial.TRIM : DaedalusMaterial.PANEL);
                }

                boolean corner = along == from || along == to;
                if (isLarge(room))
                {
                    for (int shift = -1; shift <= 1; shift++)
                    {
                        int centre = step - shift;
                        if (Math.floorMod(centre + nicheOffset, NICHE_SPACING) == 0 && isAlcove(room, side, from, to, inside, wall, centre))
                            cutAlcove(wx, y, wz, side, shift);
                    }
                }
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
                boolean niche = !isLarge(room) && Math.floorMod(step + nicheOffset, NICHE_SPACING) == 0 && isRockBehind(wx, room.minK(), wz, side);
                if (niche && canvas.get(wx, y, wz) == DaedalusMaterial.PANEL)
                {
                    canvas.fill(wx, y, wz, wx, y + NICHE_HEIGHT - 1, wz, DaedalusMaterial.AIR);
                    canvas.set(wx, y + NICHE_HEIGHT, wz, DaedalusMaterial.arch(Compass.opposite(side)));
                    if (pick(wx, wz, 1, SHRINE_ODDS) == 0)
                        relics.shrine(wx, y, wz);
                    else
                        canvas.setProp(wx, y, wz, nicheProp(pick(wx, wz, 2, 2) == 0), Compass.opposite(side));
                }
            }
        }
    }

    /**
     * @param centre the step along the wall of the middle of the alcove
     * @return whether three wall blocks from there can be cut back to the rock, decided from the plan alone so every chunk agrees
     */
    private boolean isAlcove(Room room, int side, int from, int to, int inside, int wall, int centre)
    {
        boolean alongX = side == Compass.NORTH || side == Compass.SOUTH;
        for (int shift = -1; shift <= 1; shift++)
        {
            int along = from + centre + shift;
            if (along <= from || along >= to)
                return false;
            int wx = alongX ? along : wall;
            int wz = alongX ? wall : along;
            if (!isClear(alongX ? along : inside, alongX ? inside : along) || !isRockBehind(wx, room.minK(), wz, side))
                return false;
        }
        return true;
    }

    /**
     * A bay three blocks wide cut into the wall, arched at its sides under a keystone, with something set in it.
     *
     * @param shift where this wall block lies in the bay, from -1 to 1
     */
    private void cutAlcove(int wx, int y, int wz, int side, int shift)
    {
        if (canvas.get(wx, y, wz) != DaedalusMaterial.PANEL)
            return;
        int facing = Compass.opposite(side);
        if (shift == 0)
        {
            canvas.fill(wx, y, wz, wx, y + ALCOVE_HEIGHT - 1, wz, DaedalusMaterial.AIR);
            canvas.set(wx, y + ALCOVE_HEIGHT, wz, DaedalusMaterial.TRIM);
            fillAlcove(wx, y, wz, facing);
            return;
        }
        boolean alongX = side == Compass.NORTH || side == Compass.SOUTH;
        int away = alongX ? (shift < 0 ? Compass.WEST : Compass.EAST) : (shift < 0 ? Compass.NORTH : Compass.SOUTH);
        canvas.fill(wx, y, wz, wx, y + ALCOVE_HEIGHT - 2, wz, DaedalusMaterial.AIR);
        canvas.set(wx, y + ALCOVE_HEIGHT - 1, wz, DaedalusMaterial.arch(away));
        if (pick(wx, wz, 16, 3) == 0)
            canvas.setProp(wx, y, wz, theme == DaedalusTheme.GALLERY ? PLANTER : CANDLES, facing);
    }

    private void fillAlcove(int x, int y, int z, int facing)
    {
        int roll = pick(x, z, 15, 4);
        if (roll == 0)
        {
            relics.shrine(x, y, z);
            return;
        }
        switch (theme)
        {
            case ARCHIVE ->
            {
                if (roll == 1)
                    canvas.setProp(x, y + 1, z, SCROLLS, facing);
                canvas.setProp(x, y, z, roll == 1 ? BOOKS : roll == 2 ? CABINET : pick(x, z, 28, 2) == 0 ? CONSOLE : LECTERN, facing);
            }
            case GALLERY ->
            {
                if (roll == 1)
                {
                    canvas.setProp(x, y, z, PLINTH, facing);
                    canvas.setProp(x, y + 1, z, BUST, facing);
                }
                else
                    canvas.setProp(x, y, z, roll == 2 ? STATUE : PLANTER, facing);
            }
            case STAIRWELL ->
            {
                if (roll == 1)
                {
                    canvas.setProp(x, y, z, PLINTH, facing);
                    canvas.setProp(x, y + 1, z, HOURGLASS, facing);
                }
                else
                    canvas.setProp(x, y, z, roll == 2 ? AUTOMATON : BENCH, facing);
            }
            case CISTERN -> canvas.setProp(x, y, z, roll == 1 ? BASIN : roll == 2 ? PISTON : CELLS, facing);
            case WARREN, WRITHING -> canvas.setProp(x, y, z, roll == 1 ? REMAINS : roll == 2 ? CANDLES : AUTOMATON, facing);
        }
    }

    private DaedalusProp nicheProp(boolean second)
    {
        return switch (theme)
        {
            case ARCHIVE -> second ? CANDLES : CANDELABRUM;
            case STAIRWELL -> second ? HOURGLASS : CANDELABRUM;
            case GALLERY -> second ? ORRERY : URN;
            case CISTERN -> second ? BASIN : URN;
            case WARREN -> second ? CANDLES : URN;
            case WRITHING -> second ? REMAINS : URN;
        };
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
        switch (room.shape())
        {
            case PLAIN, CELL, CHAMBER, STACKS, COLONNADE, VAULT -> stashCache(room);
            default ->
            {
                // No floor of their own to keep anything on.
            }
        }
    }

    /**
     * A barrel in the corner of the room its hash picks, unless something already stands there. Only that corner's own column is read, so every
     * chunk comes to the same answer.
     */
    private void stashCache(Room room)
    {
        if (DaedalusHash.pick(hash(room, 40), CACHE_ODDS) != 0)
            return;

        int y = floorY(room.minK()) + 1;
        int corner = DaedalusHash.pick(hash(room, 41), 4);
        int x = corner == 0 || corner == 3 ? minX(room) : maxX(room);
        int z = corner < 2 ? minZ(room) : maxZ(room);
        if (isClear(x, z) && canvas.raw(x, y, z) == DaedalusMaterial.AIR.ordinal() && !canvas.get(x, y - 1, z).isPassable())
            canvas.set(x, y, z, DaedalusMaterial.CACHE);
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

    /**
     * Like {@link #placeOnFloor}, but over a carpet runner too, for furniture set out down the middle of a room.
     */
    private boolean placeOverRunner(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (canvas.raw(x, y, z) == DaedalusMaterial.CARPET.ordinal() && isClear(x, z) && !canvas.get(x, y - 1, z).isPassable())
        {
            canvas.setProp(x, y, z, prop, facing);
            return true;
        }
        return placeOnFloor(x, y, z, prop, facing);
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
        hangFromTop(x, z, top, length, CHANDELIER);
    }

    private void hangFromTop(int x, int z, int top, int length, DaedalusProp prop)
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
        canvas.setProp(x, bottom, z, prop, Compass.NORTH);
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

    private void inCorners(Room room, boolean allFour, int y)
    {
        placeOnFloor(minX(room), y, minZ(room), cornerProp(minX(room), minZ(room)), Compass.SOUTH);
        placeOnFloor(maxX(room), y, maxZ(room), cornerProp(maxX(room), maxZ(room)), Compass.NORTH);
        if (!allFour)
            return;
        placeOnFloor(maxX(room), y, minZ(room), cornerProp(maxX(room), minZ(room)), Compass.SOUTH);
        placeOnFloor(minX(room), y, maxZ(room), cornerProp(minX(room), maxZ(room)), Compass.NORTH);
    }

    private DaedalusProp cornerProp(int x, int z)
    {
        int roll = pick(x, z, 22, 3);
        return switch (theme)
        {
            case ARCHIVE -> roll == 0 ? CANDELABRUM : roll == 1 ? CANDLES : CABINET;
            case STAIRWELL -> roll == 0 ? PLANTER : CANDELABRUM;
            case GALLERY -> roll == 0 ? URN : roll == 1 ? PLANTER : STATUE;
            case CISTERN -> roll == 0 ? BARREL : BASIN;
            case WARREN -> roll == 0 ? BARREL : BRAZIER;
            case WRITHING -> roll == 0 ? REMAINS : GROWTH;
        };
    }

    private void cobwebCorners(int x0, int z0, int x1, int z1, int top, int odds)
    {
        int[][] corners = { { x0, z0 }, { x1, z0 }, { x0, z1 }, { x1, z1 } };
        for (int[] corner : corners)
        {
            if (pick(corner[0], corner[1], 17, odds) == 0 && canvas.raw(corner[0], top, corner[1]) == DaedalusMaterial.AIR.ordinal())
                canvas.set(corner[0], top, corner[1], DaedalusMaterial.COBWEB);
        }
    }

    private void placeSconces(Room room, int y)
    {
        alongWalls(room, SCONCE_SPACING, (int) (hash(room, 15) & 3) + 3, (x, z, facing) ->
        {
            if (pick(x, z, 13, 2) == 0)
                placeOnWall(x, y + 2, z, SCONCE, facing);
        });
    }

    private void furnishPlain(Room room)
    {
        int y = floorY(room.minK()) + 1;
        long bits = hash(room, 2);
        int offset = (int) (bits >>> 4 & 3);
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);

        inCorners(room, (bits & 1) == 0, y);
        var purpose = purposeOf(room, y);

        if (purpose == Purpose.CRYPT)
            furnishCrypt(room, y);
        else switch (theme)
        {
            case ARCHIVE -> furnishArchiveWalls(room, y);
            case GALLERY ->
            {
                alongWalls(room, 3, offset, (x, z, facing) -> placeOnWall(x, y + 1, z, pick(x, z, 4, 3) == 0 ? MIRROR : FRAME, facing));
                alongWalls(room, 6, offset + 1, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
                alongWalls(room, 6, offset + 4, (x, z, facing) -> placeOnFloor(x, y, z, pick(x, z, 18, 2) == 0 ? PLANTER : STATUE, facing));
                alongWalls(room, 12, offset + 1, (x, z, facing) -> placeOnWall(x, y + 2, z, TAPESTRY, facing));
            }
            case CISTERN ->
            {
                alongWalls(room, VALVE_SPACING, offset, (x, z, facing) ->
                {
                    int roll = pick(x, z, 12, 4);
                    if (roll < 2)
                        placeOnWall(x, y + 1, z, roll == 0 ? VALVE : GAUGE, facing);
                });
                alongWalls(room, PIPE_SPACING, offset + 2, (x, z, facing) -> runPipes(x, y, z, roomTopAir(room), facing));
                alongWalls(room, GEAR_SPACING, offset + 5, (x, z, facing) ->
                {
                    if (pick(x, z, 24, 2) == 0)
                        placeOnWall(x, y + 2, z, GEARS, facing);
                });
                alongWalls(room, 7, offset + 2, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
                alongWalls(room, 6, offset + 3, (x, z, facing) ->
                {
                    if (pick(x, z, 14, 2) == 0)
                        placeOnFloor(x, y, z, BARREL, facing);
                });
            }
            case STAIRWELL ->
            {
                alongWalls(room, 4, offset, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
                alongWalls(room, CLOCK_SPACING, offset + 2, (x, z, facing) -> placeOnWall(x, y + 2, z, CLOCK, facing));
                alongWalls(room, CLOCK_SPACING, offset + 2, (x, z, facing) ->
                {
                    int roll = pick(x, z, 18, 3);
                    if (roll < 2)
                        placeOnFloor(x, y, z, roll == 0 ? STATUE : PLANTER, facing);
                });
                alongWalls(room, CLOCK_SPACING, offset + 4, (x, z, facing) -> placeOnWall(x, y + 2, z, pick(x, z, 24, 2) == 0 ? GEARS : TAPESTRY, facing));
            }
            case WRITHING -> alongWalls(room, 2, offset, (x, z, facing) ->
            {
                int roll = pick(x, z, 0, 6);
                if (roll < 3)
                    placeOnFloor(x, y, z, roll == 0 ? REMAINS : GROWTH, facing);
            });
            case WARREN -> alongWalls(room, 5, offset, (x, z, facing) -> placeOnFloor(x, y, z, pick(x, z, 5, 2) == 0 ? CRATES : COT, facing));
        }

        if (theme == DaedalusTheme.WRITHING || theme == DaedalusTheme.WARREN)
            hangCages(room);
        if (theme != DaedalusTheme.ARCHIVE)
            placeSconces(room, y);
        if (theme == DaedalusTheme.ARCHIVE || theme == DaedalusTheme.STAIRWELL)
            cobwebCorners(minX(room), minZ(room), maxX(room), maxZ(room), roomTopAir(room), 3);
        if (!isLarge(room))
        {
            if (purpose != Purpose.CRYPT)
                furnishSmallCentre(room, y);
            return;
        }
        int x = centreX(room);
        int z = centreZ(room);
        if (purpose == Purpose.BOILER_ROOM)
        {
            features.raiseBoiler(x, y, z, (int) (hash(room, 33) & 3), roomTopAir(room), minX(room), maxX(room), minZ(room), maxZ(room));
            features.abandon(x, y, z, BOILER_CLEARING, hash(room, 34));
            furnishFloor(room, y, BOILER_CLEARING);
        }
        else if (purpose == Purpose.CRYPT)
        {
            placeOnFloor(x, y, z, CONSOLE, Compass.NORTH);
            placeOnFloor(x, y, z + 1, CONSOLE, Compass.SOUTH);
            placeOnFloor(x + 1, y, z, CELLS, Compass.EAST);
            features.abandon(x, y, z, DAIS_CLEARING, hash(room, 34));
        }
        else if (hasSpace(room, x, z, RoomRelics.RITUAL_REACH, 1, RITUAL_ROOM) && DaedalusHash.pick(hash(room, 10), RITUAL_ODDS) == 0)
        {
            raiseRitual(room, x, y, z);
            furnishFloor(room, y, RITUAL_CLEARING);
        }
        else
        {
            int clearing = placeCentrepiece(room, x, y, z);
            if (clearing >= 0)
                furnishFloor(room, y, clearing);
        }
        if (theme == DaedalusTheme.WRITHING || theme == DaedalusTheme.WARREN)
            return;
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                boolean lantern = (theme == DaedalusTheme.CISTERN || theme == DaedalusTheme.STAIRWELL) && DaedalusHash.pick(DaedalusHash.mix(room.variant(), i, j, 25), 2) == 0;
                if (((i + j) & 1) == 0)
                    hangFromTop(cellX(i) + LANE_CENTRE, cellZ(j) + LANE_CENTRE + 1, roomTopAir(room), 1, lantern ? LANTERN : CHANDELIER);
            }
        }
    }

    /**
     * The middle of a large room: a statue raised on a dais, rows of tables, a pool in the cisterns, or grates and growths.
     */
    /**
     * @return how far round the middle the rest of the floor must stay clear, or -1 when the centrepiece fills the floor
     */
    private int placeCentrepiece(Room room, int x, int y, int z)
    {
        long bits = hash(room, 12);
        boolean second = (bits & 1) != 0;
        boolean tables = (bits >>> 1 & 3) == 0 || theme == DaedalusTheme.ARCHIVE && (bits >>> 1 & 3) == 1;
        if (tables && (theme == DaedalusTheme.ARCHIVE || theme == DaedalusTheme.GALLERY || theme == DaedalusTheme.STAIRWELL))
        {
            furnishTables(room, y, theme == DaedalusTheme.GALLERY ? ORRERY : theme == DaedalusTheme.STAIRWELL ? HOURGLASS : LEDGERS);
            return -1;
        }
        boolean machineHall = theme == DaedalusTheme.CISTERN || theme == DaedalusTheme.STAIRWELL || theme == DaedalusTheme.ARCHIVE;
        if (machineHall && DaedalusHash.pick(bits >>> 8, ENGINE_ODDS) == 0 && hasSpace(room, x, z, RoomFeatures.ENGINE_REACH, 1, RITUAL_ROOM))
        {
            features.raiseEngine(x, y, z);
            features.abandon(x, y, z, RoomFeatures.ENGINE_REACH, hash(room, 34));
            return ENGINE_CLEARING;
        }
        if (theme == DaedalusTheme.CISTERN && (bits >>> 1 & 1) == 0 && hasSpace(room, x, z, POOL_REACH, 1, POOL_ROOM))
        {
            features.raisePool(x, y, z, POOL_REACH);
            return POOL_CLEARING;
        }
        ringCentre(room, y);
        boolean dais = hasSpace(room, x, z, RoomFeatures.DAIS_REACH, 1, 0);
        int top = dais && theme != DaedalusTheme.CISTERN && theme != DaedalusTheme.WARREN && theme != DaedalusTheme.WRITHING ? features.raiseDais(x, y, z) : y;
        switch (theme)
        {
            case GALLERY -> placeStatue(x, top, z, second ? ORRERY : BUST, Compass.SOUTH);
            case ARCHIVE -> placeStatue(x, top, z, second ? ORRERY : LEDGERS, Compass.SOUTH);
            case STAIRWELL ->
            {
                if (second)
                    placeOnFloor(x, top, z, STATUE, Compass.SOUTH);
                else
                    placeStatue(x, top, z, HOURGLASS, Compass.SOUTH);
            }
            case WARREN -> placeOnFloor(x, y, z, BRAZIER, Compass.SOUTH);
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
        }
        return DAIS_CLEARING;
    }

    /**
     * A small group of furniture in each cell of a large room, away from its middle and set back from its walls.
     *
     * @param clearing how far round the room's middle nothing goes
     */
    private void furnishFloor(Room room, int y, int clearing)
    {
        int middleX = centreX(room);
        int middleZ = centreZ(room);
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                int x = cellX(i) + (i == room.minI() ? GROUP_SET_BACK : i == room.maxI() ? LAST - GROUP_SET_BACK : LANE_CENTRE);
                int z = cellZ(j) + (j == room.minJ() ? GROUP_SET_BACK : j == room.maxJ() ? LAST - GROUP_SET_BACK : LANE_CENTRE);
                if (Math.max(Math.abs(x - middleX), Math.abs(z - middleZ)) <= clearing)
                    continue;
                long bits = DaedalusHash.mix(room.variant(), i, j, 23);
                int facing = (int) (bits & 3);
                placeGroup(x, y, z, (int) (bits >>> 2 & 3), facing);
            }
        }
    }

    private void placeGroup(int x, int y, int z, int kind, int facing)
    {
        int ahead = facing;
        int side = Compass.clockwise(facing);
        int aheadX = Compass.dx(ahead);
        int aheadZ = Compass.dz(ahead);
        int sideX = Compass.dx(side);
        int sideZ = Compass.dz(side);
        switch (theme)
        {
            case ARCHIVE ->
            {
                if (kind == 0)
                {
                    raiseShelf(x, y, z);
                    raiseShelf(x + sideX, y, z + sideZ);
                    placeOnFloor(x - sideX, y, z - sideZ, BOOKS, facing);
                }
                else if (kind == 1)
                {
                    placeOnFloor(x, y, z, TABLE, side);
                    placeOnFloor(x, y + 1, z, CANDLES, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, CHAIR, Compass.opposite(ahead));
                    placeOnFloor(x - aheadX, y, z - aheadZ, CHAIR, ahead);
                }
                else
                {
                    placeOnFloor(x, y, z, kind == 2 ? LECTERN : CONSOLE, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, kind == 2 ? CABINET : CELLS, facing);
                }
            }
            case GALLERY ->
            {
                if (kind == 0)
                {
                    placeStatue(x, y, z, BUST, facing);
                    placeOnFloor(x + aheadX * 2, y, z + aheadZ * 2, BENCH, Compass.opposite(facing));
                }
                else if (kind == 1)
                {
                    placeOnFloor(x, y, z, STATUE, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, PLANTER, facing);
                    placeOnFloor(x - sideX, y, z - sideZ, PLANTER, facing);
                }
                else
                {
                    placeStatue(x, y, z, ORRERY, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, CANDELABRUM, facing);
                }
            }
            case STAIRWELL ->
            {
                if (kind == 0)
                {
                    placeOnFloor(x, y, z, PLANTER, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, BENCH, facing);
                    placeOnFloor(x - aheadX, y, z - aheadZ, BENCH, Compass.opposite(facing));
                }
                else if (kind == 1)
                    placeOnFloor(x, y, z, (facing & 1) == 0 ? STATUE : AUTOMATON, facing);
                else if (kind == 2)
                {
                    placeStatue(x, y, z, HOURGLASS, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, CANDELABRUM, facing);
                }
                else
                {
                    placeOnFloor(x, y, z, COIL, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, CONSOLE, Compass.opposite(side));
                }
            }
            case CISTERN ->
            {
                if (kind == 0)
                {
                    placeGrate(x, y, z);
                    placeOnFloor(x + sideX, y, z + sideZ, BARREL, facing);
                }
                else if (kind == 1)
                {
                    placeOnFloor(x, y, z, PISTON, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, PISTON, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, CELLS, facing);
                }
                else if (kind == 2)
                {
                    placeOnFloor(x, y, z, FLYWHEEL, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, BARREL, facing);
                }
                else
                {
                    placeOnFloor(x, y, z, BARREL, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, BARREL, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, CRATES, facing);
                }
            }
            case WRITHING ->
            {
                if (kind == 0)
                {
                    placeOnFloor(x, y, z, GROWTH, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, REMAINS, facing);
                    placeOnFloor(x + aheadX, y, z + aheadZ, CANDLES, facing);
                }
                else if (kind == 1)
                {
                    relics.monolith(x, y, z, 1 + (facing & 1) + kind);
                    placeOnFloor(x + aheadX, y, z + aheadZ, CANDLES, facing);
                }
                else if (kind == 3)
                {
                    placeOnFloor(x, y, z, AUTOMATON, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, GROWTH, facing);
                    placeOnFloor(x - sideX, y, z - sideZ, GROWTH, side);
                }
                else
                {
                    placeOnFloor(x, y, z, GROWTH, facing);
                    placeOnFloor(x + sideX, y, z + sideZ, GROWTH, side);
                    placeOnFloor(x - aheadX, y, z - aheadZ, GROWTH, ahead);
                }
            }
            case WARREN ->
            {
                placeOnFloor(x, y, z, BRAZIER, facing);
                placeOnFloor(x + sideX, y, z + sideZ, CRATES, facing);
            }
        }
    }

    /**
     * A free-standing bookcase two blocks high.
     */
    private void raiseShelf(int x, int y, int z)
    {
        if (!isClear(x, z) || canvas.raw(x, y, z) != DaedalusMaterial.AIR.ordinal() || canvas.raw(x, y + 1, z) != DaedalusMaterial.AIR.ordinal() || canvas.get(x, y - 1, z).isPassable())
            return;
        canvas.fill(x, y, z, x, y + 1, z, DaedalusMaterial.SHELF);
    }

    /**
     * Rows of tables down the room's length with chairs on both sides and things left on them, broken at every lattice line so the room can still be crossed.
     */
    private void furnishTables(Room room, int y, DaedalusProp keepsake)
    {
        boolean alongX = maxX(room) - minX(room) >= maxZ(room) - minZ(room);
        int cross = alongX ? centreZ(room) : centreX(room);
        int span = alongX ? maxZ(room) - minZ(room) + 1 : maxX(room) - minX(room) + 1;
        int[] rows = span >= TABLE_ROWS_SPAN ? new int[] { cross - TABLE_ROW_GAP, cross + TABLE_ROW_GAP } : new int[] { cross };
        int from = (alongX ? minX(room) : minZ(room)) + TABLE_MARGIN;
        int to = (alongX ? maxX(room) : maxZ(room)) - TABLE_MARGIN;
        int origin = alongX ? originX : originZ;
        int tableFacing = alongX ? Compass.NORTH : Compass.EAST;
        for (int row : rows)
        {
            for (int along = from; along <= to; along++)
            {
                int local = Math.floorMod(along - origin, CELL);
                if (local == 0 || local == LAST)
                    continue;
                int x = alongX ? along : row;
                int z = alongX ? row : along;
                placeOverRunner(x, y, z, TABLE, tableFacing);
                int roll = pick(x, z, 11, 5);
                var onTop = roll == 0 ? CANDLES : roll == 1 ? BOOKS : roll == 2 ? keepsake : roll == 3 && pick(x, z, 27, 2) == 0 ? CELLS : null;
                if (onTop != null && canvas.get(x, y, z) == DaedalusMaterial.PROP)
                    placeOnFloor(x, y + 1, z, onTop, Compass.opposite(tableFacing));
                if ((local & 1) == 0 || !isClear(x, z))
                    continue;
                int before = alongX ? Compass.SOUTH : Compass.EAST;
                int behind = Compass.opposite(before);
                placeOverRunner(x - Compass.dx(before), y, z - Compass.dz(before), CHAIR, before);
                placeOverRunner(x - Compass.dx(behind), y, z - Compass.dz(behind), CHAIR, behind);
            }
        }
    }

    /**
     * What a room was kept for: furnished as its theme would, a crypt of sleepers in stasis, or a boiler room.
     */
    private Purpose purposeOf(Room room, int y)
    {
        boolean crypts = theme == DaedalusTheme.ARCHIVE || theme == DaedalusTheme.GALLERY || theme == DaedalusTheme.STAIRWELL;
        if (crypts && DaedalusHash.pick(hash(room, 30), CRYPT_ODDS) == 0)
            return Purpose.CRYPT;
        boolean boilers = theme == DaedalusTheme.CISTERN || theme == DaedalusTheme.STAIRWELL;
        if (boilers && isLarge(room) && DaedalusHash.pick(hash(room, 31), BOILER_ODDS) == 0 && roomTopAir(room) - y >= MIN_BOILER_AIR
            && hasSpace(room, centreX(room), centreZ(room), RoomFeatures.BOILER_REACH, 1, 0))
            return Purpose.BOILER_ROOM;
        return Purpose.FURNISHED;
    }

    /**
     * Sarcophagi in rows down the two long walls, head to the wall where a grated vent breathes into each one, with a vent in the floor at its foot. Some lie open.
     * They stand between the pilasters, so no head or foot is ever refused while the other half stands.
     */
    private void furnishCrypt(Room room, int y)
    {
        boolean alongX = maxX(room) - minX(room) >= maxZ(room) - minZ(room);
        int pilasterStep = Math.floorMod(PILASTER_SPACING / 2 - (int) (hash(room, 9) & 1), PILASTER_SPACING);
        alongWalls(room, 2, Math.floorMod(-(pilasterStep + 1), 2), (x, z, facing) ->
        {
            if (Compass.isAlongX(facing) == alongX)
                return;
            int footX = x + Compass.dx(facing);
            int footZ = z + Compass.dz(facing);
            if (!isClear(x, z) || !isClear(footX, footZ))
                return;
            placeOnFloor(x, y, z, pick(x, z, 33, 4) == 0 ? SARCOPHAGUS_OPEN : SARCOPHAGUS, facing);
            placeOnFloor(footX, y, footZ, SARCOPHAGUS_FOOT, facing);
            int wallX = x - Compass.dx(facing);
            int wallZ = z - Compass.dz(facing);
            for (int height = y; height <= y + 1; height++)
            {
                var wall = canvas.get(wallX, height, wallZ);
                if (wall == DaedalusMaterial.PANEL || wall == DaedalusMaterial.TRIM || wall == DaedalusMaterial.WALL)
                    canvas.set(wallX, height, wallZ, DaedalusMaterial.VENT);
            }
            int ventX = footX + Compass.dx(facing);
            int ventZ = footZ + Compass.dz(facing);
            var floor = canvas.get(ventX, y - 1, ventZ);
            boolean onFloor = floor == DaedalusMaterial.FLOOR || floor == DaedalusMaterial.FLOOR_TRIM || floor == DaedalusMaterial.FLOOR_INLAY;
            if (isClear(ventX, ventZ) && onFloor && canvas.raw(ventX, y, ventZ) == DaedalusMaterial.AIR.ordinal())
                canvas.set(ventX, y - 1, ventZ, DaedalusMaterial.VENT);
        });
    }

    /**
     * Something in the middle of a small room as well, though some are left bare.
     */
    private void furnishSmallCentre(Room room, int y)
    {
        long bits = hash(room, 16);
        if ((bits & 3) == 0)
            return;
        int x = centreX(room);
        int z = centreZ(room);
        boolean second = (bits & 4) != 0;
        switch (theme)
        {
            case ARCHIVE ->
            {
                placeOnFloor(x, y, z, TABLE, Compass.NORTH);
                if (canvas.get(x, y, z) == DaedalusMaterial.PROP)
                    placeOnFloor(x, y + 1, z, second ? BOOKS : CANDLES, Compass.SOUTH);
                if (isClear(x, z))
                {
                    placeOnFloor(x, y, z + 1, CHAIR, Compass.NORTH);
                    if (second)
                        placeOnFloor(x, y, z - 1, CHAIR, Compass.SOUTH);
                }
            }
            case GALLERY -> placeOnFloor(x, y, z, second ? STATUE : PLANTER, Compass.SOUTH);
            case STAIRWELL ->
            {
                if (second)
                    placeStatue(x, y, z, HOURGLASS, Compass.SOUTH);
                else
                    placeOnFloor(x, y, z, PLANTER, Compass.SOUTH);
            }
            case CISTERN -> placeOnFloor(x, y, z, second ? BASIN : BARREL, Compass.SOUTH);
            default ->
            {
                // Cells and chambers furnish their middles themselves.
            }
        }
    }

    /**
     * Desks between the pilasters with a chair before each, scroll racks over some, cabinets and books beside them, and sconces in between.
     */
    private void furnishArchiveWalls(Room room, int y)
    {
        int deskOffset = deskOffset(room);
        alongWalls(room, PILASTER_SPACING, deskOffset, (x, z, facing) ->
        {
            if (pick(x, z, 3, 3) == 0)
            {
                placeOnFloor(x, y, z, pick(x, z, 29, 3) == 0 ? CONSOLE : LECTERN, facing);
                return;
            }
            if (!placeOnFloor(x, y, z, DESK, facing))
                placeOnFloor(x, y, z, LEDGERS, facing);
            if (isClear(x, z))
                placeOnFloor(x + Compass.dx(facing), y, z + Compass.dz(facing), CHAIR, Compass.opposite(facing));
            if (pick(x, z, 19, 2) == 0)
                placeOnWall(x, y + 2, z, SCROLLS, facing);
        });
        alongWalls(room, PILASTER_SPACING, deskOffset - 1, (x, z, facing) ->
        {
            int roll = pick(x, z, 20, 3);
            if (roll < 2)
                placeOnFloor(x, y, z, roll == 0 ? CABINET : BOOKS, facing);
        });
        alongWalls(room, PILASTER_SPACING, deskOffset + 1, (x, z, facing) ->
        {
            if (pick(x, z, 13, 2) == 0)
                placeOnWall(x, y + 2, z, SCONCE, facing);
        });
    }

    /**
     * An altar in its circle where rooms are kept, and in the rougher themes broken monoliths round a shrine.
     */
    private void raiseRitual(Room room, int x, int y, int z)
    {
        if (theme == DaedalusTheme.WRITHING || theme == DaedalusTheme.WARREN)
        {
            relics.monolithRing(x, y, z, RITUAL_RING_RADIUS, 1, layout.roomHeight() - 1, hash(room, 13));
            relics.shrine(x, y, z);
        }
        else
            relics.ritualCircle(x, y, z);
    }

    /**
     * @param columnStep 0 for a room with no columns, 1 where they may stand at every lattice junction inside it, 2 for every other one as in vaults
     * @param smallest the shortest side of the room's floor that leaves room around the feature
     * @return whether a feature reaching this far round the point fits without touching a column
     */
    private boolean hasSpace(Room room, int x, int z, int reach, int columnStep, int smallest)
    {
        if (Math.min(maxX(room) - minX(room), maxZ(room) - minZ(room)) + 1 < smallest)
            return false;
        boolean columns = columnStep > 1 || columnStep == 1 && room.cellsWide() >= COLONNADE_CELLS && room.cellsDeep() >= COLONNADE_CELLS;
        if (!columns)
            return true;
        for (int i = room.minI() + columnStep; i <= room.maxI(); i += columnStep)
        {
            for (int j = room.minJ() + columnStep; j <= room.maxJ(); j += columnStep)
            {
                int columnX = cellX(i);
                int columnZ = cellZ(j);
                if (columnX >= x - reach && columnX - 1 <= x + reach && columnZ >= z - reach && columnZ - 1 <= z + reach)
                    return false;
            }
        }
        return true;
    }

    /**
     * Desks stand halfway between the pilasters of {@link #detailWalls}, so a desk is never refused for one and the chair in front of it always has its desk.
     */
    private int deskOffset(Room room)
    {
        int pilasterStep = Math.floorMod(PILASTER_SPACING / 2 - (int) (hash(room, 9) & 1), PILASTER_SPACING);
        return Math.floorMod(-(pilasterStep + PILASTER_SPACING / 2), PILASTER_SPACING);
    }

    private void runPipes(int x, int y, int z, int top, int facing)
    {
        if (pick(x, z, 9, 2) != 0)
            return;
        for (int height = y; height <= top; height++)
            placeOnWall(x, height, z, PIPES, facing);
    }

    private DaedalusProp hungFromChamber(long bits)
    {
        if (theme != DaedalusTheme.WRITHING)
            return CAGE;
        return switch ((int) (bits >>> 2 & 3))
        {
            case 0 -> CENSER;
            case 1 -> LANTERN;
            default -> CAGE;
        };
    }

    private void hangCages(Room room)
    {
        for (int j = room.minJ(); j <= room.maxJ(); j++)
        {
            for (int i = room.minI(); i <= room.maxI(); i++)
            {
                long bits = DaedalusHash.mix(room.variant(), i, j, 14);
                if (((i + j) & 1) == 0 && DaedalusHash.pick(bits, 2) == 0)
                    hangFromTop(cellX(i) + LANE_CENTRE + 1, cellZ(j) + LANE_CENTRE, roomTopAir(room), 1, hungFromChamber(bits));
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
     * A cot against the first side of the cell with no door, and sometimes something left in a corner.
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
            var keepsake = switch ((int) (bits >>> 4 & 3))
            {
                case 0 -> CRATES;
                case 1 -> REMAINS;
                case 2 -> BRAZIER;
                default -> (bits >>> 9 & 1) == 0 ? AUTOMATON : URN;
            };
            if ((bits & 8) == 0 && isFreeOfCellLanes(room, CELL_CORNER, CELL_CORNER))
                placeCellProp(x + CELL_CORNER, y, z + CELL_CORNER, keepsake, Compass.NORTH);
            var leftover = switch ((int) (bits >>> 6 & 3))
            {
                case 0 -> BARREL;
                case 1 -> TRUNK;
                case 2 -> BOOKS;
                default -> CANDLES;
            };
            int near = LANE_CENTRE - 1;
            int[][] spots = { { near, CELL_CORNER }, { CELL_CORNER, near }, { near, near } };
            for (int[] spot : spots)
            {
                if (isFreeOfCellLanes(room, spot[0], spot[1]))
                {
                    placeCellProp(x + spot[0], y, z + spot[1], leftover, Compass.SOUTH);
                    break;
                }
            }
            cobwebCorners(x + near, z + near, x + CELL_CORNER, z + CELL_CORNER, y + layout.roomHeight() - 2, 2);
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
            int roll = pick(x, z, 0, 9);
            if (roll < 3)
                placeOnFloor(x, y, z, GROWTH, facing);
            else if (roll == 3)
                placeOnFloor(x, y, z, REMAINS, facing);
            else if (roll == 4)
                relics.monolith(x, y, z, 1 + pick(x, z, 6, layout.roomHeight() - 2));
            else if (roll == 5)
                placeOnFloor(x, y, z, CANDLES, facing);
        });
        cobwebCorners(minX(room), minZ(room), maxX(room), maxZ(room), roomTopAir(room), 2);
        int x = centreX(room);
        int z = centreZ(room);
        boolean ritual = isLarge(room) && hasSpace(room, x, z, RoomRelics.RITUAL_REACH, 0, RITUAL_ROOM) && DaedalusHash.pick(hash(room, 10), RITUAL_ODDS) != 0;
        if (ritual)
            raiseRitual(room, x, y, z);
        if (room.cellsWide() * room.cellsDeep() > 1)
            furnishFloor(room, y, ritual ? RITUAL_CLEARING : -1);
        hangCages(room);
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
            placeOnFloor(startX, y, startZ, stacksEnd(startX, startZ), alongX ? Compass.WEST : Compass.NORTH);
            placeOnFloor(endX, y, endZ, stacksEnd(endX, endZ), alongX ? Compass.EAST : Compass.SOUTH);
        }
        cobwebCorners(minX(room), minZ(room), maxX(room), maxZ(room), roomTopAir(room), 2);

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

    private static DaedalusProp stacksEnd(int x, int z)
    {
        return switch (pick(x, z, 7, 4))
        {
            case 0 -> LECTERN;
            case 1 -> BOOKS;
            case 2 -> CABINET;
            default -> LEDGERS;
        };
    }

    /**
     * Faceless busts on plinths and empty frames between the columns, under a chandelier in every inner bay.
     */
    private void furnishColonnade(Room room)
    {
        int y = floorY(room.minK()) + 1;
        collectDoors(room, room.minK(), 1, DOOR_CLEARANCE);
        alongWalls(room, CELL, 2, (x, z, facing) -> placeStatue(x, y, z, pick(x, z, 8, 4) == 0 ? ORRERY : BUST, facing));
        alongWalls(room, CELL, 6, (x, z, facing) -> placeOnWall(x, y + 1, z, pick(x, z, 4, 3) == 0 ? MIRROR : FRAME, facing));
        alongWalls(room, CELL, 4, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
        alongWalls(room, CELL, 2, (x, z, facing) -> placeOnWall(x, y + 3, z, TAPESTRY, facing));
        alongWalls(room, CELL, 0, (x, z, facing) -> placeOnFloor(x, y, z, PLANTER, facing));
        placeSconces(room, y);
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
        int offset = (int) (hash(room, 5) & 3);
        alongWalls(room, VALVE_SPACING + 2, offset, (x, z, facing) ->
        {
            if (pick(x, z, 12, 2) == 0)
                placeOnWall(x, y + 1, z, VALVE, facing);
        });
        alongWalls(room, PIPE_SPACING, offset + 3, (x, z, facing) -> runPipes(x, y, z, roomTopAir(room), facing));
        alongWalls(room, GEAR_SPACING, offset + 6, (x, z, facing) ->
        {
            int roll = pick(x, z, 26, 3);
            if (roll < 2)
                placeOnWall(x, y + 2, z, roll == 0 ? WINCH : GAUGE, facing);
        });
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
        int offset = (int) (hash(room, 6) & 3);
        alongWalls(room, 5, offset, (x, z, facing) -> placeOnFloor(x, y, z, BENCH, facing));
        alongWalls(room, 10, offset + 2, (x, z, facing) -> placeOnFloor(x, y, z, pick(x, z, 21, 2) == 0 ? STATUE : PLANTER, facing));
        if (theme == DaedalusTheme.GALLERY || theme == DaedalusTheme.STAIRWELL)
            alongWalls(room, 10, offset, (x, z, facing) -> placeOnWall(x, y + 2, z, TAPESTRY, facing));
        placeSconces(room, y);
        int x = centreX(room);
        int z = centreZ(room);
        int relic = DaedalusHash.pick(hash(room, 11), VAULT_RELIC_ODDS + 1);
        boolean space = hasSpace(room, x, z, RoomRelics.RITUAL_REACH, 2, RITUAL_ROOM);
        if (space && relic == 0)
        {
            relics.ritualCircle(x, y, z);
            int radius = Math.min(Math.min(maxX(room) - minX(room), maxZ(room) - minZ(room)) / 2 - RING_MARGIN, RoomRelics.RITUAL_REACH + VAULT_RING_GAP);
            relics.monolithRing(x, y, z, radius, SHORTEST_VAULT_MONOLITH, TALLEST_VAULT_MONOLITH, hash(room, 13));
        }
        else if (space && relic == 1)
        {
            features.raiseEngine(x, y, z);
            features.ringOf(x, y, z, VAULT_PISTON_RING, PISTON);
            features.abandon(x, y, z, VAULT_PISTON_RING, hash(room, 34));
        }
        else
        {
            placeStatue(x, y, z, theme == DaedalusTheme.GALLERY ? BUST : URN, Compass.SOUTH);
            ringCentre(room, y);
        }

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
