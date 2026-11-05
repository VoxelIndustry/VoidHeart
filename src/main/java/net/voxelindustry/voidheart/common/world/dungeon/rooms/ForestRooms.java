package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Every room of this theme holds a glowing pool of toxic water, so that each level of the dungeon has one.
 * Rooms stay sparse: a few large trees hanging from the ceiling with clear air under them read better than many.
 */
final class ForestRooms
{
    private static final int TREE_SPACING = 12;
    private static final int LAMP_POST_SPACING = 8;

    private ForestRooms()
    {
    }

    private static void overgrow(RoomCanvas c, int y0)
    {
        for (int z = 1; z < c.d; z++)
        {
            for (int x = 1; x < c.w; x++)
            {
                if (c.get(x, y0, z) != SOIL || !c.get(x, y0 + 1, z).isOpen())
                    continue;
                int roll = c.random.nextInt(14);
                if (roll == 0)
                    c.set(x, y0 + 1, z, PLANT);
                else if (roll == 1 && c.get(x, y0 + 2, z).isOpen())
                {
                    c.set(x, y0 + 1, z, TALL_PLANT);
                    c.set(x, y0 + 2, z, TALL_PLANT, Direction.DOWN, true);
                }
            }
        }
    }

    private static void canopyCeiling(RoomCanvas c)
    {
        c.fill(0, c.h, 0, c.w, c.h, c.d, ROCK_NATIVE);
        for (int z = 1; z < c.d; z++)
        {
            for (int x = 1; x < c.w; x++)
            {
                if (c.random.nextInt(16) == 0)
                    c.set(x, c.h - 1, z, HANGING);
            }
        }
    }

    /**
     * What remains of a garden: a wall low enough to see over, with lamp posts along it.
     */
    private static void gardenWall(RoomCanvas c)
    {
        for (int u = 0; u <= c.length(); u++)
        {
            int height = 1 + c.random.nextInt(2);
            for (int y = 1; y <= height; y++)
            {
                c.setUV(u, y, 0, y == height && u % 6 == 0 ? METAL_CUT : WALL);
                if (u % 4 != 0 && y == 1)
                    c.setUV(u, y, c.breadth(), WALL);
            }
            if (u % LAMP_POST_SPACING == 0)
            {
                RoomShapes.columnUV(c, u, 0, 1, 3, FRAME);
                c.setUV(u, 4, 0, LANTERN, Direction.DOWN);
            }
        }
    }

    private static void hangTrees(RoomCanvas c)
    {
        int crown = Math.min(4, 2 + c.h / 8);
        for (int x = TREE_SPACING / 2; x < c.w; x += TREE_SPACING)
        {
            for (int z = TREE_SPACING / 2; z < c.d; z += TREE_SPACING)
                RoomShapes.invertedTree(c, x + c.random.nextInt(3) - 1, z + c.random.nextInt(3) - 1, c.h - 1, crown);
        }
    }

    static void grove(RoomCanvas c)
    {
        c.open(SOIL);
        c.fill(0, -1, 0, c.w, -1, c.d, SUBSOIL);
        canopyCeiling(c);
        gardenWall(c);
        hangTrees(c);

        int half = Math.max(1, Math.min(c.w, c.d) / 5);
        RoomShapes.glowingPool(c, c.w / 3 - half, c.d / 3 - half, c.w / 3 + half, c.d / 3 + half, 2);
        RoomShapes.glowingPool(c, 2 * c.w / 3 - half, 2 * c.d / 3 - half, 2 * c.w / 3 + half, 2 * c.d / 3 + half, 2);
        overgrow(c, 0);
    }

    static void cabin(RoomCanvas c)
    {
        c.open(SOIL);
        c.fill(0, -1, 0, c.w, -1, c.d, SUBSOIL);
        canopyCeiling(c);

        int inset = 1 + c.random.nextInt(2);
        int depth = Math.min(c.d - inset - 5, 8);
        int width = Math.min(c.w - inset - 1, 10);
        if (width >= 6 && depth >= 6 && c.h >= 8)
            RoomShapes.house(c, inset, inset, inset + width - 1, inset + depth - 1, 0, 3);

        RoomShapes.glowingPool(c, Math.max(2, c.w - 4), Math.max(2, c.d - 4), c.w - 2, c.d - 2, 2);
        overgrow(c, 0);
        c.set(c.w / 2, 1, c.d - 1, LANTERN, Direction.DOWN);
        if (c.h >= 12)
            RoomShapes.invertedTree(c, c.w - 3, 3, c.h - 1, 2);
    }

    static void pond(RoomCanvas c)
    {
        c.shell(ROCK_NATIVE, SOIL, ROCK_NATIVE);
        c.fill(0, -1, 0, c.w, -1, c.d, SUBSOIL);
        int margin = Math.max(1, Math.min(c.w, c.d) / 5);
        RoomShapes.glowingPool(c, margin + 1, margin + 1, c.w - margin - 1, c.d - margin - 1, 3);
        for (int z = 1; z < c.d; z++)
        {
            for (int x = 1; x < c.w; x++)
            {
                if (c.random.nextInt(10) == 0)
                    c.set(x, c.h - 1, z, HANGING);
            }
        }
        c.set(1, 1, 1, LANTERN, Direction.DOWN);
        c.set(c.w - 1, 1, c.d - 1, LANTERN, Direction.DOWN);
    }

    /**
     * Stepped ledges climbing from the entrance level, each with its own pool.
     */
    static void terraces(RoomCanvas c)
    {
        c.open(SOIL);
        c.fill(0, -1, 0, c.w, -1, c.d, SUBSOIL);
        canopyCeiling(c);

        int ledges = Math.max(1, Math.min(3, c.h / 4));
        for (int ledge = 1; ledge <= ledges; ledge++)
        {
            int inset = ledge * 3;
            int y = ledge * 2;
            if (c.w - inset < 4 || c.d - inset < 4)
                break;
            c.fill(c.w - inset, 1, 0, c.w, y, c.d, SUBSOIL);
            c.fill(c.w - inset, y, 0, c.w, y, c.d, SOIL);
            for (int z = 2; z < c.d - 2; z += 5)
            {
                c.set(c.w - inset + 1, y, z, FLUID);
                c.set(c.w - inset + 1, y - 1, z, GLOW);
                c.set(c.w - inset + 1, y, z + 1, FLUID);
                c.set(c.w - inset + 1, y - 1, z + 1, POOL_BED);
            }
        }
        int half = Math.max(1, Math.min(c.w, c.d) / 6);
        RoomShapes.glowingPool(c, 2, c.d / 2 - half, 2 + half * 2, c.d / 2 + half, 2);
        overgrow(c, 0);
        c.set(1, 1, 1, LANTERN, Direction.DOWN);
        c.set(1, 1, c.d - 1, LANTERN, Direction.DOWN);
    }
}
