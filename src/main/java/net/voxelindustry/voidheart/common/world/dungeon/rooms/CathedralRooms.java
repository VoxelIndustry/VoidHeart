package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

final class CathedralRooms
{
    private CathedralRooms()
    {
    }

    static void nave(RoomCanvas c)
    {
        Architecture.walls(c);
        int length = c.length();
        int breadth = c.breadth();
        int aisle = Math.max(2, breadth / 4);
        int capital = Math.max(4, c.h * 2 / 3);

        vault(c, breadth);
        for (int u = 4; u < length; u += 4)
        {
            for (int v : new int[] { aisle, breadth - aisle })
            {
                c.setUV(u, 1, v, METAL_CUT);
                RoomShapes.columnUV(c, u, v, 2, capital - 1, COLUMN);
                c.setUV(u, capital, v, WALL_TRIM);
            }
            if (c.h >= 12)
                gallery(c, u, aisle, breadth, capital);
        }
        for (int u = 2; u < length; u += 4)
        {
            int top = Math.max(4, c.h - 4);
            RoomShapes.lancet(c, u, 0, 3, top, c.h >= 12 ? 1 : 0, WINDOW);
            RoomShapes.lancet(c, u, breadth, 3, top, c.h >= 12 ? 1 : 0, WINDOW);
        }

        c.fillUV(1, 1, breadth / 2 - 1, length - 1, 1, breadth / 2 + 1, RUNNER);
        pews(c, 3, length - 7, aisle + 1, breadth / 2 - 2, breadth / 2 + 2, breadth - aisle - 1);
        altarEnd(c, length, breadth);
        for (int u = 6; u < length - 2; u += 8)
            hang(c, u, breadth / 2, c.h / 3);
    }

    private static void vault(RoomCanvas c, int breadth)
    {
        int rise = Math.min(c.h / 2, 5);
        for (int v = 1; v < breadth; v++)
        {
            double t = Math.abs(v - breadth / 2.0) / (breadth / 2.0);
            int low = c.h - 1 - (int) Math.round((1 - (1 - t) * (1 - t)) * 0 + t * t * rise);
            for (int u = 1; u < c.length(); u++)
            {
                for (int y = low + 1; y < c.h; y++)
                    c.setUV(u, y, v, CEILING);
                if (u % 4 == 0)
                    c.setUV(u, low, v, WALL_TRIM);
            }
        }
    }

    private static void gallery(RoomCanvas c, int u, int aisle, int breadth, int capital)
    {
        int y = capital / 2 + 2;
        for (int du = -3; du <= 0; du++)
        {
            for (int v = 1; v < aisle; v++)
            {
                c.setUV(u + du, y, v, SLAB);
                c.setUV(u + du, y, breadth - v, SLAB);
            }
            c.setUV(u + du, y, aisle, METAL_SLAB);
            c.setUV(u + du, y, breadth - aisle, METAL_SLAB);
        }
    }

    private static void pews(RoomCanvas c, int fromU, int toU, int leftFrom, int leftTo, int rightFrom, int rightTo)
    {
        for (int u = fromU; u <= toU; u += 2)
        {
            for (int v = leftFrom; v <= leftTo; v++)
                c.setUV(u, 1, v, STAIRS, c.back());
            for (int v = rightFrom; v <= rightTo; v++)
                c.setUV(u, 1, v, STAIRS, c.back());
        }
    }

    private static void altarEnd(RoomCanvas c, int length, int breadth)
    {
        c.fillUV(length - 5, 1, 1, length - 1, 1, breadth - 1, FLOOR_TRIM);
        int mid = breadth / 2;
        c.fillUV(length - 3, 2, mid - 1, length - 3, 2, mid + 1, METAL_CUT);
        c.setUV(length - 3, 3, mid - 1, CANDLE);
        c.setUV(length - 3, 3, mid, CANDLE);
        c.setUV(length - 3, 3, mid + 1, CANDLE);

        int radius = Math.min(breadth / 2 - 1, c.h / 3);
        int center = Math.max(4, c.h / 2 + 1);
        for (int y = -radius; y <= radius; y++)
        {
            for (int v = -radius; v <= radius; v++)
            {
                if (v * v + y * y <= radius * radius)
                    c.setUV(length, center + y, mid + v, WINDOW);
            }
        }
    }

    private static void hang(RoomCanvas c, int u, int v, int drop)
    {
        if (c.alongX)
            c.hangingLantern(u, v, drop);
        else
            c.hangingLantern(v, u, drop);
    }

    static void chapel(RoomCanvas c)
    {
        Architecture.walls(c);
        int length = c.length();
        int breadth = c.breadth();
        vault(c, breadth);

        pews(c, 2, length - 5, 1, breadth / 2 - 1, breadth / 2 + 1, breadth - 1);
        c.fillUV(length - 3, 1, 1, length - 1, 1, breadth - 1, FLOOR_TRIM);
        c.setUV(length - 2, 2, breadth / 2, LECTERN, c.back());
        c.setUV(length - 2, 2, 1, CANDLE);
        c.setUV(length - 2, 2, breadth - 1, CANDLE);
        for (int y = 3; y < Math.min(c.h - 1, 7); y++)
            c.setUV(length - 1, y, breadth / 2, METAL_CUT);
        c.fillUV(length - 1, Math.min(c.h - 2, 5), breadth / 2 - 1, length - 1, Math.min(c.h - 2, 5), breadth / 2 + 1, METAL_CUT);

        for (int u = 2; u < length - 1; u += 4)
        {
            RoomShapes.lancet(c, u, 0, 2, Math.max(3, c.h - 3), 0, WINDOW);
            RoomShapes.lancet(c, u, breadth, 2, Math.max(3, c.h - 3), 0, WINDOW);
        }
        hang(c, length / 2, breadth / 2, 1);
    }

    static void crypt(RoomCanvas c)
    {
        Architecture.monument(c);
        for (int x = 4; x < c.w; x += 4)
        {
            for (int z = 4; z < c.d; z += 4)
                RoomShapes.column(c, x, z, 1, c.h - 1, COLUMN);
        }

        for (int u = 2; u < c.length() - 3; u += 4)
        {
            for (int v : new int[] { 2, c.breadth() - 2 })
            {
                c.fillUV(u, 1, v, u + 2, 1, v, WALL_TRIM);
                c.fillUV(u, 2, v, u + 2, 2, v, c.random.nextInt(4) == 0 ? METAL_SLAB : SLAB);
                c.setUV(u + 1, 3, v, CANDLE);
            }
        }

        for (int x : new int[] { 1, c.w - 1 })
        {
            for (int z : new int[] { 1, c.d - 1 })
                c.set(x, c.h - 1, z, COBWEB);
        }
        if (c.h >= 8)
            c.hangingLantern(c.w / 2, c.d / 2, 2);
        else
            c.set(c.w / 2, 1, c.d / 2, LANTERN);
    }

    static void cloister(RoomCanvas c)
    {
        c.open(FLOOR);
        int ring = 3;
        int top = Math.min(c.h - 1, 6);
        c.fill(ring + 1, 0, ring + 1, c.w - ring - 1, 0, c.d - ring - 1, SOIL);
        for (int x = ring + 1; x < c.w - ring; x++)
        {
            for (int z = ring + 1; z < c.d - ring; z++)
            {
                if (c.random.nextInt(3) == 0)
                    c.set(x, 1, z, PLANT);
            }
        }
        for (int x = 0; x <= c.w; x += 4)
        {
            RoomShapes.column(c, x, ring, 1, top, COLUMN);
            RoomShapes.column(c, x, c.d - ring, 1, top, COLUMN);
        }
        for (int z = 0; z <= c.d; z += 4)
        {
            RoomShapes.column(c, ring, z, 1, top, COLUMN);
            RoomShapes.column(c, c.w - ring, z, 1, top, COLUMN);
        }
        c.fill(0, top + 1, 0, c.w, top + 1, ring, SLAB);
        c.fill(0, top + 1, c.d - ring, c.w, top + 1, c.d, SLAB);
        c.fill(0, top + 1, 0, ring, top + 1, c.d, SLAB);
        c.fill(c.w - ring, top + 1, 0, c.w, top + 1, c.d, SLAB);
        for (int x = 2; x < c.w; x += 6)
        {
            c.set(x, top, 1, LANTERN, Direction.DOWN, true);
            c.set(x, top, c.d - 1, LANTERN, Direction.DOWN, true);
        }
    }

    static void belfry(RoomCanvas c)
    {
        Architecture.walls(c);
        int y = 2;
        var turn = Direction.EAST;
        int x = 1;
        int z = 1;
        while (y < c.h - 3)
        {
            c.set(x, y, z, WALL_TRIM);
            int nextX = x + turn.getStepX();
            int nextZ = z + turn.getStepZ();
            if (nextX < 1 || nextX > c.w - 1 || nextZ < 1 || nextZ > c.d - 1)
            {
                turn = turn.getClockWise();
                continue;
            }
            x = nextX;
            z = nextZ;
            y++;
        }

        int bellY = c.h - 3;
        RoomShapes.column(c, c.w / 2, c.d / 2, bellY + 1, c.h - 1, CHAIN);
        c.set(c.w / 2, bellY, c.d / 2, BELL);
        RoomShapes.lancet(c, c.length() / 2, 0, c.h / 2, c.h - 2, 1, GRATE);
        RoomShapes.lancet(c, c.length() / 2, c.breadth(), c.h / 2, c.h - 2, 1, GRATE);
        c.set(c.w / 2, 1, c.d / 2, LANTERN);
    }
}
