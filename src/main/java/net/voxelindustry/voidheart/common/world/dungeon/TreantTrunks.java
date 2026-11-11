package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Old trunks standing on the floor of a forest dungeon's rooms, each hiding a Treant in its lowest three blocks. A trunk rises into the canopy or the crown of a
 * hanging tree when one is near enough, and otherwise ends in a crown of its own.
 * <p>
 * It draws from a random of its own, after everything else, so that forest dungeons generated before Treants keep the rest of their layout.
 */
final class TreantTrunks
{
    // The Treant fills the trunk's lowest blocks, as DormantTreantBlock.HEIGHT does in game
    public static final int TREANT_HEIGHT = 3;
    public static final int MIN_HEADROOM = 5;
    public static final int MAX_TRUNK = 12;
    public static final double ENTRY_CLEARANCE = 12;
    public static final int SPACING = 6;

    private static final long SALT = 0x7EA1_7C0DEL;
    private static final int SPOT_ATTEMPTS = 60;
    private static final int FLOOR_SEARCH_BELOW = 2;
    private static final int CROWN = 2;
    private static final double ROOT_CHANCE = 0.6;
    private static final int[][] CORNERS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    public interface Safety
    {
        boolean isSafe(int x, int y, int z);
    }

    private final DungeonBlueprint blueprint;
    private final DungeonGrid grid;
    private final List<DungeonRoom> rooms;
    private final Safety safety;
    private final int[] entry;
    private final List<DungeonMobTable.Placement> mobs;
    private final RandomSource random;
    private final List<int[]> trunks = new ArrayList<>();

    TreantTrunks(DungeonBlueprint blueprint, DungeonGrid grid, List<DungeonRoom> rooms, Safety safety, int[] entry, List<DungeonMobTable.Placement> mobs)
    {
        this.blueprint = blueprint;
        this.grid = grid;
        this.rooms = rooms;
        this.safety = safety;
        this.entry = entry;
        this.mobs = mobs;
        random = RandomSource.create(blueprint.seed() ^ SALT);
    }

    /**
     * @return where each trunk's Treant waits
     */
    List<int[]> grow()
    {
        int wanted = DungeonMobTable.treants(blueprint.theme(), blueprint.size(), blueprint.difficulty());
        if (wanted == 0 || rooms.isEmpty())
            return trunks;

        for (int attempt = 0; attempt < wanted * SPOT_ATTEMPTS && trunks.size() < wanted; attempt++)
        {
            var room = rooms.get(random.nextInt(rooms.size()));
            int x = room.minX() + random.nextInt(room.maxX() - room.minX() + 1);
            int z = room.minZ() + random.nextInt(room.maxZ() - room.minZ() + 1);
            int y = floorIn(x, room.minY() - FLOOR_SEARCH_BELOW, room.maxY(), z);
            if (y >= 0 && farFromEntry(x, y, z) && clearOfMobs(x, y, z) && clearOfTrunks(x, z) && hasWayOut(x, y, z))
                raise(x, y, z);
        }
        return trunks;
    }

    private void raise(int x, int y, int z)
    {
        grid.set(x, y, z, TREANT, Direction.UP);
        int top = y + 1;
        for (; top < y + MAX_TRUNK && isFree(x, top, z); top++)
            grid.set(x, top, z, LOG, Direction.UP);

        if (isFree(x, top, z))
            crown(x, top, z);

        for (var corner : CORNERS)
        {
            int rx = x + corner[0];
            int rz = z + corner[1];
            // A root beside a way out would catch the Treant's shoulder as it steps through
            boolean besideWayOut = isWayOut(x, y, z, corner[0], 0) || isWayOut(x, y, z, 0, corner[1]);
            if (!besideWayOut && random.nextDouble() < ROOT_CHANCE && isFree(rx, y, rz) && isGround(rx, y - 1, rz))
                grid.set(rx, y, rz, ROOTS, Direction.UP);
        }
        trunks.add(new int[] { x, y, z });
    }

    private void crown(int x, int y, int z)
    {
        for (int dy = -1; dy <= CROWN; dy++)
        {
            for (int dz = -CROWN; dz <= CROWN; dz++)
            {
                for (int dx = -CROWN; dx <= CROWN; dx++)
                {
                    int distance = dx * dx + dz * dz + dy * dy * 2;
                    if (distance <= (CROWN + 1) * (CROWN + 1) - 1 && isFree(x + dx, y + dy, z + dz))
                        grid.set(x + dx, y + dy, z + dz, dx == 0 && dz == 0 && dy < 1 ? LOG : LEAVES);
                }
            }
        }
    }

    /**
     * The lowest cell in the range with ground under it and headroom for a Treant and the trunk above it, or -1.
     */
    private int floorIn(int x, int fromY, int toY, int z)
    {
        for (int y = fromY; y <= toY; y++)
        {
            if (isGround(x, y - 1, z) && isColumnFree(x, y, z, MIN_HEADROOM))
                return y;
        }
        return -1;
    }

    /**
     * A Treant comes out on the side its player is, so there must be one side with room for it to step two blocks out.
     */
    private boolean hasWayOut(int x, int y, int z)
    {
        for (var side : Direction.Plane.HORIZONTAL)
        {
            if (isWayOut(x, y, z, side.getStepX(), side.getStepZ()))
                return true;
        }
        return false;
    }

    private boolean isWayOut(int x, int y, int z, int stepX, int stepZ)
    {
        for (int step = 1; step <= 2; step++)
        {
            for (int up = 0; up < TREANT_HEIGHT; up++)
            {
                if (!isWalkable(x + stepX * step, y + up, z + stepZ * step))
                    return false;
            }
        }
        return true;
    }

    /**
     * What it can step through on its way out, ferns and grass as much as air, as the game finds no collision there either.
     */
    private boolean isWalkable(int x, int y, int z)
    {
        if (!isInner(x, y, z))
            return false;
        var cell = grid.material(x, y, z);
        return cell.isOpen() || cell.isPassable() && cell != FLUID;
    }

    private boolean isColumnFree(int x, int y, int z, int height)
    {
        for (int up = 0; up < height; up++)
        {
            if (!isFree(x, y + up, z))
                return false;
        }
        return true;
    }

    private boolean isFree(int x, int y, int z)
    {
        return isInner(x, y, z) && grid.material(x, y, z).isOpen() && !safety.isSafe(x, y, z);
    }

    private boolean isInner(int x, int y, int z)
    {
        int edge = grid.width;
        return x >= DungeonPlan.SHELL && y >= DungeonPlan.SHELL && z >= DungeonPlan.SHELL && x < edge - DungeonPlan.SHELL && y < edge - DungeonPlan.SHELL && z < edge - DungeonPlan.SHELL;
    }

    private boolean isGround(int x, int y, int z)
    {
        if (!grid.isInside(x, y, z))
            return false;

        var below = grid.material(x, y, z);
        return below.isCube() && !below.isOpen() && below != TEMPLATE && below != NEST && below != POCKET_WALL && below != TREANT;
    }

    private boolean farFromEntry(int x, int y, int z)
    {
        return Math.hypot(Math.hypot(x - entry[0], y - entry[1]), z - entry[2]) >= ENTRY_CLEARANCE;
    }

    private boolean clearOfMobs(int x, int y, int z)
    {
        for (var mob : mobs)
        {
            if (Math.abs(mob.x() - x) <= 2 && Math.abs(mob.z() - z) <= 2 && mob.y() + mob.mob().height + 1 >= y && mob.y() <= y + MAX_TRUNK + CROWN)
                return false;
        }
        return true;
    }

    private boolean clearOfTrunks(int x, int z)
    {
        for (var trunk : trunks)
        {
            if (Math.abs(trunk[0] - x) < SPACING && Math.abs(trunk[2] - z) < SPACING)
                return false;
        }
        return true;
    }
}
