package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial;

import java.util.Set;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Hollows in the rock of a sea that has gone. Rooms only shape stone, coral and bone: what clings to surfaces grows afterwards, in DepthsReef,
 * since warping a room would leave it hanging off a surface that moved.
 */
public final class DepthsRooms
{
    public static final String TRENCH = "trench";
    public static final String REEF = "reef";
    public static final String KELP_FOREST = "kelp_forest";
    public static final String VENT_FIELD = "vent_field";
    public static final String GROTTO = "grotto";
    public static final String WHALE_FALL = "whale_fall";

    private static final double HOLLOW_REACH = 0.94;
    private static final double ROCK_REACH = 1.3;
    private static final double FLOOR_HEIGHT_SHARE = 0.4;
    private static final double HEIGHT_RADIUS_SHARE = 0.65;
    private static final double WOBBLE_SCALE = 5;
    private static final double WOBBLE_STRENGTH = 0.14;
    private static final int RIB_SPACING = 3;
    private static final int MAX_RIB_REACH = 7;
    private static final double MIN_RIB_ARC = Math.toRadians(115);
    private static final double RIB_ARC_SPREAD = Math.toRadians(55);
    private static final Set<String> CAVES = Set.of(TRENCH, REEF, KELP_FOREST, VENT_FIELD, GROTTO, WHALE_FALL);
    private static final int WRECK_LENGTH = 7;
    private static final int WRECK_WIDTH = 4;

    private DepthsRooms()
    {
    }

    public static boolean isCave(String kind)
    {
        return CAVES.contains(kind);
    }

    /**
     * Something of the worlds the void took sank here too: a plated deck with the stumps of its frame, rusted open and half gone.
     */
    private static void wreck(RoomCanvas c)
    {
        if (c.length() <= WRECK_LENGTH + 6 || c.breadth() <= WRECK_WIDTH + 6)
            return;

        int u0 = inset(c, c.length() - WRECK_LENGTH, 3);
        int v0 = inset(c, c.breadth() - WRECK_WIDTH, 3);
        for (int u = u0; u <= u0 + WRECK_LENGTH; u++)
        {
            for (int v = v0; v <= v0 + WRECK_WIDTH; v++)
            {
                if (c.random.nextInt(4) != 0)
                    c.setUV(u, 1, v, c.random.nextInt(3) == 0 ? METAL_BLOCK : METAL_CUT);
            }
        }
        for (int u = u0; u <= u0 + WRECK_LENGTH; u += 3)
        {
            for (int v : new int[] { v0, v0 + WRECK_WIDTH })
            {
                int top = 2 + c.random.nextInt(4);
                for (int y = 2; y <= top; y++)
                    c.setUV(u, y, v, METAL_WALL);
            }
        }
    }

    /**
     * A boulder with a rounded chamber inside, so a floating room is a rock adrift and a buried one a cave. Its corners stay untouched.
     */
    private static void hollow(RoomCanvas c)
    {
        var noise = new SimplexNoise(c.random);
        double centerX = c.w / 2.0;
        double centerZ = c.d / 2.0;
        double centerY = c.h * FLOOR_HEIGHT_SHARE;
        double radiusY = c.h * HEIGHT_RADIUS_SHARE;
        for (int y = -RoomCanvas.BELOW; y <= c.h; y++)
        {
            for (int z = 0; z <= c.d; z++)
            {
                for (int x = 0; x <= c.w; x++)
                {
                    double dx = (x - centerX) / centerX;
                    double dy = (y - centerY) / radiusY;
                    double dz = (z - centerZ) / centerZ;
                    double reach = Math.sqrt(dx * dx + dy * dy + dz * dz) + noise.get(x / WOBBLE_SCALE, y / WOBBLE_SCALE, z / WOBBLE_SCALE) * WOBBLE_STRENGTH;
                    if (reach > ROCK_REACH)
                        continue;
                    if (reach >= HOLLOW_REACH || y < 0)
                        c.set(x, y, z, ROCK_NATIVE);
                    else
                        c.set(x, y, z, y == 0 ? FLOOR : AIR);
                }
            }
        }
    }

    private static int inset(RoomCanvas c, int side, int margin)
    {
        return margin + c.random.nextInt(Math.max(1, side - margin * 2));
    }

    /**
     * Thick where it meets floor and ceiling, as if the rock had been poured.
     */
    private static void pillar(RoomCanvas c, int x, int z, int waist)
    {
        for (int y = 0; y <= c.h; y++)
        {
            double fromMiddle = Math.abs(y * 2.0 / c.h - 1);
            RoomShapes.disc(c, x, y, z, waist + (int) (fromMiddle * fromMiddle * 3), ROCK_NATIVE);
        }
    }

    private static void mound(RoomCanvas c, int x, int z, int radius, DungeonMaterial material)
    {
        for (int y = 1; y <= radius; y++)
            RoomShapes.disc(c, x, y, z, (int) Math.round(Math.sqrt(radius * radius - y * y)), material);
    }

    /**
     * A plate of coral on a stem, with a smaller one lower down when there is height for it.
     */
    private static void tableCoral(RoomCanvas c, int x, int z, int top, int radius)
    {
        RoomShapes.column(c, x, z, 1, top, CORAL_BLOCK);
        RoomShapes.disc(c, x, top, z, radius, CORAL_BLOCK);
        if (top >= 7 && radius >= 3)
            RoomShapes.disc(c, x + c.random.nextInt(3) - 1, top - 3 - c.random.nextInt(2), z + c.random.nextInt(3) - 1, radius - 1, CORAL_BLOCK);
    }

    private static void scatterCoral(RoomCanvas c, int count)
    {
        for (int i = 0; i < count; i++)
        {
            int radius = 2 + c.random.nextInt(3);
            if (c.w <= radius * 2 + 4 || c.d <= radius * 2 + 4)
                continue;

            int x = inset(c, c.w, radius + 2);
            int z = inset(c, c.d, radius + 2);
            int top = Math.min(c.h - 3, 3 + c.random.nextInt(Math.max(1, c.h * 2 / 3)));
            if (c.random.nextBoolean() && top > radius)
                tableCoral(c, x, z, top, radius);
            else
                mound(c, x, z, radius, CORAL_BLOCK);
        }
    }

    /**
     * Ribs arch up from a spine half sunk in the silt. Each breaks off at its own length.
     */
    private static void whale(RoomCanvas c, int from, int to, int v, int y)
    {
        int length = to - from;
        for (int u = from; u <= to; u++)
        {
            double along = (u - from) / (double) length;
            int spineY = y + (int) Math.round(Math.sin(along * Math.PI));
            c.setUV(u, spineY, v, WHALE_BONE, c.forward());

            if ((u - from) % RIB_SPACING != 1 || along > 0.82)
                continue;

            int reach = Math.min(Math.min(MAX_RIB_REACH, c.h - spineY - 2), (int) Math.round(2 + Math.sin(along * Math.PI) * MAX_RIB_REACH));
            rib(c, u, spineY, v, reach, 1);
            rib(c, u, spineY, v, reach, -1);
        }

        for (int dy = 0; dy <= 2; dy++)
        {
            for (int dv = -2 + dy; dv <= 2 - dy; dv++)
                c.fillUV(to + 1, y + dy, v + dv, to + 3 - dy, y + dy, v + dv, WHALE_BONE);
        }
    }

    private static void rib(RoomCanvas c, int u, int spineY, int v, int reach, int side)
    {
        double arc = MIN_RIB_ARC + c.random.nextDouble() * RIB_ARC_SPREAD;
        int steps = reach * 3;
        for (int step = 1; step <= steps; step++)
        {
            double angle = arc * step / steps;
            int across = (int) Math.round(Math.sin(angle) * reach);
            int up = (int) Math.round((1 - Math.cos(angle)) * reach);
            c.setUV(u, spineY + up, v + across * side, WHALE_BONE, Direction.UP);
        }
    }

    static void trench(RoomCanvas c)
    {
        hollow(c);
        int pillars = 1 + c.w * c.d / 400;
        for (int i = 0; i < pillars; i++)
            pillar(c, inset(c, c.w, 6), inset(c, c.d, 6), 1 + c.random.nextInt(2));
        scatterCoral(c, c.w * c.d / 160);
        wreck(c);
        if (c.length() >= 24)
            whale(c, c.length() / 4, c.length() * 3 / 4, c.breadth() / 3, 1);
    }

    static void reef(RoomCanvas c)
    {
        hollow(c);
        wreck(c);
        scatterCoral(c, Math.max(2, c.w * c.d / 70));
    }

    static void kelpForest(RoomCanvas c)
    {
        hollow(c);
        wreck(c);
        if (Math.min(c.w, c.d) >= 16)
            pillar(c, inset(c, c.w, 5), inset(c, c.d, 5), 1);
    }

    static void ventField(RoomCanvas c)
    {
        hollow(c);
        int lumps = c.w * c.d / 110;
        for (int i = 0; i < lumps; i++)
            mound(c, inset(c, c.w, 4), inset(c, c.d, 4), 2 + c.random.nextInt(2), ROCK_NATIVE);
        wreck(c);
    }

    static void grotto(RoomCanvas c)
    {
        hollow(c);
        wreck(c);
        if (Math.min(c.w, c.d) >= 10)
            mound(c, inset(c, c.w, 3), inset(c, c.d, 3), 2, ROCK_NATIVE);
    }

    static void whaleFall(RoomCanvas c)
    {
        hollow(c);
        whale(c, c.length() / 6, c.length() * 3 / 4, c.breadth() / 3, 1);
        wreck(c);
        scatterCoral(c, 1);
    }
}
