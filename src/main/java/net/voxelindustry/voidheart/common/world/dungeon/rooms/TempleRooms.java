package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTemplate;

import java.util.List;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

final class TempleRooms
{
    private static final List<DungeonTemplate> RITUALS = List.of(DungeonTemplate.EMPTY_RITUAL, DungeonTemplate.EMPTY_RITUAL_ALT, DungeonTemplate.LAMP_RITUAL, DungeonTemplate.PEARL_RITUAL);
    private static final int LANTERN_SPACING = 6;
    private static final int COLONNADE_INSET = 2;
    private static final int CHAMBER_INSET = 4;

    private TempleRooms()
    {
    }

    /**
     * The upper hall of pocket_temple: monoliths lined along both long walls, lanterns on chains down the middle.
     */
    static void hall(RoomCanvas c)
    {
        Architecture.monument(c);
        int length = c.length();
        int breadth = c.breadth();
        int top = Math.max(3, c.h - 2);

        Architecture.colonnade(c, COLONNADE_INSET, top, MONOLITH);
        Architecture.colonnade(c, breadth - COLONNADE_INSET, top, MONOLITH);
        c.fillUV(1, 0, breadth / 2 - 1, length - 1, 0, breadth / 2 + 1, FLOOR_TRIM);

        for (int u = LANTERN_SPACING / 2; u < length; u += LANTERN_SPACING)
        {
            if (c.alongX)
                c.hangingLantern(u, breadth / 2, c.h / 3);
            else
                c.hangingLantern(breadth / 2, u, c.h / 3);
        }

        int patch = length * 3 / 4;
        c.fillUV(patch - 2, 0, breadth / 2 - 1, patch + 1, 0, breadth / 2 + 1, VEIN);
        c.setUV(patch, 1, breadth / 2 - 2, GLOW);
        c.setUV(patch, 1, breadth / 2 + 2, GLOW);
    }

    /**
     * The sunken part of pocket_temple: a ribbed passage running around a chamber with an offering at its heart.
     */
    static void shrine(RoomCanvas c)
    {
        Architecture.monument(c);
        int mx = c.w / 2;
        int mz = c.d / 2;
        int inset = Math.min(CHAMBER_INSET, Math.min(c.w, c.d) / 3);
        int low = Math.min(c.h - 1, 4);

        for (int y = 1; y <= low; y++)
        {
            for (int x = inset; x <= c.w - inset; x++)
            {
                for (int z = inset; z <= c.d - inset; z++)
                {
                    boolean edgeX = x == inset || x == c.w - inset;
                    boolean edgeZ = z == inset || z == c.d - inset;
                    if (edgeX && edgeZ)
                        c.set(x, y, z, WALL_TRIM);
                    else if (edgeX || edgeZ)
                        c.set(x, y, z, y == low ? WALL_TRIM : WALL);
                }
            }
        }

        c.fill(inset + 1, 0, inset + 1, c.w - inset - 1, 0, c.d - inset - 1, FLOOR_TRIM);
        c.set(mx, 0, mz, GLOW);
        c.set(mx + 1, 1, mz + 1, METAL_SLAB);
        c.set(mx - 1, 1, mz - 1, METAL_SLAB);
        c.set(mx + 1, 1, mz - 1, CANDLE);
        c.set(mx - 1, 1, mz + 1, CANDLE);
        c.set(mx + 2, 1, mz, MONOLITH);
        c.set(mx - 2, 1, mz, MONOLITH);
        c.hangingLantern(mx, mz, 1);
    }

    /**
     * Found in every theme: where the void has settled in a ruin, one of the mod's rituals sits on bare rock.
     */
    static void sanctum(RoomCanvas c)
    {
        c.rigid = true;
        Architecture.walls(c);
        c.fill(1, 0, 1, c.w - 1, 0, c.d - 1, ROCK_NATIVE);
        var ritual = RITUALS.get(c.random.nextInt(RITUALS.size()));
        placeCentered(c, ritual);
        scatterVeins(c);
        cornerLights(c);
    }

    static void monolithCourt(RoomCanvas c)
    {
        c.rigid = true;
        c.open(ROCK_NATIVE);
        placeCentered(c, DungeonTemplate.MONOLITH_CIRCLE);
        scatterVeins(c);
        cornerLights(c);
    }

    private static void placeCentered(RoomCanvas c, DungeonTemplate template)
    {
        var size = template.size();
        int x = Math.max(1, (c.w + 1 - size.getX()) / 2);
        int z = Math.max(1, (c.d + 1 - size.getZ()) / 2);
        if (x + size.getX() <= c.w && z + size.getZ() <= c.d && size.getY() < c.h)
            c.template(template, x, 0, z);
    }

    private static void scatterVeins(RoomCanvas c)
    {
        for (int i = 0; i < c.w * c.d / 12; i++)
        {
            int x = 1 + c.random.nextInt(c.w - 1);
            int z = 1 + c.random.nextInt(c.d - 1);
            if (c.get(x, 0, z).isCube() && c.get(x, 0, z) != TEMPLATE)
                c.set(x, 0, z, c.random.nextInt(4) == 0 ? VEIN_WRITHING : VEIN);
        }
    }

    private static void cornerLights(RoomCanvas c)
    {
        for (int x : new int[] { 1, c.w - 1 })
        {
            for (int z : new int[] { 1, c.d - 1 })
                c.set(x, 1, z, LANTERN, Direction.DOWN);
        }
    }
}
