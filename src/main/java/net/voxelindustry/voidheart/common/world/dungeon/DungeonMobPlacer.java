package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.CELL;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonSize.plane;

/**
 * Spreads a dungeon's mobs over its rooms: a couple in each, more in large ones, hardly any in corridors, and none where players arrive.
 */
final class DungeonMobPlacer
{
    private static final double CORRIDOR_SHARE = 0.08;
    private static final int SPOT_ATTEMPTS = 40;
    private static final int MAX_PASSES = 6;
    private static final int CELLS_PER_EXTRA_MOB = 6;
    private static final double CLING_CHANCE = 0.4;
    private static final double ENTRY_CLEARANCE = 10;
    private static final int FLOOR_SEARCH_BELOW = 2;
    private static final int FLOCK_SPREAD = 3;
    private static final int FLOCK_SIZE_SPREAD = 3;
    private static final Direction[] CLING_SURFACES = { Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private final DungeonBlueprint blueprint;
    private final DungeonGrid grid;
    private final RandomSource random;
    private final List<DungeonMobTable.Placement> placed;
    private final int[] entry;
    private final List<DungeonMob> roster;
    private final List<DungeonMob> walkers;
    private final Set<Long> taken = new HashSet<>();

    DungeonMobPlacer(DungeonBlueprint blueprint, DungeonGrid grid, RandomSource random, List<DungeonMobTable.Placement> placed, int[] entry)
    {
        this.blueprint = blueprint;
        this.grid = grid;
        this.random = random;
        this.placed = placed;
        this.entry = entry;
        roster = DungeonMobTable.roster(blueprint.theme(), blueprint.difficulty());
        walkers = roster.stream().filter(mob -> !mob.flies).toList();
    }

    void place(List<DungeonRoom> rooms, DungeonRoom entryRoom, Collection<DungeonPlanner.Flat> flats)
    {
        int budget = DungeonMobTable.budget(blueprint.size(), blueprint.difficulty());
        budget -= placeInCorridors(flats, (int) (budget * CORRIDOR_SHARE));

        var order = new ArrayList<>(rooms.stream().filter(room -> room != entryRoom).toList());
        Collections.shuffle(order, new java.util.Random(random.nextLong()));
        for (int pass = 0; pass < MAX_PASSES && budget > 0; pass++)
        {
            for (var room : order)
            {
                if (budget <= 0)
                    break;
                budget -= placeInRoom(room, Math.min(budget, quota(room)), budget);
            }
        }
        if (budget > 0)
            placeInRoom(entryRoom, budget, budget);
    }

    private int quota(DungeonRoom room)
    {
        return 1 + random.nextInt(2) + room.width() * room.depth() / CELLS_PER_EXTRA_MOB;
    }

    private int placeInCorridors(Collection<DungeonPlanner.Flat> flats, int wanted)
    {
        var order = new ArrayList<>(flats);
        Collections.shuffle(order, new java.util.Random(random.nextLong()));
        int count = 0;
        for (var flat : order)
        {
            if (count >= wanted)
                break;

            int x = plane(flat.i) + CELL / 2;
            int z = plane(flat.k) + CELL / 2;
            int y = floorIn(x, plane(flat.j), plane(flat.j) + CELL, z, DungeonMob.VOID_SKELETON);
            if (y >= 0)
            {
                add(new DungeonMobTable.Placement(DungeonMobTable.pick(walkers, random), x, y, z, Direction.UP, 0));
                count++;
            }
        }
        return count;
    }

    private int placeInRoom(DungeonRoom room, int wanted, int budget)
    {
        int count = 0;
        while (count < wanted)
        {
            var mob = DungeonMobTable.pick(roster, random);
            int inGroup = mob.flies ? placeInAir(room, mob, budget - count) : placeGroup(room, mob, Math.min(mob.groupSize, wanted - count));
            if (inGroup == 0)
                return count;
            count += inGroup;
        }
        return count;
    }

    private int placeGroup(DungeonRoom room, DungeonMob mob, int group)
    {
        int placed = 0;
        for (int i = 0; i < group; i++)
        {
            if (placeOne(room, mob))
                placed++;
        }
        return placed;
    }

    /**
     * A flock stays whole even past what the room would otherwise hold, as one of them alone is no flock.
     */
    private int placeInAir(DungeonRoom room, DungeonMob mob, int budget)
    {
        int size = mob.groupSize > 1 ? mob.groupSize - FLOCK_SIZE_SPREAD / 2 + random.nextInt(FLOCK_SIZE_SPREAD) : 1;
        int wanted = Math.min(size, budget);
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++)
        {
            int x = room.minX() + random.nextInt(room.maxX() - room.minX() + 1);
            int y = room.minY() + random.nextInt(room.maxY() - room.minY() + 1);
            int z = room.minZ() + random.nextInt(room.maxZ() - room.minZ() + 1);
            if (!isFreeInAir(x, y, z, mob))
                continue;

            taken.add(pack(x, y, z));
            add(new DungeonMobTable.Placement(mob, x, y, z, Direction.UP, 0));
            int placed = 1;
            for (int tries = 0; placed < wanted && tries < SPOT_ATTEMPTS; tries++)
            {
                int mateX = x - FLOCK_SPREAD + random.nextInt(FLOCK_SPREAD * 2 + 1);
                int mateY = y - FLOCK_SPREAD + random.nextInt(FLOCK_SPREAD * 2 + 1);
                int mateZ = z - FLOCK_SPREAD + random.nextInt(FLOCK_SPREAD * 2 + 1);
                if (isFreeInAir(mateX, mateY, mateZ, mob))
                {
                    taken.add(pack(mateX, mateY, mateZ));
                    add(new DungeonMobTable.Placement(mob, mateX, mateY, mateZ, Direction.UP, 0));
                    placed++;
                }
            }
            return placed;
        }
        return 0;
    }

    private boolean isFreeInAir(int x, int y, int z, DungeonMob mob)
    {
        return fits(x, y, z, mob) && farFromEntry(x, y, z) && !taken.contains(pack(x, y, z));
    }

    private boolean placeOne(DungeonRoom room, DungeonMob mob)
    {
        if (mob.clings && random.nextDouble() < CLING_CHANCE && placeClinging(room, mob))
            return true;

        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++)
        {
            int x = room.minX() + random.nextInt(room.maxX() - room.minX() + 1);
            int z = room.minZ() + random.nextInt(room.maxZ() - room.minZ() + 1);
            int y = floorIn(x, room.minY() - FLOOR_SEARCH_BELOW, room.maxY(), z, mob);
            if (y >= 0 && farFromEntry(x, y, z) && taken.add(pack(x, y, z)))
            {
                add(new DungeonMobTable.Placement(mob, x, y, z, Direction.UP, 0));
                return true;
            }
        }

        if (room.kind.template() == null)
            return false;

        int x = room.minX() + 1 + random.nextInt(room.maxX() - room.minX() - 1);
        int z = room.minZ() + 1 + random.nextInt(room.maxZ() - room.minZ() - 1);
        if (!taken.add(pack(x, room.minY(), z)))
            return false;
        add(new DungeonMobTable.Placement(mob, x, room.minY() - FLOOR_SEARCH_BELOW, z, Direction.UP, room.maxY() - room.minY() + FLOOR_SEARCH_BELOW));
        return true;
    }

    private boolean placeClinging(DungeonRoom room, DungeonMob mob)
    {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++)
        {
            int x = room.minX() + random.nextInt(room.maxX() - room.minX() + 1);
            int y = room.minY() + random.nextInt(room.maxY() - room.minY() + 1);
            int z = room.minZ() + random.nextInt(room.maxZ() - room.minZ() + 1);
            var surface = CLING_SURFACES[random.nextInt(CLING_SURFACES.length)];
            if (grid.isInside(x, y, z) && grid.material(x, y, z).isOpen()
                && grid.material(x - surface.getStepX(), y - surface.getStepY(), z - surface.getStepZ()).isAnchor()
                && farFromEntry(x, y, z) && taken.add(pack(x, y, z)))
            {
                add(new DungeonMobTable.Placement(mob, x, y, z, surface, 0));
                return true;
            }
        }
        return false;
    }

    /**
     * The lowest cell in the range where this mob can stand, or -1.
     */
    private int floorIn(int x, int fromY, int toY, int z, DungeonMob mob)
    {
        for (int y = fromY; y <= toY; y++)
        {
            if (isGround(x, y - 1, z) && fits(x, y, z, mob))
                return y;
        }
        return -1;
    }

    private boolean isGround(int x, int y, int z)
    {
        if (!grid.isInside(x, y, z))
            return false;

        var below = grid.material(x, y, z);
        return below.isCube() && !below.isOpen() && below != DungeonMaterial.TEMPLATE && below != DungeonMaterial.NEST && below != DungeonMaterial.POCKET_WALL;
    }

    private boolean fits(int x, int y, int z, DungeonMob mob)
    {
        for (int up = 0; up < mob.height; up++)
        {
            for (int dz = -mob.halfWidth; dz <= mob.halfWidth; dz++)
            {
                for (int dx = -mob.halfWidth; dx <= mob.halfWidth; dx++)
                {
                    if (!grid.isInside(x + dx, y + up, z + dz) || !grid.material(x + dx, y + up, z + dz).isOpen())
                        return false;
                }
            }
        }
        return true;
    }

    private boolean farFromEntry(int x, int y, int z)
    {
        return Math.hypot(Math.hypot(x - entry[0], y - entry[1]), z - entry[2]) >= ENTRY_CLEARANCE;
    }

    private void add(DungeonMobTable.Placement placement)
    {
        placed.add(placement);
    }

    private static long pack(int x, int y, int z)
    {
        return (long) x << 40 | (long) y << 20 | z;
    }
}
