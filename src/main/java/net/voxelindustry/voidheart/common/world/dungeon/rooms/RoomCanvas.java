package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonBlueprint;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonGrid;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTemplate;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.AIR;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.CHAIN;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.FLOOR;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.FLUID;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.LANTERN;
import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.POOL_BED;

/**
 * A room being drawn on its own before it is warped and stamped into the dungeon.
 * The floor is at y 0 and the ceiling at y {@link #h}, walls at x 0 and {@link #w}, z 0 and {@link #d}, with a few layers below the floor to dig pools.
 * Rooms have a long axis: u runs along it, v across it, so that one drawing serves both orientations.
 */
public final class RoomCanvas
{
    public static final int BELOW = 4;
    public static final int WALKWAY_HEIGHT = 3;
    private static final int DOORWAY_DEPTH = 3;

    public final int w;
    public final int h;
    public final int d;
    public final boolean alongX;
    public final RandomSource random;
    public final DungeonBlueprint blueprint;
    public final boolean fragment;
    public final List<DungeonTemplate.Placement> templates = new ArrayList<>();
    public final List<int[]> calmSpots = new ArrayList<>();
    // Stairs and galleries spanning several floors would come apart if warped
    public boolean rigid;

    private final DungeonGrid cells;
    private final BitSet walkway;

    public RoomCanvas(int w, int h, int d, RandomSource random, DungeonBlueprint blueprint, boolean fragment)
    {
        this.w = w;
        this.h = h;
        this.d = d;
        this.random = random;
        this.blueprint = blueprint;
        this.fragment = fragment;
        alongX = w >= d;
        cells = new DungeonGrid(w + 1, h + 1 + BELOW, d + 1);
        walkway = new BitSet((w + 1) * (d + 1) * (h + 1 + BELOW));
    }

    public DungeonGrid cells()
    {
        return cells;
    }

    public int length()
    {
        return alongX ? w : d;
    }

    public int breadth()
    {
        return alongX ? d : w;
    }

    public Direction forward()
    {
        return alongX ? Direction.EAST : Direction.SOUTH;
    }

    public Direction back()
    {
        return forward().getOpposite();
    }

    public Direction side()
    {
        return alongX ? Direction.SOUTH : Direction.EAST;
    }

    public DungeonMaterial get(int x, int y, int z)
    {
        return cells.material(x, y + BELOW, z);
    }

    public void set(int x, int y, int z, DungeonMaterial material)
    {
        cells.set(x, y + BELOW, z, material);
    }

    public void set(int x, int y, int z, DungeonMaterial material, Direction facing)
    {
        cells.set(x, y + BELOW, z, material, facing);
    }

    public void set(int x, int y, int z, DungeonMaterial material, Direction facing, boolean flipped)
    {
        cells.set(x, y + BELOW, z, material, facing, flipped);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, DungeonMaterial material)
    {
        cells.fill(x0, y0 + BELOW, z0, x1, y1 + BELOW, z1, material);
    }

    public void setUV(int u, int y, int v, DungeonMaterial material)
    {
        if (alongX)
            set(u, y, v, material);
        else
            set(v, y, u, material);
    }

    public void setUV(int u, int y, int v, DungeonMaterial material, Direction facing)
    {
        if (alongX)
            set(u, y, v, material, facing);
        else
            set(v, y, u, material, facing);
    }

    public void setUV(int u, int y, int v, DungeonMaterial material, Direction facing, boolean flipped)
    {
        if (alongX)
            set(u, y, v, material, facing, flipped);
        else
            set(v, y, u, material, facing, flipped);
    }

    public void fillUV(int u0, int y0, int v0, int u1, int y1, int v1, DungeonMaterial material)
    {
        if (alongX)
            fill(u0, y0, v0, u1, y1, v1, material);
        else
            fill(v0, y0, u0, v1, y1, u1, material);
    }

    public DungeonMaterial getUV(int u, int y, int v)
    {
        return alongX ? get(u, y, v) : get(v, y, u);
    }

    /**
     * A closed room: floor, walls and ceiling around air.
     */
    public void shell(DungeonMaterial wall, DungeonMaterial floor, DungeonMaterial ceiling)
    {
        fill(1, 1, 1, w - 1, h - 1, d - 1, AIR);
        fill(0, 0, 0, w, 0, d, floor);
        fill(0, h, 0, w, h, d, ceiling);
        for (int y = 1; y < h; y++)
        {
            fill(0, y, 0, w, y, 0, wall);
            fill(0, y, d, w, y, d, wall);
            fill(0, y, 0, 0, y, d, wall);
            fill(w, y, 0, w, y, d, wall);
        }
    }

    /**
     * A floor with air above, whose surroundings are left to what the dungeon has there: nothing when floating, rock when buried.
     */
    public void open(DungeonMaterial floor)
    {
        fill(0, 1, 0, w, h - 1, d, AIR);
        fill(0, 0, 0, w, 0, d, floor);
    }

    public void hangingLantern(int x, int z, int drop)
    {
        int bottom = Math.max(h - 1 - drop, 3);
        for (int y = h - 1; y > bottom; y--)
            set(x, y, z, CHAIN, Direction.UP);
        set(x, bottom, z, LANTERN, Direction.DOWN, true);
    }

    /**
     * Sunk into the floor, which stays still when warped since water would otherwise spill out of a shifted rim.
     */
    public void pool(int x0, int z0, int x1, int z1, int depth)
    {
        int bottom = -Math.min(depth, BELOW - 1);
        for (int z = z0 - 1; z <= z1 + 1; z++)
        {
            for (int x = x0 - 1; x <= x1 + 1; x++)
            {
                boolean rim = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = bottom - 1; y <= 0; y++)
                {
                    if (rim)
                    {
                        if (y < 0)
                            set(x, y, z, POOL_BED);
                    }
                    else
                        set(x, y, z, y == bottom - 1 ? POOL_BED : FLUID);
                }
            }
        }
        calmSpots.add(new int[] { (x0 + x1) / 2, (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 });
    }

    public void template(DungeonTemplate template, int x, int y, int z)
    {
        if (fragment)
            return;

        templates.add(new DungeonTemplate.Placement(template, x, y, z));
        var size = template.size();
        fill(x, y, z, x + size.getX() - 1, y + size.getY() - 1, z + size.getZ() - 1, DungeonMaterial.TEMPLATE);
        calmSpots.add(new int[] { x + size.getX() / 2, z + size.getZ() / 2, Math.max(size.getX(), size.getZ()) * 3 / 4 + 1 });
    }

    /**
     * Clears a walkable strip from a door to the middle of the room, through whatever was drawn there, which guarantees a dungeon stays connected.
     */
    public void carveWalkway(int doorX, int doorZ)
    {
        int centerX = w / 2;
        int centerZ = d / 2;
        if (doorX == 0 || doorX == w)
        {
            for (int x = Math.min(doorX, centerX); x <= Math.max(doorX, centerX); x++)
                clearWalkway(x, doorZ);
            for (int z = Math.min(doorZ, centerZ); z <= Math.max(doorZ, centerZ); z++)
                clearWalkway(centerX, z);
        }
        else
        {
            for (int z = Math.min(doorZ, centerZ); z <= Math.max(doorZ, centerZ); z++)
                clearWalkway(doorX, z);
            for (int x = Math.min(doorX, centerX); x <= Math.max(doorX, centerX); x++)
                clearWalkway(x, centerZ);
        }
        calmSpots.add(new int[] { doorX, doorZ, 0 });
    }

    /**
     * For rooms nobody crosses: a short way in, through the wall and whatever is right behind it. A structure template keeps its own floor.
     */
    public void carveDoorway(int doorX, int doorZ, Direction into)
    {
        for (int step = 0; step < DOORWAY_DEPTH; step++)
        {
            int x = doorX + into.getStepX() * step;
            int z = doorZ + into.getStepZ() * step;
            clearWalkway(x, z);
        }
        calmSpots.add(new int[] { doorX, doorZ, DOORWAY_DEPTH });
    }

    private void clearWalkway(int centerX, int centerZ)
    {
        for (int z = centerZ - 1; z <= centerZ + 1; z++)
        {
            for (int x = centerX - 1; x <= centerX + 1; x++)
            {
                if (x < 0 || z < 0 || x > w || z > d)
                    continue;

                var ground = get(x, 0, z);
                if (ground != DungeonMaterial.TEMPLATE && (!ground.isCube() || ground.isOpen()))
                    set(x, 0, z, FLOOR);

                walkway.set(walkwayIndex(x, 0, z));
                for (int y = 1; y <= WALKWAY_HEIGHT; y++)
                {
                    var material = get(x, y, z);
                    if (!material.isPassable() || material == FLUID)
                        set(x, y, z, AIR);
                    walkway.set(walkwayIndex(x, y, z));
                }
            }
        }
    }

    /**
     * A walkway can run over a whole pool. When none is left, digs the largest one that fits beside the walkways.
     */
    public void ensurePool()
    {
        for (int z = 0; z <= d; z++)
        {
            for (int x = 0; x <= w; x++)
            {
                if (get(x, 0, z) == FLUID)
                    return;
            }
        }

        for (int size = 2; size >= 0; size--)
        {
            for (int z = 1; z <= d - 1 - size; z++)
            {
                for (int x = 1; x <= w - 1 - size; x++)
                {
                    if (isClearForPool(x, z, size))
                    {
                        pool(x, z, x + size, z + size, 1);
                        return;
                    }
                }
            }
        }
    }

    private boolean isClearForPool(int x0, int z0, int size)
    {
        for (int z = z0 - 1; z <= z0 + size + 1; z++)
        {
            for (int x = x0 - 1; x <= x0 + size + 1; x++)
            {
                if (isWalkway(x, 0, z) || get(x, 0, z) == DungeonMaterial.TEMPLATE)
                    return false;
            }
        }
        return true;
    }

    private int walkwayIndex(int x, int y, int z)
    {
        return ((y + BELOW) * (d + 1) + z) * (w + 1) + x;
    }

    public boolean isWalkway(int x, int y, int z)
    {
        return walkway.get(walkwayIndex(x, y, z));
    }

    /**
     * A cell by its raw layer, where layer 0 is {@link #BELOW} blocks under the floor.
     */
    public short rawCell(int x, int rawY, int z)
    {
        return cells.cell(x, rawY, z);
    }
}
