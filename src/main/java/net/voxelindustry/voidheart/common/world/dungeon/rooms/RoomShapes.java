package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Shapes several themes share.
 */
final class RoomShapes
{
    private static final int TREE_CLEARANCE = 4;
    private static final int TREE_MIN_TRUNK = 2;
    private static final int POOL_LIGHT_SPACING = 5;

    private RoomShapes()
    {
    }

    static void disc(RoomCanvas c, int cx, int y, int cz, int radius, DungeonMaterial material)
    {
        for (int z = -radius; z <= radius; z++)
        {
            for (int x = -radius; x <= radius; x++)
            {
                if (x * x + z * z <= radius * radius + 1)
                    c.set(cx + x, y, cz + z, material);
            }
        }
    }

    static void column(RoomCanvas c, int x, int z, int y0, int y1, DungeonMaterial material)
    {
        for (int y = y0; y <= y1; y++)
            c.set(x, y, z, material, Direction.UP);
    }

    static void columnUV(RoomCanvas c, int u, int v, int y0, int y1, DungeonMaterial material)
    {
        for (int y = y0; y <= y1; y++)
            c.setUV(u, y, v, material, Direction.UP);
    }

    /**
     * A tall opening in a wall along the u axis, narrowing to a point.
     */
    static void lancet(RoomCanvas c, int u, int v, int y0, int y1, int halfWidth, DungeonMaterial glass)
    {
        for (int y = y0; y <= y1; y++)
        {
            int half = y >= y1 - halfWidth ? Math.max(0, halfWidth - (y - (y1 - halfWidth))) : halfWidth;
            for (int offset = -half; offset <= half; offset++)
                c.setUV(u + offset, y, v, glass);
        }
    }

    /**
     * A small house with a pitched roof. Its door is at the middle of the side facing the low z, or the low x when it is deeper than wide.
     */
    static void house(RoomCanvas c, int x0, int z0, int x1, int z1, int y0, int wallHeight)
    {
        int top = y0 + wallHeight;
        c.fill(x0, y0, z0, x1, y0, z1, PLANKS);
        c.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, top - 1, z1 - 1, AIR);
        for (int y = y0 + 1; y < top; y++)
        {
            for (int x = x0; x <= x1; x++)
            {
                c.set(x, y, z0, y == y0 + 1 ? WALL : PLANKS);
                c.set(x, y, z1, y == y0 + 1 ? WALL : PLANKS);
            }
            for (int z = z0; z <= z1; z++)
            {
                c.set(x0, y, z, y == y0 + 1 ? WALL : PLANKS);
                c.set(x1, y, z, y == y0 + 1 ? WALL : PLANKS);
            }
            c.set(x0, y, z0, FRAME, Direction.UP);
            c.set(x1, y, z0, FRAME, Direction.UP);
            c.set(x0, y, z1, FRAME, Direction.UP);
            c.set(x1, y, z1, FRAME, Direction.UP);
        }

        boolean ridgeAlongX = x1 - x0 >= z1 - z0;
        windows(c, x0, z0, x1, z1, y0 + 2);
        door(c, x0, z0, x1, z1, y0, ridgeAlongX);
        roof(c, x0, z0, x1, z1, top, ridgeAlongX);
    }

    private static void windows(RoomCanvas c, int x0, int z0, int x1, int z1, int y)
    {
        for (int x = x0 + 2; x < x1 - 1; x += 2)
        {
            c.set(x, y, z0, WINDOW);
            c.set(x, y, z1, WINDOW);
        }
        for (int z = z0 + 2; z < z1 - 1; z += 2)
        {
            c.set(x0, y, z, WINDOW);
            c.set(x1, y, z, WINDOW);
        }
    }

    private static void door(RoomCanvas c, int x0, int z0, int x1, int z1, int y0, boolean onLowZ)
    {
        if (onLowZ)
        {
            int x = (x0 + x1) / 2;
            c.set(x, y0 + 1, z0, DOOR, Direction.SOUTH);
            c.set(x, y0 + 2, z0, DOOR, Direction.SOUTH, true);
        }
        else
        {
            int z = (z0 + z1) / 2;
            c.set(x0, y0 + 1, z, DOOR, Direction.EAST);
            c.set(x0, y0 + 2, z, DOOR, Direction.EAST, true);
        }
    }

    private static void roof(RoomCanvas c, int x0, int z0, int x1, int z1, int top, boolean ridgeAlongX)
    {
        int low = ridgeAlongX ? z0 - 1 : x0 - 1;
        int high = ridgeAlongX ? z1 + 1 : x1 + 1;
        int from = (ridgeAlongX ? x0 : z0) - 1;
        int to = (ridgeAlongX ? x1 : z1) + 1;
        for (int step = 0; low + step <= high - step; step++)
        {
            int a = low + step;
            int b = high - step;
            int y = top + step;
            for (int along = from; along <= to; along++)
            {
                if (a == b)
                {
                    roofCell(c, ridgeAlongX, along, y, a, ROOF_SLAB, Direction.DOWN);
                    continue;
                }
                roofCell(c, ridgeAlongX, along, y, a, ROOF_STAIRS, ridgeAlongX ? Direction.SOUTH : Direction.EAST);
                roofCell(c, ridgeAlongX, along, y, b, ROOF_STAIRS, ridgeAlongX ? Direction.NORTH : Direction.WEST);
                if (step > 0 && along > from && along < to && (along == from + 1 || along == to - 1))
                {
                    for (int across = a + 1; across < b; across++)
                        roofCell(c, ridgeAlongX, along, y, across, PLANKS, Direction.DOWN);
                }
            }
        }
    }

    private static void roofCell(RoomCanvas c, boolean ridgeAlongX, int along, int y, int across, DungeonMaterial material, Direction facing)
    {
        if (ridgeAlongX)
            c.set(along, y, across, material, facing);
        else
            c.set(across, y, along, material, facing);
    }

    /**
     * A tree growing downward from a ceiling: its roots grip the ceiling and its crown hangs above head height, with lanterns under it like fruit.
     */
    static void invertedTree(RoomCanvas c, int x, int z, int top, int crown)
    {
        int center = TREE_CLEARANCE + crown;
        if (top - center < TREE_MIN_TRUNK)
            return;

        for (int y = top; y >= center; y--)
            c.set(x, y, z, LOG, Direction.UP);

        for (var side : Direction.Plane.HORIZONTAL)
        {
            int reach = 2 + c.random.nextInt(3);
            for (int step = 1; step <= reach; step++)
                c.set(x + side.getStepX() * step, top, z + side.getStepZ() * step, step < reach ? LOG : ROOTS, side);
        }

        for (int dy = -crown; dy <= crown; dy++)
        {
            for (int dz = -crown - 1; dz <= crown + 1; dz++)
            {
                for (int dx = -crown - 1; dx <= crown + 1; dx++)
                {
                    int distance = dx * dx + dz * dz + dy * dy * 2;
                    int limit = (crown + 1) * (crown + 1);
                    boolean thin = distance > limit - crown && c.random.nextInt(3) == 0;
                    if (distance <= limit && !thin && c.get(x + dx, center + dy, z + dz).isOpen())
                        c.set(x + dx, center + dy, z + dz, LEAVES);
                }
            }
        }

        for (var side : Direction.Plane.HORIZONTAL)
        {
            int lx = x + side.getStepX() * crown;
            int lz = z + side.getStepZ() * crown;
            int below = center - crown - 1;
            if (below >= TREE_CLEARANCE - 1 && c.get(lx, below, lz).isOpen())
                c.set(lx, below, lz, LANTERN, Direction.DOWN, true);
        }
    }

    /**
     * Light from under the water makes a pool read as toxic from afar, and shows where it is in the dark.
     */
    static void glowingPool(RoomCanvas c, int x0, int z0, int x1, int z1, int depth)
    {
        c.pool(x0, z0, x1, z1, depth);
        for (int z = z0; z <= z1; z++)
        {
            for (int x = x0; x <= x1; x++)
            {
                if ((x + z) % POOL_LIGHT_SPACING == 0)
                    c.set(x, -depth - 1, z, GLOW);
            }
        }
    }

    /**
     * A giant mushroom with a burning underside.
     */
    static void fieryMushroom(RoomCanvas c, int x, int z, int y0, int height, int radius)
    {
        int capY = y0 + height;
        for (int y = y0; y <= capY; y++)
        {
            c.set(x, y, z, STEM, Direction.UP);
            if (radius >= 4 && y < capY - 1)
            {
                c.set(x + 1, y, z, STEM, Direction.UP);
                c.set(x, y, z + 1, STEM, Direction.UP);
                c.set(x + 1, y, z + 1, STEM, Direction.UP);
            }
        }

        for (int dy = 0; dy <= radius; dy++)
        {
            int ring = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            for (int dz = -ring; dz <= ring; dz++)
            {
                for (int dx = -ring; dx <= ring; dx++)
                {
                    if (dx * dx + dz * dz > ring * ring + 1)
                        continue;
                    boolean shell = dx * dx + dz * dz >= (ring - 1) * (ring - 1) || dy == radius;
                    if (!shell && dy > 0)
                        continue;

                    var material = dy == 0 ? CAP_GILL : c.random.nextInt(7) == 0 ? CAP_GLOW : CAP;
                    c.set(x + dx, capY + dy, z + dz, material);
                }
            }
        }

        c.set(x, capY + radius + 1, z, CAMPFIRE);
        for (var side : Direction.Plane.HORIZONTAL)
            c.set(x + side.getStepX() * Math.max(1, radius - 1), capY - 1, z + side.getStepZ() * Math.max(1, radius - 1), CAP_GLOW);
    }

    /**
     * Fungal growth spreading on whatever floor is there.
     */
    static void infest(RoomCanvas c, int y, int chance)
    {
        for (int z = 0; z <= c.d; z++)
        {
            for (int x = 0; x <= c.w; x++)
            {
                if (c.random.nextInt(chance) == 0 && c.get(x, y, z).isCube() && !c.get(x, y, z).isOpen() && c.get(x, y + 1, z).isOpen())
                {
                    c.set(x, y, z, SOIL);
                    if (c.random.nextInt(3) == 0)
                        c.set(x, y + 1, z, PLANT);
                }
            }
        }
    }
}
