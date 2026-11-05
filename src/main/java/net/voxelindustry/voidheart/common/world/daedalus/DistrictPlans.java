package net.voxelindustry.voidheart.common.world.daedalus;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.ExecutionException;

/**
 * Plans are small enough to keep the whole neighbourhood of every player. Thread safe: chunks of a district generating at once wait for a single plan.
 */
public final class DistrictPlans
{
    private static final int CACHE_SIZE = 256;

    private record Key(long worldSeed, DistrictPos pos)
    {
    }

    private static final Cache<Key, DistrictPlan> PLANS = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();

    private DistrictPlans()
    {
    }

    public static DistrictPlan get(long worldSeed, DistrictPos pos)
    {
        try
        {
            return PLANS.get(new Key(worldSeed, pos), () -> DistrictPlanner.generate(worldSeed, pos));
        } catch (ExecutionException e)
        {
            throw new IllegalStateException("Could not plan district " + pos, e);
        }
    }

    /**
     * Draws every district and bridge touching the canvas. A chunk only ever touches one of them.
     */
    public static void paint(long worldSeed, DaedalusCanvas canvas)
    {
        int firstX = Math.floorDiv(canvas.minX(), DistrictPos.PITCH);
        int lastX = Math.floorDiv(canvas.maxX(), DistrictPos.PITCH);
        int firstZ = Math.floorDiv(canvas.minZ(), DistrictPos.PITCH);
        int lastZ = Math.floorDiv(canvas.maxZ(), DistrictPos.PITCH);
        for (int z = firstZ; z <= lastZ; z++)
        {
            for (int x = firstX; x <= lastX; x++)
                paintDistrict(worldSeed, new DistrictPos(x, z), canvas);
        }
    }

    private static void paintDistrict(long worldSeed, DistrictPos pos, DaedalusCanvas canvas)
    {
        int minX = pos.originX();
        int minZ = pos.originZ();
        int inner = DistrictPos.SIZE - 1;
        int outer = DistrictPos.PITCH - 1;
        if (canvas.overlaps(minX, minZ, minX + inner, minZ + inner))
            DistrictPainter.paint(get(worldSeed, pos), canvas);
        if (canvas.overlaps(minX + DistrictPos.SIZE, minZ, minX + outer, minZ + inner))
            paintBridges(worldSeed, pos, Compass.EAST, canvas);
        if (canvas.overlaps(minX, minZ + DistrictPos.SIZE, minX + inner, minZ + outer))
            paintBridges(worldSeed, pos, Compass.SOUTH, canvas);
    }

    private static void paintBridges(long worldSeed, DistrictPos pos, int direction, DaedalusCanvas canvas)
    {
        for (var bridge : DaedalusBridges.between(worldSeed, pos, direction))
            BridgePainter.paint(bridge, canvas);
    }
}
