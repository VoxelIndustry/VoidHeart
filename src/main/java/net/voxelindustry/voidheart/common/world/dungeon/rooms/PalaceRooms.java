package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

final class PalaceRooms
{
    private PalaceRooms()
    {
    }

    private static void colonnade(RoomCanvas c, int v, int top)
    {
        for (int u = 2; u < c.length() - 1; u += 4)
        {
            c.setUV(u, 1, v, METAL_CUT);
            RoomShapes.columnUV(c, u, v, 2, top - 1, COLUMN);
            c.setUV(u, top, v, WALL_TRIM);
            c.setUV(u, top + 1, v, METAL_SLAB);
        }
    }

    private static void coffers(RoomCanvas c)
    {
        for (int x = 1; x < c.w; x++)
        {
            for (int z = 1; z < c.d; z++)
            {
                if (x % 4 == 0 || z % 4 == 0)
                    c.set(x, c.h - 1, z, WALL_TRIM);
            }
        }
    }

    static void throneHall(RoomCanvas c)
    {
        Architecture.walls(c);
        int length = c.length();
        int breadth = c.breadth();
        int inset = Math.max(2, breadth / 5);
        int top = Math.max(4, c.h - 3);

        coffers(c);
        colonnade(c, inset, top);
        colonnade(c, breadth - inset, top);
        c.fillUV(1, 1, breadth / 2 - 1, length - 6, 1, breadth / 2 + 1, RUNNER);

        for (int u = 1; u < length; u += 1)
        {
            if (u % 8 >= 2 && inset > 2)
            {
                c.setUV(u, 0, 1, FLUID);
                c.setUV(u, 0, breadth - 1, FLUID);
                c.setUV(u, -1, 1, POOL_BED);
                c.setUV(u, -1, breadth - 1, POOL_BED);
            }
        }

        c.fillUV(length - 5, 1, 1, length - 1, 1, breadth - 1, FLOOR_TRIM);
        c.fillUV(length - 4, 2, breadth / 2 - 2, length - 1, 2, breadth / 2 + 2, METAL_CUT);
        c.setUV(length - 3, 3, breadth / 2, METAL_STAIRS, c.back());
        c.fillUV(length - 2, 3, breadth / 2 - 1, length - 2, 5, breadth / 2 + 1, METAL_CUT);
        c.setUV(length - 2, 6, breadth / 2, c.random.nextBoolean() ? METAL_BLOCK : METAL_CUT);
        c.setUV(length - 4, 3, breadth / 2 - 2, CANDLE);
        c.setUV(length - 4, 3, breadth / 2 + 2, CANDLE);

        for (int u = 4; u < length - 2; u += 4)
        {
            RoomShapes.lancet(c, u, 0, 3, Math.max(4, c.h - 3), 1, WINDOW);
            RoomShapes.lancet(c, u, breadth, 3, Math.max(4, c.h - 3), 1, WINDOW);
            if (u % 8 == 0)
            {
                if (c.alongX)
                    c.hangingLantern(u, breadth / 2, c.h / 3);
                else
                    c.hangingLantern(breadth / 2, u, c.h / 3);
            }
        }
    }

    static void courtyard(RoomCanvas c)
    {
        c.open(FLOOR);
        for (int x = 0; x <= c.w; x++)
        {
            for (int z = 0; z <= c.d; z++)
            {
                if ((x / 2 + z / 2) % 2 == 0)
                    c.set(x, 0, z, FLOOR_TRIM);
            }
        }

        int top = Math.min(c.h - 1, 6);
        for (int x = 0; x <= c.w; x += 4)
        {
            RoomShapes.column(c, x, 0, 1, top, COLUMN);
            RoomShapes.column(c, x, c.d, 1, top, COLUMN);
        }
        for (int z = 0; z <= c.d; z += 4)
        {
            RoomShapes.column(c, 0, z, 1, top, COLUMN);
            RoomShapes.column(c, c.w, z, 1, top, COLUMN);
        }
        c.fill(0, top + 1, 0, c.w, top + 1, 0, WALL_TRIM);
        c.fill(0, top + 1, c.d, c.w, top + 1, c.d, WALL_TRIM);
        c.fill(0, top + 1, 0, 0, top + 1, c.d, WALL_TRIM);
        c.fill(c.w, top + 1, 0, c.w, top + 1, c.d, WALL_TRIM);

        int mx = c.w / 2;
        int mz = c.d / 2;
        int half = Math.max(1, Math.min(c.w, c.d) / 4);
        c.pool(mx - half, mz - half, mx + half, mz + half, 1);
        c.fill(mx - half - 1, 1, mz - half - 1, mx + half + 1, 1, mz - half - 1, METAL_SLAB);
        c.fill(mx - half - 1, 1, mz + half + 1, mx + half + 1, 1, mz + half + 1, METAL_SLAB);
        c.fill(mx - half - 1, 1, mz - half - 1, mx - half - 1, 1, mz + half + 1, METAL_SLAB);
        c.fill(mx + half + 1, 1, mz - half - 1, mx + half + 1, 1, mz + half + 1, METAL_SLAB);
        RoomShapes.column(c, mx, mz, 0, 2, COLUMN);
        c.set(mx, 3, mz, LANTERN);
        c.set(2, 1, 2, PLANT);
        c.set(c.w - 2, 1, 2, PLANT);
        c.set(2, 1, c.d - 2, PLANT);
        c.set(c.w - 2, 1, c.d - 2, PLANT);
    }

    static void bath(RoomCanvas c)
    {
        Architecture.walls(c);
        coffers(c);
        int margin = Math.max(2, Math.min(c.w, c.d) / 4);
        c.pool(margin, margin, c.w - margin, c.d - margin, 3);
        for (int x : new int[] { 1, c.w - 1 })
        {
            for (int z : new int[] { 1, c.d - 1 })
            {
                RoomShapes.column(c, x, z, 1, c.h - 1, COLUMN);
                c.set(x, 1, z, METAL_CUT);
            }
        }
        for (int u = 3; u < c.length() - 2; u += 4)
        {
            c.setUV(u, 1, 1, CANDLE);
            c.setUV(u, 1, c.breadth() - 1, CANDLE);
        }
        c.hangingLantern(c.w / 2, c.d / 2, 1);
    }

    static void tomb(RoomCanvas c)
    {
        Architecture.monument(c);
        int length = c.length();
        int breadth = c.breadth();
        c.fillUV(1, 1, breadth / 2 - 1, length - 1, 1, breadth / 2 + 1, RUNNER);

        int u = length / 2;
        c.fillUV(u - 2, 1, breadth / 2 - 1, u + 2, 1, breadth / 2 + 1, METAL_CUT);
        c.fillUV(u - 2, 2, breadth / 2 - 1, u + 2, 2, breadth / 2 + 1, METAL_SLAB);

        for (int du = 2; du < length - 1; du += 4)
        {
            c.setUV(du, 1, 1, c.random.nextInt(5) == 0 ? METAL_CUT : WALL_TRIM);
            c.setUV(du, 2, 1, CANDLE);
            c.setUV(du, 1, breadth - 1, c.random.nextInt(5) == 0 ? METAL_CUT : WALL_TRIM);
            c.setUV(du, 2, breadth - 1, CANDLE);
        }
        c.set(1, c.h - 1, 1, COBWEB);
        c.set(c.w - 1, c.h - 1, c.d - 1, COBWEB);
        c.set(c.w / 2, c.h - 1, c.d / 2, LANTERN, Direction.DOWN, true);
    }
}
