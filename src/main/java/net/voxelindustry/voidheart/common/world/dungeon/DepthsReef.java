package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.voxelindustry.voidheart.common.world.dungeon.rooms.DepthsRooms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Life on the sea floor of a Still Depths dungeon, grown once the void has finished mangling it, on surfaces that are really there.
 * Everything here can be walked through or stands off the walkways, so routes between rooms stay open.
 */
final class DepthsReef
{
    @FunctionalInterface
    interface CellTest
    {
        boolean test(int x, int y, int z);
    }

    // Chances per open cell on a surface
    private record Growth(double coral, double kelp, double fauna, double drift, double glow)
    {
    }

    private static final Growth BACKGROUND = new Growth(0.03, 0.012, 0.015, 0.03, 0.012);
    private static final Growth RUINS = new Growth(0.06, 0.015, 0.03, 0.06, 0.02);
    private static final Map<String, Growth> ROOM_GROWTH = Map.of(
            DepthsRooms.TRENCH, new Growth(0.12, 0.05, 0.1, 0.08, 0.03),
            DepthsRooms.REEF, new Growth(0.3, 0.03, 0.06, 0.05, 0.03),
            DepthsRooms.KELP_FOREST, new Growth(0.05, 0.22, 0.03, 0.12, 0.03),
            DepthsRooms.VENT_FIELD, new Growth(0.04, 0, 0.25, 0.03, 0.02),
            DepthsRooms.GROTTO, new Growth(0.1, 0.06, 0.05, 0.2, 0.08),
            DepthsRooms.WHALE_FALL, new Growth(0.03, 0.01, 0.06, 0.03, 0.02));

    private static final int[] VENTS_BY_SIZE = { 2, 7, 20 };
    private static final int VENT_DIFFICULTY_SHARE = 3;
    private static final int VENT_ROOM_WEIGHT = 4;
    private static final int VENT_ATTEMPTS_EACH = 40;
    private static final int MIN_VENT_SPACING = 4;
    private static final int MIN_DISTANCE_FROM_ENTRY = 10;
    private static final int MIN_CHIMNEY = 1;
    private static final int CHIMNEY_SPREAD = 4;
    private static final int JET_HEADROOM = 3;
    private static final int TUBE_WORM_RING = 3;
    private static final double TUBE_WORM_CHANCE = 0.45;

    private static final double ZONE_SCALE = 14;
    private static final double REEF_ZONE = 0.1;
    private static final double SILT_CHANCE = 0.85;
    private static final double CRUST_ZONE = 0.35;
    private static final double CRUST_CHANCE = 0.6;
    private static final double SAFE_CELL_SHARE = 0.5;
    private static final int MAX_KELP = 14;
    private static final int MIN_KELP = 3;
    private static final int MAX_DRIFT = 7;
    private static final int ROOM_MARGIN = 3;
    private static final int ENTRY_CLEARING = 6;

    private static final Direction[] FAUNA_SIDES = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);

    private final DungeonBlueprint blueprint;
    private final DungeonGrid grid;
    private final List<DungeonRoom> rooms;
    private final CellTest safe;
    private final int[] entry;
    private final RandomSource random;
    private final SimplexNoise zones;
    private final List<int[]> vents = new ArrayList<>();

    DepthsReef(DungeonBlueprint blueprint, DungeonGrid grid, List<DungeonRoom> rooms, CellTest safe, int[] entry, RandomSource random)
    {
        this.blueprint = blueprint;
        this.grid = grid;
        this.rooms = rooms;
        this.safe = safe;
        this.entry = entry;
        this.random = random;
        zones = new SimplexNoise(random);
    }

    void grow()
    {
        placeVents();
        settleSilt();
        for (var room : rooms)
        {
            var growth = ROOM_GROWTH.getOrDefault(room.kind.name(), RUINS);
            growIn(room.minX() - ROOM_MARGIN, room.minY() - ROOM_MARGIN, room.minZ() - ROOM_MARGIN,
                    room.maxX() + ROOM_MARGIN, room.maxY() + ROOM_MARGIN, room.maxZ() + ROOM_MARGIN, growth, false);
        }
        int last = grid.width - 1 - DungeonPlan.SHELL;
        growIn(DungeonPlan.SHELL, DungeonPlan.SHELL, DungeonPlan.SHELL, last, last, last, BACKGROUND, true);
    }

    /**
     * Bone is left bare: anything on it would break up the outline that makes a skeleton read as one.
     */
    private boolean isSurface(DungeonMaterial material)
    {
        return material != WHALE_BONE && (material.isAnchor() || material == FLOOR_TRIM);
    }

    private boolean isSafe(int x, int y, int z)
    {
        return !isInner(x, y, z) || safe.test(x, y, z);
    }

    private boolean isInner(int x, int y, int z)
    {
        int last = grid.width - 1 - DungeonPlan.SHELL;
        return x >= DungeonPlan.SHELL && y >= DungeonPlan.SHELL && z >= DungeonPlan.SHELL && x <= last && y <= last && z <= last;
    }

    private double zone(int x, int y, int z)
    {
        return zones.get(x / ZONE_SCALE, y / ZONE_SCALE, z / ZONE_SCALE);
    }

    /**
     * What sinks through a sea settles on everything that faces up.
     */
    private void settleSilt()
    {
        int last = grid.width - 1 - DungeonPlan.SHELL;
        for (int y = DungeonPlan.SHELL; y < last; y++)
        {
            for (int z = DungeonPlan.SHELL; z <= last; z++)
            {
                for (int x = DungeonPlan.SHELL; x <= last; x++)
                {
                    var material = grid.material(x, y, z);
                    if ((material == ROCK || material == ROCK_NATIVE) && grid.material(x, y + 1, z).isOpen() && random.nextDouble() < SILT_CHANCE)
                        grid.set(x, y, z, FLOOR);
                }
            }
        }
    }

    private void growIn(int x0, int y0, int z0, int x1, int y1, int z1, Growth growth, boolean patchy)
    {
        for (int y = Math.max(y0, DungeonPlan.SHELL); y <= Math.min(y1, grid.height - 1 - DungeonPlan.SHELL); y++)
        {
            for (int z = Math.max(z0, DungeonPlan.SHELL); z <= Math.min(z1, grid.depth - 1 - DungeonPlan.SHELL); z++)
            {
                for (int x = Math.max(x0, DungeonPlan.SHELL); x <= Math.min(x1, grid.width - 1 - DungeonPlan.SHELL); x++)
                {
                    if (grid.material(x, y, z).isOpen())
                        growAt(x, y, z, growth, patchy);
                }
            }
        }
    }

    private void growAt(int x, int y, int z, Growth growth, boolean patchy)
    {
        double zone = zone(x, y, z);
        if (patchy && zone < 0 || isNearEntry(x, y, z))
            return;

        double share = (isSafe(x, y, z) ? SAFE_CELL_SHARE : 1) * (zone > REEF_ZONE ? 1.5 : 1);
        if (isSurface(grid.material(x, y - 1, z)))
            growOnFloor(x, y, z, growth, share, zone);
        else if (isSurface(grid.material(x, y + 1, z)))
            growOnCeiling(x, y, z, growth, share);
        else
        {
            for (var side : FAUNA_SIDES)
            {
                if (isSurface(grid.material(x - side.getStepX(), y, z - side.getStepZ())))
                {
                    growOnWall(x, y, z, side, growth, share);
                    return;
                }
            }
        }
    }

    private boolean isNearEntry(int x, int y, int z)
    {
        return Math.abs(x - entry[0]) <= ENTRY_CLEARING && Math.abs(y - entry[1]) <= ENTRY_CLEARING && Math.abs(z - entry[2]) <= ENTRY_CLEARING;
    }

    private void growOnFloor(int x, int y, int z, Growth growth, double share, double zone)
    {
        double roll = random.nextDouble() / share;
        if (zone > CRUST_ZONE && random.nextDouble() < CRUST_CHANCE)
            grid.set(x, y - 1, z, CORAL_BLOCK);

        if (roll < growth.coral)
        {
            grid.set(x, y, z, CORAL_GROWTH, Direction.UP);
            return;
        }
        roll -= growth.coral;
        if (roll < growth.kelp)
        {
            standKelp(x, y, z);
            return;
        }
        roll -= growth.kelp;
        if (roll < growth.fauna)
        {
            placeFauna(x, y, z);
            return;
        }
        roll -= growth.fauna;
        if (roll < growth.glow)
            grid.set(x, y, z, GLOW_ALGAE, Direction.UP);
    }

    private void placeFauna(int x, int y, int z)
    {
        switch (random.nextInt(3))
        {
            case 0 -> grid.set(x, y, z, ANEMONE, Direction.UP);
            case 1 -> grid.set(x, y, z, BARNACLE, Direction.UP);
            default -> placeTubeWorms(x, y, z);
        }
    }

    private void placeTubeWorms(int x, int y, int z)
    {
        if (!grid.material(x, y + 1, z).isOpen() || !isInner(x, y + 1, z))
        {
            grid.set(x, y, z, ANEMONE, Direction.UP);
            return;
        }
        grid.set(x, y, z, TALL_PLANT);
        grid.set(x, y + 1, z, TALL_PLANT, Direction.DOWN, true);
    }

    /**
     * Stands up as if water still held it. Kept out of walkways, where a whole column would hide the way.
     */
    private void standKelp(int x, int y, int z)
    {
        int free = 0;
        while (free < MAX_KELP && grid.material(x, y + free, z).isOpen() && !isSafe(x, y + free, z))
            free++;
        if (free <= MIN_KELP)
            return;

        int height = MIN_KELP + random.nextInt(free - MIN_KELP);
        for (int up = 0; up < height; up++)
            grid.set(x, y + up, z, up == height - 1 ? KELP_TIP : KELP);
    }

    private void growOnCeiling(int x, int y, int z, Growth growth, double share)
    {
        double roll = random.nextDouble() / share;
        if (roll < growth.drift)
        {
            hangDriftAlgae(x, y, z);
            return;
        }
        roll -= growth.drift;
        if (roll < growth.coral)
        {
            grid.set(x, y, z, CORAL_GROWTH, Direction.DOWN);
            return;
        }
        roll -= growth.coral;
        if (roll < growth.glow)
            grid.set(x, y, z, GLOW_ALGAE, Direction.DOWN);
    }

    private void hangDriftAlgae(int x, int y, int z)
    {
        int free = 0;
        while (free < MAX_DRIFT && grid.material(x, y - free, z).isOpen() && !isSafe(x, y - free, z))
            free++;
        if (free == 0)
            return;

        int length = 1 + random.nextInt(free);
        for (int down = 0; down < length; down++)
            grid.set(x, y - down, z, down == length - 1 ? DRIFT_ALGAE_TIP : DRIFT_ALGAE);
    }

    private void growOnWall(int x, int y, int z, Direction side, Growth growth, double share)
    {
        double roll = random.nextDouble() / share;
        if (roll < growth.coral)
        {
            grid.set(x, y, z, CORAL_GROWTH, side);
            return;
        }
        roll -= growth.coral;
        if (roll < growth.fauna)
        {
            grid.set(x, y, z, random.nextBoolean() ? BARNACLE : ANEMONE, side);
            return;
        }
        roll -= growth.fauna;
        if (roll < growth.glow)
            grid.set(x, y, z, GLOW_ALGAE, side);
    }

    private void placeVents()
    {
        int wanted = VENTS_BY_SIZE[blueprint.size().ordinal()] * (VENT_DIFFICULTY_SHARE + blueprint.difficulty()) / (VENT_DIFFICULTY_SHARE + 1);
        var candidates = new ArrayList<DungeonRoom>();
        for (var room : rooms)
        {
            if (room.kind.template() != null)
                continue;
            boolean favored = room.kind.name().equals(DepthsRooms.VENT_FIELD) || room.kind.name().equals(DepthsRooms.TRENCH);
            for (int weight = favored ? VENT_ROOM_WEIGHT : 1; weight > 0; weight--)
                candidates.add(room);
        }
        if (candidates.isEmpty())
            return;

        for (int attempt = 0; attempt < wanted * VENT_ATTEMPTS_EACH && vents.size() < wanted; attempt++)
        {
            var room = candidates.get(random.nextInt(candidates.size()));
            int x = room.minX() + 1 + random.nextInt(room.maxX() - room.minX() - 1);
            int z = room.minZ() + 1 + random.nextInt(room.maxZ() - room.minZ() - 1);
            if (isFarFromOthers(x, z))
                tryVentAt(x, z, room);
        }
        if (vents.isEmpty())
            placeAnywhere();
    }

    /**
     * All of a dungeon's rooms can be taken up by hand built pockets or lie around the entry, and a Still Depths without a smoker would not be one.
     */
    private void placeAnywhere()
    {
        int last = grid.width - 1 - DungeonPlan.SHELL;
        int span = last - DungeonPlan.SHELL + 1;
        for (int attempt = 0; attempt < VENT_ATTEMPTS_EACH * VENT_ATTEMPTS_EACH && vents.isEmpty(); attempt++)
        {
            int x = DungeonPlan.SHELL + random.nextInt(span);
            int y = DungeonPlan.SHELL + 1 + random.nextInt(span - 1);
            int z = DungeonPlan.SHELL + random.nextInt(span);
            if (isFarFromOthers(x, z) && grid.material(x, y, z).isOpen() && isSurface(grid.material(x, y - 1, z)))
                placeVentOn(x, y, z);
        }
    }

    private boolean isFarFromOthers(int x, int z)
    {
        if (Math.abs(x - entry[0]) + Math.abs(z - entry[2]) < MIN_DISTANCE_FROM_ENTRY)
            return false;
        for (var vent : vents)
        {
            if (Math.abs(x - vent[0]) + Math.abs(z - vent[2]) < MIN_VENT_SPACING)
                return false;
        }
        return true;
    }

    private void tryVentAt(int x, int z, DungeonRoom room)
    {
        for (int y = room.minY() - ROOM_MARGIN; y <= room.maxY(); y++)
        {
            if (!grid.material(x, y, z).isOpen() || !isSurface(grid.material(x, y - 1, z)) || !isInner(x, y, z))
                continue;

            placeVentOn(x, y, z);
            return;
        }
    }

    private void placeVentOn(int x, int y, int z)
    {
        if (isSafe(x, y, z))
            placeFissure(x, y, z);
        else
            placeChimney(x, y, z);
    }

    /**
     * Flush with a floor people walk on: it stays solid underfoot, and crossing it is a question of timing.
     */
    private void placeFissure(int x, int y, int z)
    {
        if (!hasOpenAbove(x, y, z, JET_HEADROOM))
            return;

        grid.set(x, y - 1, z, SMOKER_VENT);
        vents.add(new int[] { x, y - 1, z });
    }

    private void placeChimney(int x, int y, int z)
    {
        int height = MIN_CHIMNEY + random.nextInt(CHIMNEY_SPREAD);
        if (!hasOpenAbove(x, y, z, height + JET_HEADROOM))
            return;
        for (int up = 0; up <= height; up++)
        {
            if (isSafe(x, y + up, z))
                return;
        }

        for (int up = 0; up < height; up++)
        {
            grid.set(x, y + up, z, VENT_CHIMNEY);
            if (up == 0)
                skirtChimney(x, y, z);
        }
        grid.set(x, y + height, z, SMOKER_VENT);
        vents.add(new int[] { x, y + height, z });
        ringWithTubeWorms(x, y, z);
    }

    private void skirtChimney(int x, int y, int z)
    {
        for (var side : FAUNA_SIDES)
        {
            int sideX = x + side.getStepX();
            int sideZ = z + side.getStepZ();
            if (grid.material(sideX, y, sideZ).isOpen() && !isSafe(sideX, y, sideZ))
                grid.set(sideX, y, sideZ, VENT_CHIMNEY);
        }
    }

    private boolean hasOpenAbove(int x, int y, int z, int cells)
    {
        for (int up = 0; up < cells; up++)
        {
            if (!grid.material(x, y + up, z).isOpen() || !isInner(x, y + up, z))
                return false;
        }
        return true;
    }

    private void ringWithTubeWorms(int x, int y, int z)
    {
        for (int dz = -TUBE_WORM_RING; dz <= TUBE_WORM_RING; dz++)
        {
            for (int dx = -TUBE_WORM_RING; dx <= TUBE_WORM_RING; dx++)
            {
                if (Math.abs(dx) + Math.abs(dz) < 2 || random.nextDouble() > TUBE_WORM_CHANCE)
                    continue;
                if (isInner(x + dx, y, z + dz) && grid.material(x + dx, y, z + dz).isOpen() && isSurface(grid.material(x + dx, y - 1, z + dz)))
                    placeTubeWorms(x + dx, y, z + dz);
            }
        }
    }
}
