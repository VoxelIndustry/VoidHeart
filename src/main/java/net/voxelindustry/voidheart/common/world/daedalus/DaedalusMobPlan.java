package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Where a district's mobs stand when it generates, by lattice cell: the spot inside a cell is only found once the chunk is painted, see {@link DaedalusMobSpots}.
 * A district holds one Kernacian Dragoon on guard in a large room far from the bridges, and a few mobs in a small share of its many rooms, with some along corridors.
 * They are placed once and never come back, so a cleared district stays cleared.
 */
public final class DaedalusMobPlan
{
    public static final int BUDGET = 100;
    static final int NESTS = 6;
    static final double CORRIDOR_SHARE = 0.15;
    // In cells, counting tiers as cells too
    static final double LANDING_CLEARANCE = 3;
    static final double DRAGOON_CLEARANCE = 5;
    static final int ANYWHERE = -1;

    private static final long SALT = 0x6461656D6F6273L;
    private static final double SECOND_GROUP_CHANCE = 0.35;
    private static final int CELLS_PER_EXTRA_GROUP = 4;
    private static final int VAULT_BONUS = 6;
    private static final int PICK_ATTEMPTS = 8;
    private static final int DRAGOON_ROOMS_TRIED = 12;

    /**
     * @param group     placements made together share it, as the two halves of a Legionary pair do
     * @param facing    a {@link Compass} direction, or {@link #ANYWHERE} to face anywhere
     * @param localX    where in the cell it would rather stand, or {@link #ANYWHERE}
     */
    public record Placement(DaedalusMob mob, int i, int k, int j, int group, int facing, int localX, int localZ, long salt)
    {
        public int chunkX(DistrictPos pos)
        {
            return Math.floorDiv(pos.originX() + i * DistrictPos.CELL, 16);
        }

        public int chunkZ(DistrictPos pos)
        {
            return Math.floorDiv(pos.originZ() + j * DistrictPos.CELL, 16);
        }
    }

    private final DistrictPos pos;
    private final List<Placement> placements;
    private final Map<Long, List<Placement>> byChunk = new HashMap<>();

    private DaedalusMobPlan(DistrictPos pos, List<Placement> placements)
    {
        this.pos = pos;
        this.placements = List.copyOf(placements);
        for (var placement : this.placements)
            byChunk.computeIfAbsent(chunkKey(placement.chunkX(pos), placement.chunkZ(pos)), key -> new ArrayList<>()).add(placement);
    }

    public List<Placement> placements()
    {
        return placements;
    }

    public List<Placement> inChunk(int chunkX, int chunkZ)
    {
        return byChunk.getOrDefault(chunkKey(chunkX, chunkZ), List.of());
    }

    public DistrictPos pos()
    {
        return pos;
    }

    private static long chunkKey(int chunkX, int chunkZ)
    {
        return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    }

    static DaedalusMobPlan of(DistrictPlan plan)
    {
        return new Planner(plan).plan();
    }

    private static final class Planner
    {
        private final DistrictPlan plan;
        private final DaedalusRoster roster;
        private final SplittableRandom random;
        private final List<Placement> placed = new ArrayList<>();
        private int mobs;
        private int familyMobs;
        private int groups;

        private Planner(DistrictPlan plan)
        {
            this.plan = plan;
            roster = DaedalusRoster.of(plan.theme);
            random = new SplittableRandom(DaedalusHash.mix(plan.worldSeed, plan.pos.x(), plan.pos.z(), SALT));
        }

        private DaedalusMobPlan plan()
        {
            var dragoonRoom = placeDragoon();
            var rooms = new ArrayList<Room>();
            for (var room : plan.rooms())
            {
                if (room != dragoonRoom)
                    rooms.add(room);
            }
            Collections.shuffle(rooms, new Random(random.nextLong()));

            placeInCorridors((int) (BUDGET * CORRIDOR_SHARE));
            for (var room : rooms)
            {
                if (mobs >= BUDGET)
                    break;
                placeInRoom(room);
            }
            if (roster.hasNests())
                placeNests(rooms);
            return new DaedalusMobPlan(plan.pos, placed);
        }

        /**
         * Painting is the only way to know where furniture leaves room to stand, so the dragoon's spot is tried on the cells of the best rooms in turn until one fits.
         */
        private Room placeDragoon()
        {
            var candidates = new ArrayList<Room>();
            var scores = new HashMap<Room, Integer>();
            for (var room : plan.rooms())
            {
                if (!room.shape().hasDoorsAt(room.minK(), room) || distanceToLanding(centreI(room), room.minK(), centreJ(room)) < DRAGOON_CLEARANCE)
                    continue;
                candidates.add(room);
                scores.put(room, room.cellsWide() * room.cellsDeep() * 2 + (isVaultFloor(room) ? VAULT_BONUS : 0) + random.nextInt(4));
            }
            candidates.sort((a, b) -> scores.get(b) - scores.get(a));

            var canvas = new DaedalusCanvas(16, 16);
            for (int index = 0; index < Math.min(DRAGOON_ROOMS_TRIED, candidates.size()); index++)
            {
                var room = candidates.get(index);
                int facing = doorSide(room);
                long salt = random.nextLong();
                // The middle of the room in blocks, then each of its floor cells from the nearest
                int middleX = room.minI() * DistrictPos.CELL + room.cellsWide() * DistrictPos.CELL / 2;
                int middleZ = room.minJ() * DistrictPos.CELL + room.cellsDeep() * DistrictPos.CELL / 2;
                var cells = new ArrayList<int[]>();
                for (int j = room.minJ(); j <= room.maxJ(); j++)
                {
                    for (int i = room.minI(); i <= room.maxI(); i++)
                        cells.add(new int[] { i, j });
                }
                cells.sort(java.util.Comparator.comparingInt(cell -> Math.abs(cell[0] * DistrictPos.CELL + 4 - middleX) + Math.abs(cell[1] * DistrictPos.CELL + 4 - middleZ)));

                for (int[] cell : cells)
                {
                    int localX = Math.clamp(middleX - cell[0] * DistrictPos.CELL, 0, DistrictPos.CELL - 1);
                    int localZ = Math.clamp(middleZ - cell[1] * DistrictPos.CELL, 0, DistrictPos.CELL - 1);
                    var placement = new Placement(DaedalusMob.KERNACIAN_DRAGOON, cell[0], room.minK(), cell[1], groups, facing, localX, localZ, salt);
                    canvas.reset(placement.chunkX(plan.pos) * 16, placement.chunkZ(plan.pos) * 16);
                    DistrictPainter.paint(plan, canvas);
                    if (DaedalusMobSpots.find(canvas, plan.pos, placement, new HashSet<>()) == null)
                        continue;
                    groups++;
                    add(placement);
                    return room;
                }
            }
            return null;
        }

        /**
         * The side a door of the room's floor opens on, so that the dragoon watches the way in.
         */
        private int doorSide(Room room)
        {
            var doors = new ArrayList<Integer>();
            int k = room.minK();
            for (int j = room.minJ(); j <= room.maxJ(); j++)
            {
                for (int i = room.minI(); i <= room.maxI(); i++)
                {
                    for (int direction = 0; direction < Compass.COUNT; direction++)
                    {
                        int ni = i + Compass.dx(direction);
                        int nj = j + Compass.dz(direction);
                        boolean outside = ni < room.minI() || ni > room.maxI() || nj < room.minJ() || nj > room.maxJ();
                        if (outside && plan.isOpen(i, k, j, direction))
                            doors.add(direction);
                    }
                }
            }
            return doors.isEmpty() ? random.nextInt(Compass.COUNT) : doors.get(random.nextInt(doors.size()));
        }

        private boolean isVaultFloor(Room room)
        {
            for (var open : plan.voids())
            {
                if (open.minK() == room.minK() && open.minI() <= room.maxI() && open.maxI() >= room.minI() && open.minJ() <= room.maxJ() && open.maxJ() >= room.minJ())
                    return true;
            }
            return false;
        }

        private void placeInCorridors(int wanted)
        {
            var corridors = new ArrayList<Integer>();
            for (int cell = 0; cell < DistrictPlan.CELL_COUNT; cell++)
            {
                int i = cell % DistrictPos.CELLS;
                int j = cell / DistrictPos.CELLS % DistrictPos.CELLS;
                int k = cell / (DistrictPos.CELLS * DistrictPos.CELLS);
                if (plan.kind(i, k, j) == DistrictPlan.CORRIDOR && distanceToLanding(i, k, j) >= LANDING_CLEARANCE)
                    corridors.add(cell);
            }
            Collections.shuffle(corridors, new Random(random.nextLong()));

            for (int index = 0; index < corridors.size() && mobs < wanted; index++)
            {
                int cell = corridors.get(index);
                var mob = pickWalker();
                if (mob != null)
                    addGroup(mob, cell % DistrictPos.CELLS, cell / (DistrictPos.CELLS * DistrictPos.CELLS), cell / DistrictPos.CELLS % DistrictPos.CELLS);
            }
        }

        private DaedalusMob pickWalker()
        {
            for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++)
            {
                var mob = roster.pick(random, share());
                if (mob.placing == DaedalusMob.Placing.FLOOR && mob.halfWidth == 0)
                    return mob;
            }
            return null;
        }

        private void placeInRoom(Room room)
        {
            var floors = floorCells(room);
            if (floors.isEmpty())
                return;

            int wanted = 1 + (random.nextDouble() < SECOND_GROUP_CHANCE ? 1 : 0) + room.cellsWide() * room.cellsDeep() / CELLS_PER_EXTRA_GROUP;
            for (int group = 0; group < wanted && mobs < BUDGET; group++)
            {
                var mob = roster.pick(random, share());
                if (mob.placing == DaedalusMob.Placing.AIR)
                {
                    int[] air = airCell(room);
                    addGroup(mob, air[0], air[1], air[2]);
                }
                else
                {
                    int[] cell = floors.get(random.nextInt(floors.size()));
                    addGroup(mob, cell[0], cell[1], cell[2]);
                }
            }
        }

        /**
         * Flyers keep to the open: a pit or vault when the district has one, otherwise the room they were picked for.
         */
        private int[] airCell(Room room)
        {
            var open = new ArrayList<int[]>();
            for (var volume : plan.voids())
            {
                for (int k = volume.minK(); k <= volume.maxK(); k++)
                {
                    for (int j = volume.minJ(); j <= volume.maxJ(); j++)
                    {
                        for (int i = volume.minI(); i <= volume.maxI(); i++)
                        {
                            if (distanceToLanding(i, k, j) >= LANDING_CLEARANCE)
                                open.add(new int[] { i, k, j });
                        }
                    }
                }
            }
            if (!open.isEmpty())
                return open.get(random.nextInt(open.size()));

            int k = room.minK() + random.nextInt(room.maxK() - room.minK() + 1);
            return new int[] { room.minI() + random.nextInt(room.cellsWide()), k, room.minJ() + random.nextInt(room.cellsDeep()) };
        }

        private List<int[]> floorCells(Room room)
        {
            var cells = new ArrayList<int[]>();
            for (int k = room.minK(); k <= room.maxK(); k++)
            {
                if (!room.shape().hasDoorsAt(k, room))
                    continue;
                for (int j = room.minJ(); j <= room.maxJ(); j++)
                {
                    for (int i = room.minI(); i <= room.maxI(); i++)
                    {
                        if (distanceToLanding(i, k, j) >= LANDING_CLEARANCE)
                            cells.add(new int[] { i, k, j });
                    }
                }
            }
            return cells;
        }

        private void placeNests(List<Room> rooms)
        {
            int nests = 0;
            for (var room : rooms)
            {
                if (nests >= NESTS)
                    return;
                var floors = floorCells(room);
                if (floors.isEmpty())
                    continue;
                int[] cell = floors.get(random.nextInt(floors.size()));
                placed.add(new Placement(DaedalusMob.BARNACLE_NEST, cell[0], cell[1], cell[2], groups++, ANYWHERE, ANYWHERE, ANYWHERE, random.nextLong()));
                nests++;
            }
        }

        private void addGroup(DaedalusMob mob, int i, int k, int j)
        {
            int group = groups++;
            int size = mob == DaedalusMob.EYE_MAW ? mob.groupSize - 1 + random.nextInt(3) : mob.groupSize;
            for (int member = 0; member < size; member++)
                add(new Placement(mob, i, k, j, group, ANYWHERE, ANYWHERE, ANYWHERE, random.nextLong()));
        }

        private void add(Placement placement)
        {
            placed.add(placement);
            mobs++;
            if (roster.isFamily(placement.mob()))
                familyMobs++;
        }

        private double share()
        {
            return mobs == 0 ? 0 : familyMobs / (double) mobs;
        }

        private double distanceToLanding(int i, int k, int j)
        {
            double nearest = Double.MAX_VALUE;
            for (var landing : plan.landings())
                nearest = Math.min(nearest, Math.sqrt(square(landing.i() - i) + square(landing.k() - k) + square(landing.j() - j)));
            return nearest;
        }

        private static int centreI(Room room)
        {
            return (room.minI() + room.maxI()) / 2;
        }

        private static int centreJ(Room room)
        {
            return (room.minJ() + room.maxJ()) / 2;
        }

        private static double square(int value)
        {
            return value * (double) value;
        }
    }
}
