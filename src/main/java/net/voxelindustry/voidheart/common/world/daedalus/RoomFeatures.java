package net.voxelindustry.voidheart.common.world.daedalus;

/**
 * Raised features that break up a room's floor: a dais under its centrepiece and a pool round a font. Each block is kept or skipped by reading only its own column, so chunks agree at their borders.
 */
final class RoomFeatures
{
    static final int DAIS_REACH = 2;
    static final int ENGINE_REACH = 4;
    static final int BOILER_REACH = 2;
    private static final int BOILER_HEIGHT = 5;
    private static final int MIN_BOILER_HEIGHT = 3;
    private static final int PIPE_HEIGHT = 3;
    private static final int ABANDON_ODDS = 6;
    private static final long ABANDON_SALT = 0xAB4D0;

    private final DaedalusCanvas canvas;
    private final RoomRelics.Clearance clearance;

    RoomFeatures(DaedalusCanvas canvas, RoomRelics.Clearance clearance)
    {
        this.canvas = canvas;
        this.clearance = clearance;
    }

    /** @return the height a centrepiece stands at, on a platform walked up by stairs on each side */
    int raiseDais(int x, int y, int z)
    {
        for (int dz = -DAIS_REACH; dz <= DAIS_REACH; dz++)
        {
            for (int dx = -DAIS_REACH; dx <= DAIS_REACH; dx++)
            {
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == DAIS_REACH;
                DaedalusMaterial material;
                if (!rim)
                    material = DaedalusMaterial.FLOOR_TRIM;
                else if (Math.abs(dx) == Math.abs(dz))
                    material = DaedalusMaterial.SLAB;
                else if (Math.abs(dx) == DAIS_REACH)
                    material = DaedalusMaterial.stairs(dx < 0 ? Compass.EAST : Compass.WEST);
                else
                    material = DaedalusMaterial.stairs(dz < 0 ? Compass.SOUTH : Compass.NORTH);
                place(x + dx, y, z + dz, material);
            }
        }
        return y + 1;
    }

    void raisePool(int x, int y, int z, int reach)
    {
        for (int dz = -reach; dz <= reach; dz++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == reach;
                boolean font = dx == 0 && dz == 0;
                place(x + dx, y, z + dz, rim ? DaedalusMaterial.SLAB : font ? DaedalusMaterial.COLUMN : DaedalusMaterial.WATER);
            }
        }
        if (canvas.get(x, y, z) == DaedalusMaterial.COLUMN && canvas.raw(x, y + 1, z) == DaedalusMaterial.AIR.ordinal())
            canvas.setProp(x, y + 1, z, DaedalusProp.BASIN, Compass.NORTH);
    }

    /**
     * A machine of the people who harnessed the void: a condenser between four coils, fed by flywheels and watched from two consoles, on a floor ringed with trim.
     */
    void raiseEngine(int x, int y, int z)
    {
        for (int dz = -ENGINE_REACH; dz <= ENGINE_REACH; dz++)
        {
            for (int dx = -ENGINE_REACH; dx <= ENGINE_REACH; dx++)
            {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == ENGINE_REACH || ring == 0)
                    inlay(x + dx, y - 1, z + dz, ring == 0 ? DaedalusMaterial.ELDRITCH : DaedalusMaterial.FLOOR_TRIM);
            }
        }
        placeProp(x, y, z, DaedalusProp.CONDENSER, Compass.NORTH);
        for (int corner = 0; corner < Compass.COUNT; corner++)
        {
            int dx = corner == 0 || corner == 3 ? -2 : 2;
            int dz = corner < 2 ? -2 : 2;
            placeProp(x + dx, y, z + dz, DaedalusProp.COIL, Compass.NORTH);
        }
        placeProp(x, y, z + 3, DaedalusProp.CONSOLE, Compass.SOUTH);
        placeProp(x, y, z - 3, DaedalusProp.CONSOLE, Compass.NORTH);
        placeProp(x + 1, y, z + 3, DaedalusProp.CELLS, Compass.SOUTH);
        placeProp(x - 1, y, z - 3, DaedalusProp.CELLS, Compass.NORTH);
        placeProp(x + 3, y, z, DaedalusProp.FLYWHEEL, Compass.NORTH);
        placeProp(x - 3, y, z, DaedalusProp.FLYWHEEL, Compass.SOUTH);
    }

    /**
     * A copper boiler three blocks across with its firebox facing out, gauges on its flanks, a chimney up through the ceiling and pipes along both sides to vents in the room's walls.
     *
     * @param top the highest air of the room, and the bounds of its floor the pipes run to
     */
    void raiseBoiler(int x, int y, int z, int front, int top, int minX, int maxX, int minZ, int maxZ)
    {
        int height = Math.min(BOILER_HEIGHT, top - y);
        if (height < MIN_BOILER_HEIGHT)
            return;
        int frontX = Compass.dx(front);
        int frontZ = Compass.dz(front);
        for (int level = 0; level < height; level++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                for (int dx = -1; dx <= 1; dx++)
                {
                    boolean corner = dx != 0 && dz != 0;
                    boolean centre = dx == 0 && dz == 0;
                    DaedalusMaterial material;
                    if (level == 0)
                        material = dx == frontX && dz == frontZ ? DaedalusMaterial.VENT : DaedalusMaterial.COPPER;
                    else if (corner)
                        continue;
                    else if (level == height - 1)
                        material = centre ? DaedalusMaterial.COPPER : DaedalusMaterial.COPPER_SLAB;
                    else
                        material = DaedalusMaterial.COPPER;
                    build(x + dx, y + level, z + dz, material);
                }
            }
        }
        int chimney = y + height;
        while (chimney <= top && canvas.raw(x, chimney, z) == DaedalusMaterial.AIR.ordinal())
            canvas.set(x, chimney++, z, DaedalusMaterial.PIPE_Y);
        if (isSolidMasonry(canvas.get(x, chimney, z)))
            canvas.set(x, chimney, z, DaedalusMaterial.VENT);

        placeOnBoiler(x + frontX * 2, y + 1, z + frontZ * 2, DaedalusProp.VALVE, front);
        placeOnBoiler(x + frontX * 2, y + 2, z + frontZ * 2, DaedalusProp.GAUGE, front);
        int pipeY = y + PIPE_HEIGHT;
        for (int flank : new int[] { Compass.clockwise(front), Compass.opposite(Compass.clockwise(front)) })
        {
            int flankX = Compass.dx(flank);
            int flankZ = Compass.dz(flank);
            placeOnBoiler(x + flankX * 2, y + 2, z + flankZ * 2, DaedalusProp.GAUGE, flank);
            if (pipeY > top)
                continue;
            var pipe = flankX != 0 ? DaedalusMaterial.PIPE_X : DaedalusMaterial.PIPE_Z;
            int runX = x + flankX * 2;
            int runZ = z + flankZ * 2;
            while (runX >= minX && runX <= maxX && runZ >= minZ && runZ <= maxZ)
            {
                build(runX, pipeY, runZ, pipe);
                runX += flankX;
                runZ += flankZ;
            }
            if (isSolidMasonry(canvas.get(runX, pipeY, runZ)))
                canvas.set(runX, pipeY, runZ, DaedalusMaterial.VENT);
        }
    }

    /**
     * Centuries of neglect round a machine: fallen masonry on the floor and cobwebs over what still stands.
     */
    void abandon(int x, int y, int z, int reach, long seed)
    {
        for (int dz = -reach; dz <= reach; dz++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                int roll = DaedalusHash.pick(DaedalusHash.mix(seed, x + dx, z + dz, ABANDON_SALT), ABANDON_ODDS);
                int atX = x + dx;
                int atZ = z + dz;
                if (roll == 0 && canvas.raw(atX, y, atZ) == DaedalusMaterial.AIR.ordinal() && clearance.isClear(atX, atZ) && !canvas.get(atX, y - 1, atZ).isPassable())
                    canvas.set(atX, y, atZ, DaedalusMaterial.SLAB);
                else if (roll == 1 && canvas.get(atX, y, atZ) == DaedalusMaterial.PROP && canvas.raw(atX, y + 1, atZ) == DaedalusMaterial.AIR.ordinal())
                    canvas.set(atX, y + 1, atZ, DaedalusMaterial.COBWEB);
            }
        }
    }

    private static boolean isSolidMasonry(DaedalusMaterial material)
    {
        return material == DaedalusMaterial.WALL || material == DaedalusMaterial.PANEL || material == DaedalusMaterial.TRIM || material == DaedalusMaterial.CEILING;
    }

    private void build(int x, int y, int z, DaedalusMaterial material)
    {
        int raw = canvas.raw(x, y, z);
        if ((raw == DaedalusMaterial.AIR.ordinal() || raw == DaedalusMaterial.CARPET.ordinal()) && clearance.isClear(x, z))
            canvas.set(x, y, z, material);
    }

    private void placeOnBoiler(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (canvas.raw(x, y, z) == DaedalusMaterial.AIR.ordinal() && clearance.isClear(x, z))
            canvas.setProp(x, y, z, prop, facing);
    }

    /**
     * The same prop at the compass and diagonal points of a circle.
     */
    void ringOf(int x, int y, int z, int radius, DaedalusProp prop)
    {
        int diagonal = (int) Math.round(radius / Math.sqrt(2));
        int[][] points = { { 0, -radius }, { diagonal, -diagonal }, { radius, 0 }, { diagonal, diagonal }, { 0, radius }, { -diagonal, diagonal }, { -radius, 0 }, { -diagonal, -diagonal } };
        for (int[] point : points)
            placeProp(x + point[0], y, z + point[1], prop, Compass.NORTH);
    }

    private void placeProp(int x, int y, int z, DaedalusProp prop, int facing)
    {
        if (canvas.raw(x, y, z) == DaedalusMaterial.AIR.ordinal() && clearance.isClear(x, z) && !canvas.get(x, y - 1, z).isPassable())
            canvas.setProp(x, y, z, prop, facing);
    }

    private void inlay(int x, int y, int z, DaedalusMaterial material)
    {
        var floor = canvas.get(x, y, z);
        if (floor == DaedalusMaterial.FLOOR || floor == DaedalusMaterial.FLOOR_TRIM || floor == DaedalusMaterial.FLOOR_INLAY)
            canvas.set(x, y, z, material);
    }

    private void place(int x, int y, int z, DaedalusMaterial material)
    {
        int raw = canvas.raw(x, y, z);
        boolean open = raw == DaedalusMaterial.AIR.ordinal() || raw == DaedalusMaterial.CARPET.ordinal();
        if (open && clearance.isClear(x, z) && !canvas.get(x, y - 1, z).isPassable())
            canvas.set(x, y, z, material);
    }
}
