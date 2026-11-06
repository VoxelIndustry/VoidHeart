package net.voxelindustry.voidheart.common.world.daedalus;

/**
 * The void's old rituals left standing in rooms, as the ruins of the overworld hold them: altars ringed by their pillars, circles of broken monoliths and shrines of a pillar under a void lamp.
 * Like furniture, each block is kept or skipped by reading only its own column, so chunks agree at their borders.
 */
final class RoomRelics
{
    static final int RITUAL_REACH = 4;

    /** Where the altar looks for the eight pillars it crafts from, so a circle found in the Daedalus works. */
    private static final int[][] ALTAR_PILLARS = { { -1, 3 }, { 1, 3 }, { 1, -3 }, { -1, -3 }, { 3, -1 }, { 3, 1 }, { -3, -1 }, { -3, 1 } };
    private static final int[][] CIRCLE_CANDLES = { { 2, 2 }, { 2, -2 }, { -2, 2 }, { -2, -2 } };
    private static final int[] CANDLE_FACINGS = { Compass.NORTH, Compass.SOUTH, Compass.WEST, Compass.EAST };
    private static final int RING_POINTS = 8;

    interface Clearance
    {
        boolean isClear(int x, int z);
    }

    private final DaedalusCanvas canvas;
    private final Clearance clearance;

    RoomRelics(DaedalusCanvas canvas, Clearance clearance)
    {
        this.canvas = canvas;
        this.clearance = clearance;
    }

    /** An altar amid its pillars on a diamond of eldritch stone, with candles on its sides and a gap to step in at each tip. */
    void ritualCircle(int x, int y, int z)
    {
        for (int dz = -RITUAL_REACH; dz <= RITUAL_REACH; dz++)
        {
            for (int dx = -RITUAL_REACH; dx <= RITUAL_REACH; dx++)
            {
                if (Math.abs(dx) + Math.abs(dz) == RITUAL_REACH || dx == 0 && dz == 0)
                    inlay(x + dx, y - 1, z + dz, DaedalusMaterial.ELDRITCH);
            }
        }
        place(x, y, z, DaedalusMaterial.ALTAR);
        for (int[] offset : ALTAR_PILLARS)
            place(x + offset[0], y, z + offset[1], DaedalusMaterial.PILLAR);
        for (int index = 0; index < CIRCLE_CANDLES.length; index++)
        {
            int candleX = x + CIRCLE_CANDLES[index][0];
            int candleZ = z + CIRCLE_CANDLES[index][1];
            if (isFree(candleX, y, candleZ))
                canvas.setProp(candleX, y, candleZ, DaedalusProp.CANDLES, CANDLE_FACINGS[index]);
        }
    }

    void monolithRing(int x, int y, int z, int radius, int shortest, int tallest, long seed)
    {
        int diagonal = (int) Math.round(radius / Math.sqrt(2));
        for (int point = 0; point < RING_POINTS; point++)
        {
            int dx = point % 2 == 0 ? Compass.dx(point / 2) * radius : (point == 1 || point == 3 ? diagonal : -diagonal);
            int dz = point % 2 == 0 ? Compass.dz(point / 2) * radius : (point == 1 || point == 7 ? -diagonal : diagonal);
            int height = shortest + DaedalusHash.pick(DaedalusHash.mix(seed + point), tallest - shortest + 1);
            monolith(x + dx, y, z + dz, height);
        }
    }

    /** Cut short where it would touch what stands above, so a block of air always stays over its tip. */
    void monolith(int x, int y, int z, int height)
    {
        if (!clearance.isClear(x, z) || canvas.get(x, y - 1, z).isPassable())
            return;
        int fits = 0;
        while (fits <= height && canvas.raw(x, y + fits, z) == DaedalusMaterial.AIR.ordinal())
            fits++;
        int standing = Math.min(height, fits - 1);
        if (standing < 1)
            return;
        inlay(x, y - 1, z, DaedalusMaterial.OBSIDIAN);
        for (int index = 0; index < standing; index++)
            canvas.set(x, y + index, z, DaedalusMaterial.monolith(index, standing));
    }

    void shrine(int x, int y, int z)
    {
        if (isFree(x, y, z) && canvas.raw(x, y + 1, z) == DaedalusMaterial.AIR.ordinal())
        {
            canvas.set(x, y, z, DaedalusMaterial.PILLAR);
            canvas.set(x, y + 1, z, DaedalusMaterial.SHRINE_LAMP);
        }
    }

    private boolean isFree(int x, int y, int z)
    {
        int raw = canvas.raw(x, y, z);
        boolean open = raw == DaedalusMaterial.AIR.ordinal() || raw == DaedalusMaterial.CARPET.ordinal();
        return open && clearance.isClear(x, z) && !canvas.get(x, y - 1, z).isPassable();
    }

    private void place(int x, int y, int z, DaedalusMaterial material)
    {
        if (isFree(x, y, z))
            canvas.set(x, y, z, material);
    }

    private void inlay(int x, int y, int z, DaedalusMaterial material)
    {
        var floor = canvas.get(x, y, z);
        if (floor == DaedalusMaterial.FLOOR || floor == DaedalusMaterial.FLOOR_TRIM || floor == DaedalusMaterial.FLOOR_INLAY || floor == DaedalusMaterial.ELDRITCH)
            canvas.set(x, y, z, material);
    }
}
