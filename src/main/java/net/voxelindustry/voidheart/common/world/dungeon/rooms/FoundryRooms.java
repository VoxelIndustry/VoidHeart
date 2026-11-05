package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

final class FoundryRooms
{
    private FoundryRooms()
    {
    }

    private static void tank(RoomCanvas c, int cx, int cz, int radius, int y0, int y1)
    {
        for (int y = y0; y <= y1; y++)
        {
            var material = (y - y0) % 4 == 3 || y == y1 ? METAL_CUT : PIPE;
            RoomShapes.disc(c, cx, y, cz, radius, material);
        }
        RoomShapes.disc(c, cx, y1 + 1, cz, Math.max(1, radius - 1), METAL_SLAB);
    }

    private static void catwalk(RoomCanvas c, int y)
    {
        int reach = 3;
        for (int x = 1; x < c.w; x++)
        {
            for (int z = 1; z < c.d; z++)
            {
                boolean ring = x <= reach || z <= reach || x >= c.w - reach || z >= c.d - reach;
                if (!ring)
                    continue;
                c.set(x, y, z, METAL_SLAB);
                boolean rail = x == reach || z == reach || x == c.w - reach || z == c.d - reach;
                if (rail && !(x < reach || z < reach || x > c.w - reach || z > c.d - reach))
                    c.set(x, y + 1, z, GRATE);
            }
        }
    }

    static void boilerHall(RoomCanvas c)
    {
        Architecture.monument(c);
        int length = c.length();
        int breadth = c.breadth();

        for (int u = 8; u < length - 3; u += 12)
        {
            int radius = Math.min(4, breadth / 4);
            int top = Math.max(4, c.h - 5);
            if (c.alongX)
                tank(c, u, breadth / 2, radius, 1, top);
            else
                tank(c, breadth / 2, u, radius, 1, top);
            for (int v = breadth / 2; v < breadth; v++)
                c.setUV(u, top + 1, v, PIPE_THIN, c.side());
        }

        for (int u = 2; u < length; u += 4)
        {
            c.setUV(u, 1, 1, FORGE, c.side());
            c.setUV(u, 1, breadth - 1, FURNACE, c.side().getOpposite());
            RoomShapes.columnUV(c, u + 2, 1, 1, c.h - 1, PIPE);
            RoomShapes.columnUV(c, u + 2, breadth - 1, 1, c.h - 1, METAL_CUT);
        }

        if (c.h >= 12)
            catwalk(c, c.h / 2);
        lights(c, 6);
    }

    static void smelter(RoomCanvas c)
    {
        Architecture.monument(c);
        int mx = c.w / 2;
        int mz = c.d / 2;
        int half = Math.max(1, Math.min(c.w, c.d) / 4);
        c.pool(mx - half, mz - half, mx + half, mz + half, 2);
        c.fill(mx - half - 1, 1, mz - half - 1, mx + half + 1, 1, mz - half - 1, METAL_CUT);
        c.fill(mx - half - 1, 1, mz + half + 1, mx + half + 1, 1, mz + half + 1, METAL_CUT);
        c.fill(mx - half - 1, 1, mz - half - 1, mx - half - 1, 1, mz + half + 1, METAL_CUT);
        c.fill(mx + half + 1, 1, mz - half - 1, mx + half + 1, 1, mz + half + 1, METAL_CUT);

        for (int z = 2; z < c.d - 1; z += 2)
        {
            c.set(1, 1, z, FORGE, Direction.EAST);
            c.set(1, 2, z, FURNACE, Direction.EAST);
            c.set(c.w - 1, 1, z, FURNACE, Direction.WEST);
        }
        c.set(2, 1, 1, ANVIL, Direction.EAST);
        c.set(c.w - 2, 1, c.d - 1, CAULDRON);

        for (int y = c.h - 1; y >= Math.max(3, c.h / 2); y--)
            c.set(mx, y, mz, CHAIN, Direction.UP);
        c.set(mx, Math.max(3, c.h / 2) - 1, mz, METAL_CUT);
        lights(c, 4);
    }

    static void gantry(RoomCanvas c)
    {
        Architecture.walls(c);
        int length = c.length();
        int breadth = c.breadth();
        int beam = c.h - 2;

        for (int u = 4; u < length; u += 8)
        {
            for (int v = 1; v < breadth; v++)
                c.setUV(u, beam, v, METAL_WALL);
            RoomShapes.columnUV(c, u, 1, 1, beam - 1, METAL_CUT);
            RoomShapes.columnUV(c, u, breadth - 1, 1, beam - 1, METAL_CUT);

            int load = breadth / 2 + c.random.nextInt(3) - 1;
            int bottom = Math.max(3, beam - 3 - c.random.nextInt(Math.max(1, c.h / 3)));
            for (int y = beam - 1; y > bottom; y--)
                c.setUV(u, y, load, CHAIN, Direction.UP);
            c.setUV(u, bottom, load, c.random.nextInt(8) == 0 ? METAL_BLOCK : METAL_CUT);
        }

        for (int u = 1; u < length; u++)
        {
            c.setUV(u, 0, breadth / 3, METAL_CUT);
            c.setUV(u, 0, breadth - breadth / 3, METAL_CUT);
        }
        for (int u = 2; u < length - 1; u += 3)
        {
            if (c.random.nextBoolean())
                c.setUV(u, 1, 1, BARREL, Direction.UP);
            if (c.random.nextBoolean())
                c.setUV(u, 1, breadth - 1, BARREL, Direction.UP);
        }
        lights(c, 2);
    }

    static void store(RoomCanvas c)
    {
        Architecture.monument(c);
        for (int x = 2; x < c.w - 1; x += 3)
        {
            for (int z = 2; z < c.d - 1; z++)
            {
                if (z % 4 == 0)
                    continue;
                int stack = 1 + c.random.nextInt(Math.min(3, c.h - 1));
                for (int y = 1; y <= stack; y++)
                    c.set(x, y, z, BARREL, Direction.UP);
            }
        }
        for (int z = 1; z < c.d; z++)
            c.set(c.w / 2, c.h - 1, z, PIPE_THIN, Direction.SOUTH);
        c.set(1, 1, 1, CAULDRON);
        lights(c, 1);
    }

    private static void lights(RoomCanvas c, int drop)
    {
        for (int x = 3; x < c.w - 1; x += 8)
        {
            for (int z = 3; z < c.d - 1; z += 8)
                c.hangingLantern(x, z, drop);
        }
    }
}
