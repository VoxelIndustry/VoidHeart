package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * The language of massive_pocket, pocket_temple and pocket_tower, which every theme speaks in its own materials:
 * floors gridded every 4 blocks, 8 block storeys, panelled walls with dark inset windows, ribbed ceilings and ring galleries around an atrium.
 */
final class Architecture
{
    private static final int STOREY = 8;
    private static final int LOW_ROOM = STOREY - 1;
    private static final int CORNER_BAY = 4;
    private static final int WINDOW_MODULE = 8;
    private static final int CENTER_PANEL_MIN_LENGTH = 16;
    private static final int KEYSTONE_REACH = 6;
    private static final int GALLERY_WIDTH = 4;
    private static final int SHAFT_RADIUS = 2;
    private static final int LANE_WIDTH = 3;
    private static final int FIRST_LANE = GALLERY_WIDTH + 1;
    private static final int SECOND_LANE = FIRST_LANE + LANE_WIDTH;
    private static final int SHAFT_MIN_SIDE = 28;
    private static final int KEEP_FIRST_FLOOR = 10;
    private static final int KEEP_TOP_FLOOR = 50;
    private static final int KEEP_STAIR_NEAR = 9;
    private static final int KEEP_STAIR_FAR = 17;

    private Architecture()
    {
    }

    // For rooms bringing their own ceiling
    static void walls(RoomCanvas c)
    {
        c.shell(WALL, FLOOR, CEILING);
        gridFloor(c, 0);
        facade(c);
    }

    static void monument(RoomCanvas c)
    {
        walls(c);
        ribs(c, c.h - 1, DungeonSize.CELL);
    }

    static void gridFloor(RoomCanvas c, int y)
    {
        for (int z = 0; z <= c.d; z++)
        {
            for (int x = 0; x <= c.w; x++)
            {
                if (x % DungeonSize.CELL == 0 || z % DungeonSize.CELL == 0)
                    c.set(x, y, z, FLOOR_TRIM);
            }
        }
    }

    private static void facade(RoomCanvas c)
    {
        facadeSide(c, c.w, true, 0);
        facadeSide(c, c.w, true, c.d);
        facadeSide(c, c.d, false, 0);
        facadeSide(c, c.d, false, c.w);
    }

    private static void facadeSide(RoomCanvas c, int length, boolean alongX, int fixed)
    {
        for (int y = 1; y < c.h; y++)
        {
            for (int s = 0; s <= length; s++)
            {
                var material = c.h < LOW_ROOM ? lowWallCell(s, y, c.h) : facadeCell(s, length, y % STOREY);
                if (alongX)
                    c.set(s, y, fixed, material);
                else
                    c.set(fixed, y, s, material);
            }
        }
    }

    private static DungeonMaterial lowWallCell(int s, int y, int height)
    {
        if (y == height - 1 || s % DungeonSize.CELL == 0)
            return WALL_TRIM;
        return WALL;
    }

    /**
     * One storey of the keep's facade, read from its slices: a band at the floor, framed corner bays, 2 wide windows with sills,
     * a large central panel, and a shadow gap under the storey above that only the keystone crosses.
     */
    private static DungeonMaterial facadeCell(int s, int length, int row)
    {
        if (row == 0)
            return WALL;

        int fromCorner = Math.min(s, length - s);
        if (fromCorner <= CORNER_BAY)
        {
            if (row == STOREY - 1)
                return fromCorner == 0 ? PANEL : AIR;
            if (row == 1 || row == STOREY - 2 || fromCorner == 0 || fromCorner == CORNER_BAY)
                return WALL;
            return PANEL;
        }

        int fromCenter = Math.abs(s - length / 2);
        boolean hasCenter = length >= CENTER_PANEL_MIN_LENGTH;
        if (row == STOREY - 1)
            return hasCenter && fromCenter <= KEYSTONE_REACH ? (s % 2 == 0 ? PANEL : WALL) : AIR;
        if (hasCenter && fromCenter <= 2)
            return row == STOREY - 2 && fromCenter == 2 ? WALL : WINDOW;
        if (row == STOREY - 2)
            return WALL;

        return switch ((fromCorner - CORNER_BAY - 1) % WINDOW_MODULE)
        {
            case 0, 5 -> row == 1 ? WALL : PANEL;
            case 1, 4 -> row == 5 ? PANEL : row == 2 ? WALL_TRIM : WALL;
            case 2, 3 -> row == 5 ? PANEL : row == 4 ? WALL : WINDOW;
            default -> WALL;
        };
    }

    // Stairs bracing a slab across the room, as in the temple's corridors
    static void ribs(RoomCanvas c, int y, int spacing)
    {
        for (int u = spacing; u < c.length(); u += spacing)
            ribAt(c, u, y);
    }

    private static void ribAt(RoomCanvas c, int u, int y)
    {
        int last = c.breadth() - 1;
        c.setUV(u, y, 1, STAIRS, c.side().getOpposite(), true);
        c.setUV(u, y, last, STAIRS, c.side(), true);
        for (int v = 2; v < last; v++)
            c.setUV(u, y, v, SLAB, Direction.DOWN, true);
    }

    /**
     * Columns one block apart along a wall, with stairs bracing their heads and feet, as in the temple's upper hall.
     */
    static void colonnade(RoomCanvas c, int v, int top, DungeonMaterial column)
    {
        var towardWall = v < c.breadth() / 2 ? c.side().getOpposite() : c.side();
        for (int u = 2; u < c.length() - 1; u++)
        {
            if (u % 2 == 1)
                RoomShapes.columnUV(c, u, v, 1, top, column);
            else
            {
                c.setUV(u, 1, v, STAIRS, towardWall);
                c.setUV(u, top, v, STAIRS, towardWall, true);
            }
        }
    }

    // The hollow column rising through the keep's atrium, ending wherever it broke
    static void shaft(RoomCanvas c, int cx, int cz, int top)
    {
        for (int z = -SHAFT_RADIUS; z <= SHAFT_RADIUS; z++)
        {
            for (int x = -SHAFT_RADIUS; x <= SHAFT_RADIUS; x++)
            {
                boolean corner = Math.abs(x) == SHAFT_RADIUS && Math.abs(z) == SHAFT_RADIUS;
                boolean side = Math.abs(x) == SHAFT_RADIUS || Math.abs(z) == SHAFT_RADIUS;
                if (!side)
                    continue;

                int broken = top - c.random.nextInt(Math.max(1, top / 4));
                RoomShapes.column(c, cx + x, cz + z, 1, broken, corner ? WALL_TRIM : MONOLITH);
            }
        }
    }

    /**
     * A small massive_pocket: storeys of ring galleries around an atrium, climbed by stairs that double back, with a chest on the top floor for those who do.
     */
    static void atrium(RoomCanvas c)
    {
        c.rigid = true;
        walls(c);
        int top = STOREY;
        while (top + STOREY < c.h - 2)
            top += STOREY;

        for (int y = STOREY; y <= top; y += STOREY)
            gallery(c, y);

        if (Math.min(c.w, c.d) >= SHAFT_MIN_SIDE)
            shaft(c, c.w / 2, c.d / 2, c.h - 3);
        climb(c, top);
        c.set(2, top + 1, 2, CHEST, Direction.SOUTH);
        for (int x : new int[] { c.w / 4 + 2, c.w * 3 / 4 - 2 })
        {
            for (int z : new int[] { c.d / 4 + 2, c.d * 3 / 4 - 2 })
                c.hangingLantern(x, z, c.h / 3);
        }
    }

    private static void gallery(RoomCanvas c, int y)
    {
        for (int z = 1; z < c.d; z++)
        {
            for (int x = 1; x < c.w; x++)
            {
                int fromWall = Math.min(Math.min(x, c.w - x), Math.min(z, c.d - z));
                if (fromWall > GALLERY_WIDTH)
                    continue;

                boolean line = fromWall == GALLERY_WIDTH || x % DungeonSize.CELL == 0 || z % DungeonSize.CELL == 0;
                c.set(x, y, z, line ? WALL : PANEL);
                if (fromWall == GALLERY_WIDTH)
                    c.set(x, y - 1, z, WALL);
                else if (fromWall == 2)
                    c.set(x, y - 1, z, SLAB, Direction.DOWN, true);
                if (fromWall == GALLERY_WIDTH && (x + z) % STOREY == 0)
                    RoomShapes.column(c, x, z, y - STOREY + 1, y - 2, COLUMN);
            }
        }
    }

    private static void climb(RoomCanvas c, int top)
    {
        int near = GALLERY_WIDTH + 2;
        boolean forward = true;
        for (int y = 0; y < top; y += STOREY)
        {
            flight(c, forward ? FIRST_LANE : SECOND_LANE, forward ? near : near + STOREY, y, STOREY, forward ? Direction.SOUTH : Direction.NORTH);
            forward = !forward;
        }
    }

    /**
     * Each flight starts on a landing beside the end of the previous one: stacked directly above it, it would leave no headroom on the way up.
     */
    private static void flight(RoomCanvas c, int lane, int fromZ, int fromY, int rise, Direction heading)
    {
        for (int x = lane; x < lane + LANE_WIDTH; x++)
        {
            if (fromY > 0)
                c.set(x, fromY, fromZ, PANEL);

            for (int step = 1; step <= rise; step++)
            {
                int z = fromZ + heading.getStepZ() * step;
                c.set(x, fromY + step, z, STAIRS, heading);
                for (int y = fromY + step + 1; y <= fromY + step + 3; y++)
                {
                    if (!c.get(x, y, z).isOpen())
                        c.set(x, y, z, AIR);
                }
            }
        }
    }

    /**
     * massive_pocket has no way up, having been meant for a player already inside: stairs double back through its atrium from the ground plate to each ring floor.
     */
    static void keepStairs(RoomCanvas c)
    {
        flight(c, FIRST_LANE, KEEP_STAIR_FAR - KEEP_FIRST_FLOOR, 0, KEEP_FIRST_FLOOR, Direction.SOUTH);
        boolean back = true;
        for (int y = KEEP_FIRST_FLOOR; y < KEEP_TOP_FLOOR; y += STOREY)
        {
            flight(c, back ? SECOND_LANE : FIRST_LANE, back ? KEEP_STAIR_FAR : KEEP_STAIR_NEAR, y, STOREY, back ? Direction.NORTH : Direction.SOUTH);
            back = !back;
        }
    }
}
