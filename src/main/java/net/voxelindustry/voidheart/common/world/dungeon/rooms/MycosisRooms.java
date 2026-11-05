package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Villages overrun by burning fungus. Real fire would spread through the wooden houses and burn them away, so only campfires and magma burn here.
 */
final class MycosisRooms
{
    private static final int HOUSE_MIN_SIDE = 6;
    private static final int HOUSE_MAX_SIDE = 9;

    private MycosisRooms()
    {
    }

    private static void lampPost(RoomCanvas c, int x, int z)
    {
        for (int y = 1; y <= 3; y++)
            c.set(x, y, z, WOOD_FENCE);
        c.set(x, 4, z, LANTERN);
    }

    private static void well(RoomCanvas c, int x, int z)
    {
        c.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, METAL_CUT);
        c.set(x, 1, z, FLUID);
        c.set(x, 0, z, FLUID);
        c.set(x, -1, z, POOL_BED);
        for (int[] corner : new int[][] { { -1, -1 }, { 1, 1 } })
        {
            for (int y = 2; y <= 3; y++)
                c.set(x + corner[0], y, z + corner[1], WOOD_FENCE);
        }
        c.fill(x - 1, 4, z - 1, x + 1, 4, z + 1, ROOF_SLAB);
        c.calmSpots.add(new int[] { x, z, 2 });
    }

    private static void housesAround(RoomCanvas c, int streetZ)
    {
        int headroom = 3 + HOUSE_MAX_SIDE / 2 + 2;
        if (c.h < headroom)
            return;

        for (int side = 0; side < 2; side++)
        {
            boolean north = side == 0;
            int cursor = 1;
            while (cursor + HOUSE_MIN_SIDE < c.w)
            {
                int width = Math.min(HOUSE_MIN_SIDE + c.random.nextInt(HOUSE_MAX_SIDE - HOUSE_MIN_SIDE + 1), c.w - cursor - 1);
                int depth = HOUSE_MIN_SIDE + c.random.nextInt(2);
                int z0 = north ? streetZ - 3 - depth : streetZ + 3;
                if (z0 >= 1 && z0 + depth < c.d && width >= HOUSE_MIN_SIDE)
                    RoomShapes.house(c, cursor, z0, cursor + width - 1, z0 + depth - 1, 0, 3);
                cursor += width + 2 + c.random.nextInt(3);
            }
        }
    }

    private static void mushrooms(RoomCanvas c, int count)
    {
        for (int i = 0; i < count; i++)
        {
            int radius = 2 + c.random.nextInt(Math.max(1, Math.min(4, c.h / 5)));
            int height = Math.max(3, Math.min(c.h - radius - 3, 4 + c.random.nextInt(Math.max(1, c.h / 2))));
            if (height + radius + 2 >= c.h || c.w <= radius * 2 + 2 || c.d <= radius * 2 + 2)
                continue;
            int x = radius + 1 + c.random.nextInt(c.w - radius * 2 - 1);
            int z = radius + 1 + c.random.nextInt(c.d - radius * 2 - 1);
            RoomShapes.fieryMushroom(c, x, z, 1, height, radius);
        }
    }

    static void village(RoomCanvas c)
    {
        c.open(FLOOR);
        c.fill(0, -1, 0, c.w, -1, c.d, ROCK_NATIVE);
        int streetZ = c.d / 2;
        c.fill(0, 0, streetZ - 2, c.w, 0, streetZ + 2, FLOOR_TRIM);
        housesAround(c, streetZ);
        well(c, c.w / 2, streetZ);
        for (int x = 4; x < c.w - 1; x += 8)
        {
            lampPost(c, x, streetZ - 2);
            lampPost(c, x + 4, streetZ + 2);
        }
        mushrooms(c, Math.max(2, c.w * c.d / 120));
        RoomShapes.infest(c, 0, 5);
    }

    static void hut(RoomCanvas c)
    {
        c.open(FLOOR);
        c.fill(0, -1, 0, c.w, -1, c.d, ROCK_NATIVE);
        int width = Math.min(c.w - 3, HOUSE_MAX_SIDE);
        int depth = Math.min(c.d - 3, HOUSE_MAX_SIDE - 2);
        if (width >= HOUSE_MIN_SIDE && depth >= HOUSE_MIN_SIDE && c.h >= 8)
            RoomShapes.house(c, 1, 1, width, depth, 0, 3);
        if (c.h >= 8)
            RoomShapes.fieryMushroom(c, c.w - 3, c.d - 3, 1, Math.min(c.h - 6, 5), 2);
        c.set(c.w - 1, 1, 1, HAY);
        c.set(c.w - 1, 2, 1, HAY);
        c.set(c.w - 1, 1, 2, HAY);
        lampPost(c, 1, c.d - 1);
        RoomShapes.infest(c, 0, 4);
    }

    static void farm(RoomCanvas c)
    {
        c.open(SOIL);
        c.fill(0, -1, 0, c.w, -1, c.d, ROCK_NATIVE);
        for (int u = 1; u < c.length(); u++)
        {
            for (int v = 1; v < c.breadth(); v++)
            {
                if (v % 3 == 0)
                    c.setUV(u, 0, v, FLOOR_TRIM);
                else if (c.random.nextInt(3) != 0)
                    c.setUV(u, 1, v, PLANT);
            }
        }
        for (int u = 0; u <= c.length(); u++)
        {
            c.setUV(u, 1, 0, WOOD_FENCE);
            c.setUV(u, 1, c.breadth(), WOOD_FENCE);
        }
        for (int v = 0; v <= c.breadth(); v++)
        {
            c.setUV(0, 1, v, WOOD_FENCE);
            c.setUV(c.length(), 1, v, WOOD_FENCE);
        }
        lampPost(c, 2, 2);
        lampPost(c, c.w - 2, c.d - 2);
        c.set(1, 2, c.d - 1, HAY);
        mushrooms(c, Math.max(1, c.w * c.d / 200));
    }

    /**
     * The one place with lava, in a bare stone pit far from anything that burns.
     */
    static void pit(RoomCanvas c)
    {
        c.shell(ROCK_NATIVE, ROCK_NATIVE, ROCK_NATIVE);
        int margin = Math.max(2, Math.min(c.w, c.d) / 4);
        c.pool(margin, margin, c.w - margin, c.d - margin, 3);
        for (int u = 1; u < c.length(); u += 3)
        {
            c.setUV(u, 0, 1, CAP_GILL);
            c.setUV(u, 0, c.breadth() - 1, CAP_GILL);
        }
        c.set(1, 1, 1, CAMPFIRE);
        c.set(c.w - 1, 1, c.d - 1, CAMPFIRE);
        for (int x : new int[] { 1, c.w - 1 })
        {
            for (int z : new int[] { 1, c.d - 1 })
                c.set(x, c.h - 1, z, CAP_GLOW);
        }
    }
}
