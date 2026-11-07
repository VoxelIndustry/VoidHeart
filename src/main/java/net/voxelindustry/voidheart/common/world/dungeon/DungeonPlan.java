package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.DepthsRooms;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.DungeonKit;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.RoomCanvas;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.RoomKind;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.CELL;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.plane;

/**
 * Everything a dungeon holds, as a pure function of its blueprint. Rooms are drawn on their own, warped and stamped over rock, then the void
 * mangles the result. Cells on the way between doors are protected from all of it, which keeps every room reachable.
 */
public final class DungeonPlan
{
    public static final int SHELL = 2;

    private static final int STAMP_AIR = 0;
    private static final int STAMP_SOLID = 1;
    private static final int STAMP_WALKWAY = 2;
    private static final double WARP_FLOATING = 2.0;
    private static final double WARP_BURIED = 1.3;
    private static final double WARP_SCALE = 34;
    private static final double WARP_FADE_DISTANCE = 7;
    private static final double[] WARP_STRENGTHS = { 0, 0.5, 1, 1, 1.5 };
    private static final int FILL_LIGHT_SPACING = 7;
    private static final int RETURN_RIFT_DISTANCE = 2;
    private static final int CAVITY_LIGHT_SPACING = 9;
    private static final int LIGHT_EVERY_CELLS = 3;
    private static final double ENCLOSED_BRIDGE_CHANCE = 0.25;
    private static final int[] FRAGMENTS = { 2, 5, 14 };
    private static final int[] CRYSTAL_CLUSTERS = { 9, 28, 85 };
    private static final int[] CHESTS = { 1, 3, 8 };
    private static final double MEMORIAM_SHARE = 0.12;
    private static final double VEIN_SCALE = 30;
    private static final double VEIN_WIDTH = 0.022;
    private static final double BURIED_ROCK_LIMIT = 0.75;
    private static final double BURIED_CAVERNOUS_ROCK_LIMIT = 0.42;
    private static final double FLOATING_MASS_LIMIT = 0.78;
    private static final double FLOATING_SEAMOUNT_LIMIT = 0.5;
    private static final int TUNNEL_BITE_ONE_IN = 2;

    private final DungeonBlueprint blueprint;
    private final RandomSource random;
    private final int edge;
    private final DungeonGrid grid;
    private final BitSet safe;
    private final BitSet walkwayFloors;
    private final DungeonPlanner layout;
    private final List<DungeonTemplate.Placement> templates = new ArrayList<>();
    private final List<int[]> chests = new ArrayList<>();
    private final List<DungeonMobTable.Placement> mobs = new ArrayList<>();
    private final SimplexNoise warpX;
    private final SimplexNoise warpY;
    private final SimplexNoise warpZ;
    private int[] entry;
    private int[] returnRift;

    private DungeonPlan(DungeonBlueprint blueprint)
    {
        this.blueprint = blueprint;
        random = RandomSource.create(blueprint.seed());
        edge = blueprint.size().edge();
        grid = new DungeonGrid(edge, edge, edge);
        safe = new BitSet(edge * edge * edge);
        walkwayFloors = new BitSet(edge * edge * edge);
        warpX = new SimplexNoise(random);
        warpY = new SimplexNoise(random);
        warpZ = new SimplexNoise(random);

        layout = new DungeonPlanner(blueprint, random);

        fillRock();
        layout.rooms.forEach(this::stampUnderRoomRock);
        layout.rooms.forEach(this::stampRoom);
        layout.flats.values().forEach(this::stampCorridor);
        layout.stairs.forEach(this::stampStair);
        layout.rooms.forEach(this::stampDoorFrames);
        scatterFragments();
        layout.rooms.forEach(this::erode);
        growVeins();
        settleEntry();
        growCrystals();
        placeChests();
        if (isNatural())
            new DepthsReef(blueprint, grid, layout.rooms, this::isSafe, entry, random).grow();
        placeNests();
        spreadFillLight();
        sealShell();
        placeMobs();
    }

    public static DungeonPlan generate(DungeonBlueprint blueprint)
    {
        return new DungeonPlan(blueprint);
    }

    public DungeonBlueprint blueprint()
    {
        return blueprint;
    }

    public DungeonGrid grid()
    {
        return grid;
    }

    public List<DungeonRoom> rooms()
    {
        return layout.rooms;
    }

    /**
     * Where each corridor flight's top step lies, across its width, level with the floor it climbs to.
     */
    public List<StairTop> stairTops()
    {
        var tops = new ArrayList<StairTop>();
        for (var stair : layout.stairs)
        {
            var up = stair.ascending();
            boolean alongX = up.getAxis() == Direction.Axis.X;
            int along = up.getAxisDirection() == Direction.AxisDirection.POSITIVE ? CELL : 0;
            for (int lateral = 1; lateral < CELL; lateral++)
                tops.add(new StairTop(plane(stair.i()) + (alongX ? along : lateral), plane(stair.j()) + CELL, plane(stair.k()) + (alongX ? lateral : along), up));
        }
        return tops;
    }

    public record StairTop(int x, int y, int z, Direction up)
    {
    }

    public List<DungeonTemplate.Placement> templates()
    {
        return templates;
    }

    public List<int[]> chests()
    {
        return chests;
    }

    public List<DungeonMobTable.Placement> mobs()
    {
        return mobs;
    }

    /**
     * Where players arrive: x, y, z of the air cell they stand in, local to the dungeon's corner.
     */
    public int[] entry()
    {
        return entry;
    }

    DungeonPlanner layout()
    {
        return layout;
    }

    /**
     * Where the way back floats: x, y, z of its cell, local to the dungeon's corner.
     */
    public int[] returnRift()
    {
        return returnRift;
    }

    public DungeonRoom entryRoom()
    {
        return layout.entry;
    }

    /**
     * Rock with life in it rather than a building: corridors are tunnels, and nothing stands on columns or bridges.
     */
    private boolean isNatural()
    {
        return blueprint.theme() == DungeonTheme.DEPTHS;
    }

    private DungeonMaterial built(DungeonMaterial material)
    {
        return isNatural() ? ROCK_NATIVE : material;
    }

    private int index(int x, int y, int z)
    {
        return (y * edge + z) * edge + x;
    }

    private boolean isSafe(int x, int y, int z)
    {
        return safe.get(index(x, y, z));
    }

    private void markSafe(int x, int y, int z)
    {
        if (grid.isInside(x, y, z))
            safe.set(index(x, y, z));
    }

    private void setUnlessSafe(int x, int y, int z, DungeonMaterial material)
    {
        if (isInner(x, y, z) && !isSafe(x, y, z))
            grid.set(x, y, z, material);
    }

    /**
     * The floor of a room above can sag into a corridor, and must stay when the corridor is carved.
     * A pool dug that deep keeps its top layer of water over a new bottom, at the cost of a block of headroom.
     */
    private void carveCorridor(int x, int y, int z, boolean topLayer)
    {
        if (walkwayFloors.get(index(x, y, z)))
            return;

        boolean underWater = topLayer && grid.material(x, y + 1, z) == FLUID;
        grid.set(x, y, z, underWater ? POOL_BED : AIR);
    }

    private boolean isInner(int x, int y, int z)
    {
        return x >= SHELL && y >= SHELL && z >= SHELL && x < edge - SHELL && y < edge - SHELL && z < edge - SHELL;
    }

    private void fillRock()
    {
        var large = new SimplexNoise(random);
        var fine = new SimplexNoise(random);
        double scale = Math.sqrt(edge / 64.0);
        for (int y = SHELL; y < edge - SHELL; y++)
        {
            for (int z = SHELL; z < edge - SHELL; z++)
            {
                for (int x = SHELL; x < edge - SHELL; x++)
                {
                    double low = large.get(x / 16.0, y / 16.0, z / 16.0);
                    double detail = fine.get(x / 6.0, y / 6.0, z / 6.0);
                    boolean rock = blueprint.isBuried()
                                   ? low + detail * 0.4 < (isNatural() ? BURIED_CAVERNOUS_ROCK_LIMIT : BURIED_ROCK_LIMIT)
                                   : isCrust(x, y, z, low, detail, scale);
                    if (rock)
                        grid.set(x, y, z, ROCK);
                }
            }
        }
    }

    private boolean isCrust(int x, int y, int z, double low, double detail, double scale)
    {
        double thickness = (low * 0.5 + 0.5 + detail * 0.2) * scale;
        int fromWall = Math.min(Math.min(x, edge - 1 - x), Math.min(z, edge - 1 - z)) - SHELL;
        int fromFloor = y - SHELL;
        int fromCeiling = edge - 1 - SHELL - y;
        return fromFloor < 3 + thickness * 5
               || fromCeiling < 1 + thickness * 7
               || fromWall < 1 + thickness * 6
               || low > (isNatural() ? FLOATING_SEAMOUNT_LIMIT : FLOATING_MASS_LIMIT) && detail > -0.2;
    }

    /**
     * Each ruin keeps a piece of the ground it was torn from: an inverted hill when floating, a collar of native rock when buried.
     */
    private void stampUnderRoomRock(DungeonRoom room)
    {
        if (blueprint.isBuried())
        {
            stampNativeCollar(room);
            return;
        }

        boolean onPilotis = hasPilotis(room);
        int plate = onPilotis ? room.minY() - CELL : room.minY();
        var noise = new SimplexNoise(random);
        for (int z = room.minZ(); z <= room.maxZ(); z++)
        {
            for (int x = room.minX(); x <= room.maxX(); x++)
            {
                if (onPilotis)
                    stampPiloti(room, x, z, plate);

                int fromEdge = Math.min(Math.min(x - room.minX(), room.maxX() - x), Math.min(z - room.minZ(), room.maxZ() - z));
                double depth = Math.min(fromEdge * 0.9, 18) * (0.65 + 0.35 * noise.get(x / 7.0, z / 7.0));
                for (int down = 1; down <= depth + 1; down++)
                {
                    int y = plate - down;
                    if (isInner(x, y, z))
                        grid.set(x, y, z, down <= 3 ? ROCK_NATIVE : ROCK);
                }
            }
        }
    }

    /**
     * Built rooms stand on columns over a plate of their own, the way the keep floats in massive_pocket.
     */
    private boolean hasPilotis(DungeonRoom room)
    {
        return room.kind.template() == null && room.j0 > 0 && blueprint.theme() != DungeonTheme.FOREST && blueprint.theme() != DungeonTheme.MYCOSIS && !isNatural();
    }

    private void stampPiloti(DungeonRoom room, int x, int z, int plate)
    {
        if (!isInner(x, plate, z))
            return;

        grid.set(x, plate, z, x % CELL == 0 || z % CELL == 0 ? FLOOR_TRIM : FLOOR);
        if ((x - room.minX()) % (CELL * 2) == 0 && (z - room.minZ()) % (CELL * 2) == 0)
        {
            for (int y = plate + 1; y < room.minY(); y++)
                grid.set(x, y, z, COLUMN, Direction.UP);
        }
    }

    private void stampNativeCollar(DungeonRoom room)
    {
        int reach = 3 + random.nextInt(3);
        for (int y = room.minY() - reach; y <= room.maxY() + reach; y++)
        {
            for (int z = room.minZ() - reach; z <= room.maxZ() + reach; z++)
            {
                for (int x = room.minX() - reach; x <= room.maxX() + reach; x++)
                {
                    if (grid.material(x, y, z) == ROCK && random.nextInt(4) != 0)
                        grid.set(x, y, z, ROCK_NATIVE);
                }
            }
        }
    }

    private void stampRoom(DungeonRoom room)
    {
        var canvas = new RoomCanvas(room.maxX() - room.minX(), room.maxY() - room.minY(), room.maxZ() - room.minZ(), RandomSource.create(random.nextLong()), blueprint, false);
        room.kind.builder().accept(canvas);

        for (var door : room.doors)
        {
            int doorX = door.x() - room.minX();
            int doorZ = door.z() - room.minZ();
            if (room.kind.isLeaf())
                canvas.carveDoorway(doorX, doorZ, door.into());
            else
                canvas.carveWalkway(doorX, doorZ);
        }
        if (room == layout.entry)
            canvas.carveWalkway(canvas.w / 2, canvas.d / 2);
        if (blueprint.theme() == DungeonTheme.FOREST)
            canvas.ensurePool();

        canvas.templates.forEach(placement -> templates.add(new DungeonTemplate.Placement(placement.template(),
                room.minX() + placement.x(), room.minY() + placement.y(), room.minZ() + placement.z())));

        stampWarped(canvas, room);
    }

    /**
     * Every doorway is framed: posts of the theme's stone under a lintel of its metal, which reads as a gate even where the walls have gone.
     */
    private void stampDoorFrames(DungeonRoom room)
    {
        if (DepthsRooms.isCave(room.kind.name()))
            return;

        for (var door : room.doors)
        {
            for (int lateral = -2; lateral <= 2; lateral++)
            {
                int x = door.x() + (door.into().getAxis() == Direction.Axis.Z ? lateral : 0);
                int z = door.z() + (door.into().getAxis() == Direction.Axis.X ? lateral : 0);
                if (Math.abs(lateral) == 2)
                {
                    for (int y = 1; y < CELL; y++)
                        setUnlessSafe(x, door.y() + y, z, WALL_TRIM);
                }
                setUnlessSafe(x, door.y() + CELL, z, Math.abs(lateral) == 2 ? WALL_TRIM : METAL_CUT);
            }
        }
    }

    private void stampWarped(RoomCanvas canvas, DungeonRoom room)
    {
        boolean rigid = canvas.rigid || room.kind.template() != null || canvas.h <= CELL;
        double amplitude = rigid ? 0 : (blueprint.isBuried() ? WARP_BURIED : WARP_FLOATING) * WARP_STRENGTHS[random.nextInt(WARP_STRENGTHS.length)];
        double[][] fade = warpFade(canvas);
        int rawHeight = canvas.h + 1 + RoomCanvas.BELOW;

        // Air first so that displaced solids never leave holes in walls, then walkways last so that displaced solids never block them
        for (int pass = STAMP_AIR; pass <= STAMP_WALKWAY; pass++)
        {
            for (int rawY = 0; rawY < rawHeight; rawY++)
            {
                int y = rawY - RoomCanvas.BELOW;
                int worldY = room.minY() + y;
                for (int z = 0; z <= canvas.d; z++)
                {
                    int worldZ = room.minZ() + z;
                    for (int x = 0; x <= canvas.w; x++)
                    {
                        short cell = canvas.rawCell(x, rawY, z);
                        var material = DungeonCell.material(cell);
                        if (material == UNSET || pass != stampPass(canvas, x, y, z, material))
                            continue;

                        int worldX = room.minX() + x;
                        double strength = amplitude * fade[x][z];
                        // Walkways only ever move up and down, since any sideways shear could open a gap in them
                        boolean shears = pass != STAMP_WALKWAY;
                        int targetX = worldX + (shears ? (int) Math.round(strength * warpX.get(worldY / WARP_SCALE, worldZ / WARP_SCALE)) : 0);
                        int shiftY = (int) Math.round(strength * warpY.get(worldX / WARP_SCALE, worldZ / WARP_SCALE));
                        // Corridors run a cell away from a room's floor: more than a block of sag would close their headroom
                        int targetY = worldY + (shears ? shiftY : Math.clamp(shiftY, -1, 1));
                        int targetZ = worldZ + (shears ? (int) Math.round(strength * warpZ.get(worldY / WARP_SCALE, worldX / WARP_SCALE)) : 0);
                        if (!isInner(targetX, targetY, targetZ) || pass != STAMP_WALKWAY && isSafe(targetX, targetY, targetZ))
                            continue;

                        grid.setCell(targetX, targetY, targetZ, cell);
                        if (pass == STAMP_WALKWAY && y == 0)
                            walkwayFloors.set(index(targetX, targetY, targetZ));
                        if (pass == STAMP_WALKWAY || material == FLUID || material == POOL_BED)
                            markSafe(targetX, targetY, targetZ);
                        if (material == FLUID)
                            protectAround(targetX, targetY, targetZ);
                    }
                }
            }
        }
    }

    private static int stampPass(RoomCanvas canvas, int x, int y, int z, DungeonMaterial material)
    {
        if (canvas.isWalkway(x, y, z))
            return STAMP_WALKWAY;
        return material == AIR ? STAMP_AIR : STAMP_SOLID;
    }

    private void protectAround(int x, int y, int z)
    {
        for (var side : Direction.values())
        {
            if (side != Direction.UP)
                markSafe(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
        }
    }

    private double[][] warpFade(RoomCanvas canvas)
    {
        double[][] fade = new double[canvas.w + 1][canvas.d + 1];
        for (int x = 0; x <= canvas.w; x++)
        {
            for (int z = 0; z <= canvas.d; z++)
            {
                double strength = 1;
                for (var spot : canvas.calmSpots)
                {
                    double distance = Math.max(0, Math.hypot(x - spot[0], z - spot[1]) - spot[2]);
                    strength = Math.min(strength, Math.min(1, distance / WARP_FADE_DISTANCE));
                }
                fade[x][z] = strength;
            }
        }
        return fade;
    }

    private void stampCorridor(DungeonPlanner.Flat flat)
    {
        int x0 = plane(flat.i);
        int y0 = plane(flat.j);
        int z0 = plane(flat.k);
        boolean enclosed = blueprint.isBuried() || isNatural() || random.nextDouble() < ENCLOSED_BRIDGE_CHANCE;

        for (int z = z0; z <= z0 + CELL; z++)
        {
            for (int x = x0; x <= x0 + CELL; x++)
            {
                boolean gridLine = x == x0 || x == x0 + CELL || z == z0 || z == z0 + CELL;
                if (!isSafe(x, y0, z))
                    grid.set(x, y0, z, gridLine ? FLOOR_TRIM : FLOOR);
                if (enclosed)
                    setUnlessSafe(x, y0 + CELL, z, built(CEILING));
            }
        }

        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (flat.openings.contains(side))
                continue;

            for (int along = 0; along <= CELL; along++)
            {
                int x = side.getAxis() == Direction.Axis.X ? (side == Direction.EAST ? x0 + CELL : x0) : x0 + along;
                int z = side.getAxis() == Direction.Axis.Z ? (side == Direction.SOUTH ? z0 + CELL : z0) : z0 + along;
                if (enclosed)
                {
                    boolean pilaster = along == 0 || along == CELL;
                    for (int y = 1; y < CELL; y++)
                        setUnlessSafe(x, y0 + y, z, built(pilaster ? WALL_TRIM : WALL));
                }
                else
                    setUnlessSafe(x, y0 + 1, z, FENCE);
            }
        }

        for (int y = 1; y < CELL; y++)
        {
            for (int z = 1; z < CELL; z++)
            {
                for (int x = 1; x < CELL; x++)
                {
                    carveCorridor(x0 + x, y0 + y, z0 + z, y == CELL - 1);
                    markSafe(x0 + x, y0 + y, z0 + z);
                }
            }
        }
        for (int z = 1; z < CELL; z++)
        {
            for (int x = 1; x < CELL; x++)
                markSafe(x0 + x, y0, z0 + z);
        }
        flat.openings.forEach(side -> openThreshold(x0, y0, z0, side));
        if (isNatural())
        {
            biteTunnel(x0, y0, z0);
            return;
        }
        if (enclosed)
            stampCorridorRib(flat, x0, y0, z0);

        if ((flat.i + flat.j + flat.k) % LIGHT_EVERY_CELLS == 0)
        {
            if (enclosed)
                grid.set(x0 + 1, y0 + CELL - 1, z0 + 1, LANTERN, Direction.DOWN, true);
            else
                cornerLamp(flat, x0, y0, z0);
        }
    }

    private void biteTunnel(int x0, int y0, int z0)
    {
        if (random.nextInt(TUNNEL_BITE_ONE_IN) == 0)
            biteAt(x0 + CELL / 2 + random.nextInt(3) - 1, y0 + CELL - 1, z0 + CELL / 2 + random.nextInt(3) - 1, 3 + random.nextInt(2), AIR, warpX, null);
    }

    /**
     * The stairs and slab arching over the temple's passages, on straight stretches where they cannot catch a turning player's head.
     */
    private void stampCorridorRib(DungeonPlanner.Flat flat, int x0, int y0, int z0)
    {
        boolean alongX = flat.openings.equals(java.util.Set.of(Direction.EAST, Direction.WEST));
        boolean alongZ = flat.openings.equals(java.util.Set.of(Direction.NORTH, Direction.SOUTH));
        if (!alongX && !alongZ)
            return;

        int y = y0 + CELL - 1;
        var firstSide = alongX ? Direction.NORTH : Direction.WEST;
        for (int lateral = 1; lateral < CELL; lateral++)
        {
            int x = x0 + (alongX ? CELL / 2 : lateral);
            int z = z0 + (alongX ? lateral : CELL / 2);
            if (lateral == 1)
                grid.set(x, y, z, STAIRS, firstSide, true);
            else if (lateral == CELL - 1)
                grid.set(x, y, z, STAIRS, firstSide.getOpposite(), true);
            else
                grid.set(x, y, z, SLAB, Direction.DOWN, true);
        }
    }

    private void cornerLamp(DungeonPlanner.Flat flat, int x0, int y0, int z0)
    {
        if (!flat.openings.contains(Direction.NORTH) && !flat.openings.contains(Direction.WEST))
        {
            grid.set(x0, y0 + 1, z0, FENCE);
            grid.set(x0, y0 + 2, z0, LANTERN);
        }
    }

    private void openThreshold(int x0, int y0, int z0, Direction side)
    {
        for (int lateral = 1; lateral < CELL; lateral++)
        {
            int x = side.getAxis() == Direction.Axis.X ? (side == Direction.EAST ? x0 + CELL : x0) : x0 + lateral;
            int z = side.getAxis() == Direction.Axis.Z ? (side == Direction.SOUTH ? z0 + CELL : z0) : z0 + lateral;
            grid.set(x, y0, z, FLOOR);
            markSafe(x, y0, z);
            for (int y = 1; y < CELL; y++)
            {
                grid.set(x, y0 + y, z, AIR);
                markSafe(x, y0 + y, z);
            }
        }
    }

    private void stampStair(DungeonPlanner.Stair stair)
    {
        int x0 = plane(stair.i());
        int y0 = plane(stair.j());
        int z0 = plane(stair.k());
        var up = stair.ascending();
        boolean alongX = up.getAxis() == Direction.Axis.X;
        boolean enclosed = blueprint.isBuried() || isNatural() || random.nextBoolean();
        int top = CELL * 2;

        for (int along = 0; along <= CELL; along++)
        {
            int offset = up.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : CELL - along;
            for (int lateral = 0; lateral <= CELL; lateral++)
            {
                int x = x0 + (alongX ? offset : lateral);
                int z = z0 + (alongX ? lateral : offset);
                boolean wall = lateral == 0 || lateral == CELL;
                stampStairColumn(x, y0, z, along, wall, enclosed, top, up);
            }
        }
    }

    private void stampStairColumn(int x, int y0, int z, int along, boolean wall, boolean enclosed, int top, Direction up)
    {
        if (wall)
        {
            if (enclosed)
            {
                for (int y = 0; y <= top; y++)
                    setUnlessSafe(x, y0 + y, z, built(WALL));
            }
            else
            {
                for (int y = 0; y <= along; y++)
                    setUnlessSafe(x, y0 + y, z, WALL);
                setUnlessSafe(x, y0 + along + 1, z, FENCE);
            }
            return;
        }

        for (int y = 0; y < along; y++)
            grid.set(x, y0 + y, z, y == 0 ? FLOOR : built(WALL));

        // The top step is level with the upper floor, so the flight is walked onto it rather than jumped
        if (along == 0)
            grid.set(x, y0 + along, z, FLOOR);
        else
            grid.set(x, y0 + along, z, STAIRS, up);
        markSafe(x, y0 + along, z);

        for (int y = along + 1; y < top; y++)
        {
            boolean endWall = along == 0 && y >= CELL || along == CELL && y < CELL;
            if (endWall)
            {
                if (enclosed)
                    setUnlessSafe(x, y0 + y, z, built(WALL));
                continue;
            }
            carveCorridor(x, y0 + y, z, y == top - 1);
            if (y <= along + RoomCanvas.WALKWAY_HEIGHT)
                markSafe(x, y0 + y, z);
        }
        if (enclosed)
            setUnlessSafe(x, y0 + top, z, built(CEILING));
    }

    /**
     * Whole pieces of the ruined world, tilted or upside down, torn from whatever they were attached to.
     */
    private void scatterFragments()
    {
        var kinds = DungeonKit.of(blueprint.theme()).rooms().stream().filter(kind -> !kind.isLeaf()).toList();
        int count = FRAGMENTS[blueprint.size().ordinal()] / (blueprint.isBuried() ? 2 : 1);
        for (int fragment = 0; fragment < count; fragment++)
            scatterFragment(kinds.get(random.nextInt(kinds.size())));
    }

    private void scatterFragment(RoomKind kind)
    {
        int width = CELL * (2 + random.nextInt(2));
        int height = CELL * (1 + random.nextInt(2));
        int depth = CELL * (2 + random.nextInt(2));
        var canvas = new RoomCanvas(width, height, depth, RandomSource.create(random.nextLong()), blueprint, true);
        kind.builder().accept(canvas);

        double radius = Math.sqrt(width * width + height * height + depth * depth) / 2 + 1;
        int reach = (int) Math.ceil(radius);
        if (edge - SHELL * 2 <= reach * 2)
            return;

        for (int attempt = 0; attempt < 24; attempt++)
        {
            int cx = SHELL + reach + random.nextInt(edge - SHELL * 2 - reach * 2);
            int cy = SHELL + reach + random.nextInt(edge - SHELL * 2 - reach * 2);
            int cz = SHELL + reach + random.nextInt(edge - SHELL * 2 - reach * 2);
            if (blueprint.isBuried() || isClear(cx, cy, cz, reach))
            {
                stampTilted(canvas, cx, cy, cz, reach);
                return;
            }
        }
    }

    private boolean isClear(int cx, int cy, int cz, int reach)
    {
        for (int y = cy - reach; y <= cy + reach; y += 2)
        {
            for (int z = cz - reach; z <= cz + reach; z += 2)
            {
                for (int x = cx - reach; x <= cx + reach; x += 2)
                {
                    if (isSafe(x, y, z) || isBuilt(grid.material(x, y, z)))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean isBuilt(DungeonMaterial material)
    {
        return !material.isOpen() && material != ROCK && material != ROCK_NATIVE;
    }

    private void stampTilted(RoomCanvas canvas, int cx, int cy, int cz, int reach)
    {
        boolean upsideDown = random.nextInt(3) == 0;
        double angle = upsideDown ? 0 : Math.toRadians(14 + random.nextInt(32)) * (random.nextBoolean() ? 1 : -1);
        double heading = random.nextDouble() * Math.PI * 2;
        double axisX = Math.cos(heading);
        double axisZ = Math.sin(heading);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double halfW = canvas.w / 2.0;
        double halfH = canvas.h / 2.0;
        double halfD = canvas.d / 2.0;

        for (int y = -reach; y <= reach; y++)
        {
            for (int z = -reach; z <= reach; z++)
            {
                for (int x = -reach; x <= reach; x++)
                {
                    double along = x * axisX + z * axisZ;
                    double acrossX = x - along * axisX;
                    double acrossZ = z - along * axisZ;
                    double sx = along * axisX + acrossX * cos - y * sin * -axisZ;
                    double sy = y * cos + (acrossX * -axisZ + acrossZ * axisX) * sin;
                    double sz = along * axisZ + acrossZ * cos - y * sin * axisX;

                    int fromX = (int) Math.round(sx + halfW);
                    int fromY = (int) Math.round((upsideDown ? -sy : sy) + halfH);
                    int fromZ = (int) Math.round(sz + halfD);
                    if (fromX < 0 || fromZ < 0 || fromY < 0 || fromX > canvas.w || fromY > canvas.h || fromZ > canvas.d)
                        continue;

                    short cell = canvas.rawCell(fromX, fromY + RoomCanvas.BELOW, fromZ);
                    var material = DungeonCell.material(cell);
                    if (material == UNSET)
                        continue;

                    int tx = cx + x;
                    int ty = cy + y;
                    int tz = cz + z;
                    if (!isInner(tx, ty, tz) || isSafe(tx, ty, tz))
                        continue;
                    if (upsideDown)
                        grid.setCell(tx, ty, tz, DungeonCell.upsideDown(cell));
                    else if (material.isCube() || material == AIR)
                        grid.setCell(tx, ty, tz, cell);
                    else if (material != FLUID)
                        grid.set(tx, ty, tz, AIR);
                }
            }
        }
    }

    /**
     * Floating ruins lose their walls and roofs to the void. Buried ones are slowly swallowed, rock pushing in.
     */
    private void erode(DungeonRoom room)
    {
        var bite = blueprint.isBuried() ? ROCK : AIR;
        int bites = 2 + random.nextInt(3) + room.width() + room.depth();
        var noise = new SimplexNoise(random);
        for (int i = 0; i < bites; i++)
        {
            int x = pickEdge(room.minX(), room.maxX());
            int z = pickEdge(room.minZ(), room.maxZ());
            int y = room.minY() + random.nextInt(room.maxY() - room.minY() + 1);
            int radius = 2 + random.nextInt(2 + room.width());
            biteAt(x, y, z, Math.min(radius, 8), bite, noise, blueprint.isBuried() ? AIR : null);
        }

        if (!blueprint.isBuried() && random.nextInt(2) == 0)
            collapseRoof(room, noise);
    }

    private int pickEdge(int min, int max)
    {
        return random.nextInt(3) == 0 ? min + random.nextInt(max - min + 1) : random.nextBoolean() ? min : max;
    }

    private void biteAt(int cx, int cy, int cz, int radius, DungeonMaterial material, SimplexNoise noise, DungeonMaterial only)
    {
        for (int y = cy - radius; y <= cy + radius; y++)
        {
            for (int z = cz - radius; z <= cz + radius; z++)
            {
                for (int x = cx - radius; x <= cx + radius; x++)
                {
                    double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
                    if (distance > radius * (0.7 + 0.4 * noise.get(x / 4.0, y / 4.0, z / 4.0)))
                        continue;
                    if (!isInner(x, y, z) || isSafe(x, y, z) || grid.material(x, y, z) == UNSET)
                        continue;
                    if (material == AIR || grid.material(x, y, z) != TEMPLATE)
                        grid.set(x, y, z, material);
                }
            }
        }
    }

    private void collapseRoof(DungeonRoom room, SimplexNoise noise)
    {
        for (int z = room.minZ(); z <= room.maxZ(); z++)
        {
            for (int x = room.minX(); x <= room.maxX(); x++)
            {
                if (noise.get(x / 6.0, z / 6.0) < 0.1)
                    continue;
                for (int y = room.maxY() - 3; y <= room.maxY() + 3; y++)
                {
                    if (isInner(x, y, z) && !isSafe(x, y, z) && grid.material(x, y, z) != UNSET)
                        grid.set(x, y, z, AIR);
                }
            }
        }
    }

    /**
     * Tubes of corrupted rock running through everything, where two noise fields both cross zero.
     */
    private void growVeins()
    {
        var first = new SimplexNoise(random);
        var second = new SimplexNoise(random);
        for (int y = SHELL; y < edge - SHELL; y++)
        {
            for (int z = SHELL; z < edge - SHELL; z++)
            {
                for (int x = SHELL; x < edge - SHELL; x++)
                {
                    double a = first.get(x / VEIN_SCALE, y / VEIN_SCALE, z / VEIN_SCALE);
                    if (Math.abs(a) > VEIN_WIDTH)
                        continue;
                    double b = second.get(x / VEIN_SCALE, y / VEIN_SCALE, z / VEIN_SCALE);
                    if (Math.abs(b) > VEIN_WIDTH || isSafe(x, y, z) || grid.material(x, y, z) == TEMPLATE)
                        continue;

                    double closeness = (Math.abs(a) + Math.abs(b)) / (VEIN_WIDTH * 2);
                    grid.set(x, y, z, veinMaterial(closeness));
                }
            }
        }
    }

    private DungeonMaterial veinMaterial(double closeness)
    {
        if (closeness > 0.7)
        {
            int roll = random.nextInt(24);
            if (roll == 0)
                return VEIN_MAW;
            if (roll < 3)
                return VEIN_BLOSSOM;
            return VEIN_WRITHING;
        }
        return VEIN;
    }

    private void settleEntry()
    {
        var room = layout.entry;
        int x = (room.minX() + room.maxX()) / 2;
        int z = (room.minZ() + room.maxZ()) / 2;
        int y = room.minY() + 1;
        for (int candidate = room.minY() + 3; candidate >= room.minY() - 3; candidate--)
        {
            if (!grid.material(x, candidate - 1, z).isPassable())
            {
                y = candidate;
                break;
            }
        }
        for (int up = 0; up < RoomCanvas.WALKWAY_HEIGHT; up++)
        {
            grid.set(x, y + up, z, AIR);
            markSafe(x, y + up, z);
        }
        entry = new int[] { x, y, z };
        placeReturnRift(x, y, z);
    }

    /**
     * Beside the arrival spot, floating at head height over walkable floor. Above the spot itself if nothing nearby will do.
     */
    private void placeReturnRift(int x, int y, int z)
    {
        returnRift = new int[] { x, y + 1, z };
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            int riftX = x + direction.getStepX() * RETURN_RIFT_DISTANCE;
            int riftZ = z + direction.getStepZ() * RETURN_RIFT_DISTANCE;
            if (canHoldRift(riftX, y, riftZ))
            {
                returnRift = new int[] { riftX, y + 1, riftZ };
                break;
            }
        }
        grid.set(returnRift[0], returnRift[1], returnRift[2], RETURN_RIFT);
        markSafe(returnRift[0], returnRift[1], returnRift[2]);
    }

    private boolean canHoldRift(int x, int y, int z)
    {
        if (x < SHELL || z < SHELL || x >= edge - SHELL || z >= edge - SHELL)
            return false;
        if (grid.material(x, y - 1, z).isPassable())
            return false;
        for (int up = 0; up < RoomCanvas.WALKWAY_HEIGHT; up++)
        {
            if (!grid.material(x, y + up, z).isOpen())
                return false;
        }
        return true;
    }

    private void growCrystals()
    {
        var spots = new ArrayList<int[]>();
        for (int y = SHELL; y < edge - SHELL; y++)
        {
            for (int z = SHELL; z < edge - SHELL; z++)
            {
                for (int x = SHELL; x < edge - SHELL; x++)
                {
                    if (grid.material(x, y, z).isOpen() && !isSafe(x, y, z))
                        collectSpot(spots, x, y, z);
                }
            }
        }
        if (spots.isEmpty())
            return;

        int clusters = (int) (CRYSTAL_CLUSTERS[blueprint.size().ordinal()] * (0.8 + blueprint.difficulty() * 0.1));
        int placedMemoriam = 0;
        for (int cluster = 0; cluster < clusters; cluster++)
        {
            boolean mustBeMemoriam = placedMemoriam == 0 && cluster == clusters - 1;
            var kind = mustBeMemoriam || random.nextDouble() < MEMORIAM_SHARE
                       ? DungeonCrystal.MEMORIAM
                       : random.nextBoolean() ? DungeonCrystal.AZURIUM : DungeonCrystal.RUBRUM;
            int grown = growCluster(spots.get(random.nextInt(spots.size())), kind);
            if (kind == DungeonCrystal.MEMORIAM)
                placedMemoriam += grown;
        }
        // A spot can already be taken by an earlier cluster, and Memoriam is found nowhere else
        for (var spot : spots)
        {
            if (placedMemoriam > 0)
                return;
            placedMemoriam += growCluster(spot, DungeonCrystal.MEMORIAM);
        }
    }

    private void collectSpot(List<int[]> spots, int x, int y, int z)
    {
        for (var side : Direction.values())
        {
            if (grid.material(x - side.getStepX(), y - side.getStepY(), z - side.getStepZ()).isAnchor())
            {
                spots.add(new int[] { x, y, z, side.ordinal() });
                return;
            }
        }
    }

    private int growCluster(int[] spot, DungeonCrystal kind)
    {
        int grown = 0;
        var facing = Direction.values()[spot[3]];
        int count = 1 + random.nextInt(4);
        for (int crystal = 0; crystal < count; crystal++)
        {
            int x = spot[0] + (crystal == 0 || facing.getAxis() == Direction.Axis.X ? 0 : random.nextInt(5) - 2);
            int y = spot[1] + (crystal == 0 || facing.getAxis() == Direction.Axis.Y ? 0 : random.nextInt(5) - 2);
            int z = spot[2] + (crystal == 0 || facing.getAxis() == Direction.Axis.Z ? 0 : random.nextInt(5) - 2);
            boolean anchored = grid.material(x - facing.getStepX(), y - facing.getStepY(), z - facing.getStepZ()).isAnchor();
            if (isInner(x, y, z) && grid.material(x, y, z).isOpen() && !isSafe(x, y, z) && anchored)
            {
                grid.set(x, y, z, DungeonMaterial.crystal(kind, random.nextInt(3)), facing);
                grown++;
            }
        }
        return grown;
    }

    /**
     * Fleshy growths on the rock of the rooms pests live in, on top of the mobs placed afterwards.
     */
    private void placeNests()
    {
        int wanted = DungeonMobTable.nests(blueprint.theme(), blueprint.size(), blueprint.difficulty());
        var rooms = layout.rooms.stream().filter(room -> room != layout.entry).toList();
        if (rooms.isEmpty())
            return;

        for (int nest = 0; nest < wanted; nest++)
        {
            var room = rooms.get(random.nextInt(rooms.size()));
            for (int attempt = 0; attempt < 40; attempt++)
            {
                int x = room.minX() + random.nextInt(room.maxX() - room.minX() + 1);
                int y = room.minY() + random.nextInt(room.maxY() - room.minY() + 1);
                int z = room.minZ() + random.nextInt(room.maxZ() - room.minZ() + 1);
                var facing = Direction.values()[random.nextInt(Direction.values().length)];
                if (isInner(x, y, z) && !isSafe(x, y, z) && grid.material(x, y, z).isOpen()
                    && grid.material(x - facing.getStepX(), y - facing.getStepY(), z - facing.getStepZ()).isAnchor())
                {
                    grid.set(x, y, z, NEST, facing);
                    break;
                }
            }
        }
    }

    private void placeMobs()
    {
        new DungeonMobPlacer(blueprint, grid, random, mobs, entry).place(layout.rooms, layout.entry, layout.flats.values());
    }

    private void placeChests()
    {
        int wanted = CHESTS[blueprint.size().ordinal()] + blueprint.difficulty() / 2;
        var candidates = new ArrayList<>(layout.rooms.stream().filter(room -> !room.kind.isLeaf() && room != layout.entry).toList());
        for (int i = candidates.size() - 1; i > 0; i--)
            java.util.Collections.swap(candidates, i, random.nextInt(i + 1));

        for (var room : candidates)
        {
            if (chests.size() >= wanted)
                return;
            placeChestIn(room);
        }
        if (chests.size() < wanted)
            placeChestIn(layout.entry);
    }

    private void placeChestIn(DungeonRoom room)
    {
        for (int attempt = 0; attempt < 80; attempt++)
        {
            int x = room.minX() + 1 + random.nextInt(room.maxX() - room.minX() - 1);
            int z = room.minZ() + 1 + random.nextInt(room.maxZ() - room.minZ() - 1);
            for (int y = room.minY() - 2; y <= room.minY() + 3; y++)
            {
                if (isChestSpot(x, y, z))
                {
                    grid.set(x, y, z, CHEST, facingAwayFromWall(x, y, z));
                    chests.add(new int[] { x, y, z });
                    return;
                }
            }
        }
    }

    private boolean isChestSpot(int x, int y, int z)
    {
        if (isSafe(x, y, z) || !grid.material(x, y, z).isOpen() || !grid.material(x, y + 1, z).isOpen())
            return false;

        var below = grid.material(x, y - 1, z);
        return below.isCube() && !below.isOpen() && below != TEMPLATE;
    }

    private Direction facingAwayFromWall(int x, int y, int z)
    {
        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (grid.material(x + side.getStepX(), y, z + side.getStepZ()).isCube() && !grid.material(x + side.getStepX(), y, z + side.getStepZ()).isOpen())
                return side.getOpposite();
        }
        return Direction.NORTH;
    }

    /**
     * The harder the dungeon, the less of it can be seen.
     */
    private void spreadFillLight()
    {
        int spacing = FILL_LIGHT_SPACING + blueprint.difficulty() / 2;
        for (var room : layout.rooms)
        {
            for (int y = room.minY() + 2; y < room.maxY(); y += spacing)
            {
                for (int z = room.minZ() + spacing / 2; z < room.maxZ(); z += spacing)
                {
                    for (int x = room.minX() + spacing / 2; x < room.maxX(); x += spacing)
                    {
                        if (grid.material(x, y, z).isOpen())
                            grid.set(x, y, z, FILL_LIGHT);
                    }
                }
            }
        }
        for (var flat : layout.flats.values())
        {
            int x = plane(flat.i) + CELL / 2;
            int y = plane(flat.j) + 2;
            int z = plane(flat.k) + CELL / 2;
            if ((flat.i + flat.k) % 2 == 0 && grid.material(x, y, z).isOpen())
                grid.set(x, y, z, FILL_LIGHT);
        }
        if (!blueprint.isBuried())
            lightCavity();
    }

    /**
     * Enough to make out the ruins across the cavity, which their own lights would not show from outside.
     */
    private void lightCavity()
    {
        for (int y = SHELL; y < edge - SHELL; y += CAVITY_LIGHT_SPACING)
        {
            for (int z = SHELL; z < edge - SHELL; z += CAVITY_LIGHT_SPACING)
            {
                for (int x = SHELL; x < edge - SHELL; x += CAVITY_LIGHT_SPACING)
                {
                    if (grid.material(x, y, z).isOpen())
                        grid.set(x, y, z, FILL_LIGHT);
                }
            }
        }
    }

    private void sealShell()
    {
        for (int y = 0; y < edge; y++)
        {
            for (int z = 0; z < edge; z++)
            {
                for (int x = 0; x < edge; x++)
                {
                    int depth = Math.min(Math.min(Math.min(x, edge - 1 - x), Math.min(y, edge - 1 - y)), Math.min(z, edge - 1 - z));
                    if (depth == 0)
                        grid.set(x, y, z, POCKET_WALL);
                    else if (depth < SHELL)
                        grid.set(x, y, z, ROCK);
                    else
                        x = edge - SHELL - 1;
                }
            }
        }
    }
}
